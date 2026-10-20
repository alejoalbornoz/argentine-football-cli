"""Predict one match and print the probabilities as JSON on stdout (called by the Java app).

Usage, from the project root:
    python python/predict.py --db data/futbol.db --home "Boca Juniors" --away "River Plate" --model poisson

On failure it prints a one-line message on stderr and exits with code 1. The texts meant for the
app's user (details and error messages) are in Spanish, like the rest of the app.
"""

import argparse
import json
import sqlite3
import sys
from pathlib import Path

import numpy as np
import pandas as pd

import elo
import poisson
from match_data import load_matches


def predict_elo(matches: pd.DataFrame, home: str, away: str, k: float, home_advantage: float) -> dict:
    model = elo.Elo(k=k, home_advantage=home_advantage)
    model.fit(matches)
    return build_result("Elo", home, away, model.predict(home, away), {
        f"Rating de {home}": f"{model.rating(home):.0f}",
        f"Rating de {away}": f"{model.rating(away):.0f}",
        f"Último partido de {home}": last_match_date(matches, home),
        f"Último partido de {away}": last_match_date(matches, away),
        "Ventaja de local": f"{number(home_advantage)} puntos",
        "Factor K": number(k),
        "Factor de empate": number(model.draw_factor, 3),
        "Partidos usados": str(len(matches)),
    })


def predict_poisson(matches: pd.DataFrame, home: str, away: str, window_days: int) -> dict:
    model = poisson.PoissonModel(window_days=window_days)
    model.fit(matches)
    home_goals, away_goals = model.expected_goals(home, away)
    matrix = poisson.score_matrix(home_goals, away_goals)
    likeliest = np.unravel_index(matrix.argmax(), matrix.shape)  # (home goals, away goals)
    return build_result("Poisson", home, away, poisson.outcome_probabilities(home_goals, away_goals), {
        "Goles esperados": f"{home} {number(home_goals, 2)} - {number(away_goals, 2)} {away}",
        "Resultado más probable": f"{likeliest[0]}-{likeliest[1]} ({number(100 * matrix[likeliest], 1)}%)",
        f"Ataque / defensa de {home}": describe_strength(model.strength(home)),
        f"Ataque / defensa de {away}": describe_strength(model.strength(away)),
        "Goles por partido en la liga":
            f"{number(model.home_avg, 2)} de local, {number(model.away_avg, 2)} de visitante",
        "Ventana": (f"últimos {window_days} días hasta el {model.window_end:%d/%m/%Y} "
                    f"({model.window_matches} partidos)"),
    })


def number(value: float, decimals: int | None = None) -> str:
    """A number the way the (Spanish) app shows it: 1,33 instead of 1.33.

    Without `decimals` it drops useless zeros: 60.0 -> "60", 2.5 -> "2,5".
    """
    text = f"{value:g}" if decimals is None else f"{value:.{decimals}f}"
    return text.replace(".", ",")


def build_result(model: str, home: str, away: str, probabilities: tuple[float, float, float],
                 details: dict[str, str]) -> dict:
    home_win, draw, away_win = probabilities
    return {
        "model": model,
        "home_team": home,
        "away_team": away,
        "home_win": home_win,
        "draw": draw,
        "away_win": away_win,
        "details": details,  # shown by the Java app as "label  value" lines, in this order
    }


def describe_strength(strength: poisson.Strength) -> str:
    if strength.matches == 0:
        return "1,00 / 1,00 (sin partidos en la ventana: se lo considera promedio)"
    return f"{number(strength.attack, 2)} / {number(strength.defence, 2)} ({strength.matches} partidos)"


def last_match_date(matches: pd.DataFrame, team: str) -> str:
    played = matches[(matches.home_team == team) | (matches.away_team == team)]
    return f"{pd.Timestamp(played.match_date.iloc[-1]):%d/%m/%Y}"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Predict a match and print the result as JSON.")
    parser.add_argument("--db", type=Path, required=True, help="the app's SQLite database")
    parser.add_argument("--home", required=True, help="home team, spelled as in the database")
    parser.add_argument("--away", required=True, help="away team, spelled as in the database")
    parser.add_argument("--model", choices=["elo", "poisson"], default="elo")
    parser.add_argument("--k", type=float, default=elo.DEFAULT_K, help="Elo K factor")
    parser.add_argument("--home-advantage", type=float, default=elo.DEFAULT_HOME_ADVANTAGE,
                        help="Elo points added to the home team")
    parser.add_argument("--window-days", type=int, default=poisson.DEFAULT_WINDOW_DAYS,
                        help="Poisson: only matches in this many days before the latest one count")
    args = parser.parse_args(argv)

    try:
        matches = load_matches(args.db)
        teams = set(matches.home_team) | set(matches.away_team)
        for team in (args.home, args.away):
            if team not in teams:
                raise ValueError(f"equipo desconocido: {team}")
        if args.home == args.away:
            raise ValueError("el local y el visitante tienen que ser equipos distintos")

        if args.model == "elo":
            result = predict_elo(matches, args.home, args.away, args.k, args.home_advantage)
        else:
            result = predict_poisson(matches, args.home, args.away, args.window_days)
    except (OSError, ValueError, sqlite3.Error, pd.errors.DatabaseError) as error:
        print(error, file=sys.stderr)
        return 1

    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    sys.exit(main())
