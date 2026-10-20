"""Poisson goals model: attack and defence strength per team, plus home advantage.

Each side's goals are a Poisson random variable with these means (expected goals):

    home: league_home_avg * attack[home] * defence[away]
    away: league_away_avg * attack[away] * defence[home]

Strengths are relative to the league average, so 1.0 is an average team: attack 1.2 scores 20%
more than average, defence 0.8 concedes 20% less. Home advantage comes from the league itself:
home teams score more on average than away teams (league_home_avg > league_away_avg).

Only recent matches count (window_days), and every team starts with PRIOR_MATCHES "average"
matches so that a handful of results cannot make a team look extreme.
"""

from dataclasses import dataclass

import numpy as np
import pandas as pd
from scipy.stats import poisson

DEFAULT_WINDOW_DAYS = 730  # about two seasons
PRIOR_MATCHES = 5
MAX_GOALS = 10  # scores above 10 are so unlikely they don't change the probabilities


@dataclass(frozen=True)
class Strength:
    attack: float
    defence: float
    matches: int  # matches in the window; 0 means the team is assumed average


def score_matrix(home_expected: float, away_expected: float) -> np.ndarray:
    """matrix[i, j] = probability that the home team scores i and the away team j.

    Assumes the two teams' goals are independent.
    """
    goals = np.arange(MAX_GOALS + 1)
    matrix = np.outer(poisson.pmf(goals, home_expected), poisson.pmf(goals, away_expected))
    return matrix / matrix.sum()  # put the tiny probability of 11+ goals back in


def outcome_probabilities(home_expected: float, away_expected: float) -> tuple[float, float, float]:
    """(home win, draw, away win): the cells below, on and above the diagonal of the score matrix."""
    matrix = score_matrix(home_expected, away_expected)
    return float(np.tril(matrix, -1).sum()), float(np.trace(matrix)), float(np.triu(matrix, 1).sum())


class PoissonModel:

    def __init__(self, window_days: int = DEFAULT_WINDOW_DAYS):
        self.window_days = window_days
        # Learned by fit():
        self.home_avg = self.away_avg = 0.0
        self.strengths: dict[str, Strength] = {}
        self.window_start = self.window_end = None
        self.window_matches = 0

    def fit(self, matches: pd.DataFrame) -> None:
        """Learn league averages and team strengths from the window that ends at the latest match."""
        dates = pd.to_datetime(matches.match_date)
        self.window_end = dates.max()
        self.window_start = self.window_end - pd.Timedelta(days=self.window_days)
        recent = matches[dates > self.window_start]
        if recent.empty:
            raise ValueError("no matches to fit the Poisson model")
        self.window_matches = len(recent)

        self.home_avg = recent.home_goals.mean()
        self.away_avg = recent.away_goals.mean()
        team_avg = (self.home_avg + self.away_avg) / 2  # goals one team scores in an average match

        # One row per team per match, so home and away games count the same way.
        per_team = pd.concat([
            pd.DataFrame({"team": recent.home_team, "scored": recent.home_goals, "conceded": recent.away_goals}),
            pd.DataFrame({"team": recent.away_team, "scored": recent.away_goals, "conceded": recent.home_goals}),
        ])
        by_team = per_team.groupby("team")
        totals = by_team[["scored", "conceded"]].sum()
        totals["matches"] = by_team.size()

        prior_goals = PRIOR_MATCHES * team_avg
        self.strengths = {}
        for team, row in totals.iterrows():
            expected = (row.matches + PRIOR_MATCHES) * team_avg  # goals an average team would have
            self.strengths[team] = Strength(attack=float((row.scored + prior_goals) / expected),
                                            defence=float((row.conceded + prior_goals) / expected),
                                            matches=int(row.matches))

    def strength(self, team: str) -> Strength:
        return self.strengths.get(team, Strength(attack=1.0, defence=1.0, matches=0))

    def expected_goals(self, home: str, away: str) -> tuple[float, float]:
        home_strength, away_strength = self.strength(home), self.strength(away)
        return (self.home_avg * home_strength.attack * away_strength.defence,
                self.away_avg * away_strength.attack * home_strength.defence)

    def predict(self, home: str, away: str) -> tuple[float, float, float]:
        return outcome_probabilities(*self.expected_goals(home, away))
