"""Predict one match and print the probabilities as JSON on stdout (called by the Java app).

Usage, from the project root:
    python python/predict.py --db data/futbol.db --home "Boca Juniors" --away "River Plate" --model poisson

On failure it prints a one-line message on stderr and exits with code 1.
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
        f"{home} rating": f"{model.rating(home):.0f}",
        f"{away} rating": f"{model.rating(away):.0f}",
        f"{home} last match": last_match_date(matches, home),
        f"{away} last match": last_match_date(matches, away),
        "Home advantage": f"{home_advantage:g} points",
        "K factor": f"{k:g}",
        "Draw factor": f"{model.draw_factor:.3f}",
        "Matches used": str(len(matches)),
    })


def predict_poisson(matches: pd.DataFrame, home: str, away: str, window_days: int) -> dict:
    model = poisson.PoissonModel(window_days=window_days)
    model.fit(matches)
    home_goals, away_goals = model.expected_goals(home, away)
    matrix = poisson.score_matrix(home_goals, away_goals)
    likeliest = np.unravel_index(matrix.argmax(), matrix.shape)  # (home goals, away goals)
    return build_result("Poisson", home, away, poisson.outcome_probabilities(home_goals, away_goals), {
        "Expected goals": f"{home} {home_goals:.2f} - {away_goals:.2f} {away}",
        "Most likely score": f"{likeliest[0]}-{likeliest[1]} ({matrix[likeliest]:.1%})",
        f"{home} attack / defence": describe_strength(model.strength(home)),
        f"{away} attack / defence": describe_strength(model.strength(away)),
        "League goals per match": f"{model.home_avg:.2f} home, {model.away_avg:.2f} away",
        "Window": f"last {window_days} days to {model.window_end:%Y-%m-%d} ({model.window_matches} matches)",
    })


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
        return "1.00 / 1.00 (no matches in the window: assumed average)"
    return f"{strength.attack:.2f} / {strength.defence:.2f} ({strength.matches} matches)"


def last_match_date(matches: pd.DataFrame, team: str) -> str:
    played = matches[(matches.home_team == team) | (matches.away_team == team)]
    return played.match_date.iloc[-1]


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
                raise ValueError(f"unknown team: {team}")
        if args.home == args.away:
            raise ValueError("home and away must be different teams")

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
