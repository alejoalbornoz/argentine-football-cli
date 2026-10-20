"""Walk-forward backtest: how well would each model have predicted matches it had not seen?

Matches before --from only warm the models up. From then on, the backtest goes date by date:
it predicts every match of the day using only matches from earlier dates, and only then lets
the models learn that day's results. No prediction ever sees its own result, a later one, or
another result from the same day.

Usage, from the project root:
    python python/backtest.py --db data/futbol.db [--from 2023-01-01]

Prints the scores as JSON on stdout. On failure it prints a message on stderr and exits with code 1.
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
from match_data import HOME_WIN, load_matches, outcome
from metrics import accuracy, brier_score, log_loss

DEFAULT_FROM = "2023-01-01"
BASELINE = "Baseline: home always wins"


def walk_forward(matches: pd.DataFrame, start: str, k: float = elo.DEFAULT_K,
                 home_advantage: float = elo.DEFAULT_HOME_ADVANTAGE,
                 window_days: int = poisson.DEFAULT_WINDOW_DAYS) -> dict[str, np.ndarray]:
    """Each model's (home win, draw, away win) probabilities for every match from `start` on.

    `matches` must be in chronological order. Rows of the arrays follow that order.
    """
    outcomes = np.array([outcome(h, a) for h, a in zip(matches.home_goals, matches.away_goals)])
    warm_up = matches[matches.match_date < start]
    evaluated = matches[matches.match_date >= start]
    if evaluated.empty:
        raise ValueError(f"no matches on or after {start} to evaluate")
    if warm_up.empty:
        raise ValueError(f"no matches before {start} to warm the models up")

    elo_model = elo.Elo(k=k, home_advantage=home_advantage)
    elo_model.fit(warm_up)  # ratings and draw factor learned from the warm-up only

    predictions = {"Elo": [], "Poisson": [], BASELINE: []}
    for date, day in evaluated.groupby("match_date", sort=True):
        before = (matches.match_date < date).to_numpy()  # ISO dates compare correctly as text
        poisson_model = poisson.PoissonModel(window_days=window_days)
        poisson_model.fit(matches[before])  # refitted every date: about 10 of the ~13 seconds
        # Probabilistic version of "home always wins": the same past frequencies for every match.
        past_rates = np.bincount(outcomes[before], minlength=3) / before.sum()

        for match in day.itertuples(index=False):
            predictions["Elo"].append(elo_model.predict(match.home_team, match.away_team))
            predictions["Poisson"].append(poisson_model.predict(match.home_team, match.away_team))
            predictions[BASELINE].append(past_rates)
        for match in day.itertuples(index=False):  # only now does Elo learn the day's results
            elo_model.update(match.home_team, match.away_team, match.home_goals, match.away_goals)

    return {model: np.array(rows) for model, rows in predictions.items()}


def backtest(matches: pd.DataFrame, start: str, **params) -> dict:
    """Scores for each model and the baseline over the matches from `start` on."""
    predictions = walk_forward(matches, start, **params)
    evaluated = matches[matches.match_date >= start]
    outcomes = np.array([outcome(h, a) for h, a in zip(evaluated.home_goals, evaluated.away_goals)])
    rates = np.bincount(outcomes, minlength=3) / len(outcomes)

    results = []
    for model, probabilities in predictions.items():
        # The baseline's pick is always a home win; its probabilities only serve log loss and Brier.
        hits = float(np.mean(outcomes == HOME_WIN)) if model == BASELINE else accuracy(probabilities, outcomes)
        results.append({
            "model": model,
            "log_loss": log_loss(probabilities, outcomes),
            "brier": brier_score(probabilities, outcomes),
            "accuracy": hits,
        })

    return {
        "evaluated_from": evaluated.match_date.iloc[0],
        "evaluated_to": evaluated.match_date.iloc[-1],
        "matches": len(evaluated),
        "warm_up_matches": len(matches) - len(evaluated),
        "actual": {"home_win": rates[0], "draw": rates[1], "away_win": rates[2]},
        "results": results,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Backtest the models and print the scores as JSON.")
    parser.add_argument("--db", type=Path, required=True, help="the app's SQLite database")
    parser.add_argument("--from", dest="start", default=DEFAULT_FROM,
                        help="first date to evaluate (YYYY-MM-DD); earlier matches only warm up")
    parser.add_argument("--k", type=float, default=elo.DEFAULT_K, help="Elo K factor")
    parser.add_argument("--home-advantage", type=float, default=elo.DEFAULT_HOME_ADVANTAGE,
                        help="Elo points added to the home team")
    parser.add_argument("--window-days", type=int, default=poisson.DEFAULT_WINDOW_DAYS,
                        help="Poisson: only matches in this many days before each date count")
    args = parser.parse_args(argv)

    try:
        result = backtest(load_matches(args.db), args.start, k=args.k,
                          home_advantage=args.home_advantage, window_days=args.window_days)
    except (OSError, ValueError, sqlite3.Error, pd.errors.DatabaseError) as error:
        print(error, file=sys.stderr)
        return 1

    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    sys.exit(main())
