"""Elo ratings with home advantage, turned into home win / draw / away win probabilities.

Elo only gives an *expected score* E for the home team (win = 1, draw = 0.5, loss = 0), which
mixes wins and draws: E = P(home win) + 0.5 * P(draw). To split it we assume draws are most
likely between even teams and rarer as one side becomes a clear favourite:

    P(draw) = draw_factor * (1 - (2E - 1)^2)

draw_factor is learned from the data (see fit_draw_factor). Keeping it at or below 0.5
guarantees that all three probabilities stay between 0 and 1.
"""

import numpy as np
import pandas as pd
from scipy.optimize import minimize_scalar

from match_data import DRAW, HOME_WIN, outcome
from metrics import log_loss

INITIAL_RATING = 1500.0
DEFAULT_K = 20.0
DEFAULT_HOME_ADVANTAGE = 60.0  # the data's home record is worth about 57 Elo points
DEFAULT_DRAW_FACTOR = 0.35
MAX_DRAW_FACTOR = 0.5


def outcome_probabilities(expected, draw_factor: float):
    """Split an expected home score into (home win, draw, away win) probabilities.

    Works on single numbers and on numpy arrays alike.
    """
    p_draw = draw_factor * (1.0 - (2.0 * expected - 1.0) ** 2)
    return expected - p_draw / 2, p_draw, 1.0 - expected - p_draw / 2


def fit_draw_factor(expected: np.ndarray, outcomes: np.ndarray) -> float:
    """The draw_factor that best explains the observed outcomes (lowest log loss, see metrics.py)."""

    def loss(draw_factor: float) -> float:
        return log_loss(np.column_stack(outcome_probabilities(expected, draw_factor)), outcomes)

    result = minimize_scalar(loss, bounds=(1e-3, MAX_DRAW_FACTOR), method="bounded")
    return float(result.x)


class Elo:
    """Ratings that learn match by match.

    To evaluate without peeking at the future, call predict() for a match *before* update().
    """

    def __init__(self, k: float = DEFAULT_K, home_advantage: float = DEFAULT_HOME_ADVANTAGE,
                 draw_factor: float = DEFAULT_DRAW_FACTOR):
        self.k = k
        self.home_advantage = home_advantage
        self.draw_factor = draw_factor
        self.ratings: dict[str, float] = {}

    def rating(self, team: str) -> float:
        """A team's current rating; teams not seen yet start at INITIAL_RATING."""
        return self.ratings.get(team, INITIAL_RATING)

    def expected_home_score(self, home: str, away: str) -> float:
        difference = self.rating(home) + self.home_advantage - self.rating(away)
        return 1.0 / (1.0 + 10.0 ** (-difference / 400.0))

    def predict(self, home: str, away: str) -> tuple[float, float, float]:
        """(home win, draw, away win) probabilities with the current ratings."""
        return outcome_probabilities(self.expected_home_score(home, away), self.draw_factor)

    def update(self, home: str, away: str, home_goals: int, away_goals: int) -> None:
        """Move both ratings toward the result. Points won by one team are lost by the other."""
        result = outcome(home_goals, away_goals)
        actual = 1.0 if result == HOME_WIN else 0.5 if result == DRAW else 0.0
        change = self.k * (actual - self.expected_home_score(home, away))
        self.ratings[home] = self.rating(home) + change
        self.ratings[away] = self.rating(away) - change

    def fit(self, matches: pd.DataFrame) -> None:
        """Rate all matches in order, then learn draw_factor from how often draws really happened.

        Each match's expected score is taken *before* its result is applied, so the draw fit
        only sees what the ratings knew at kickoff.
        """
        expected, outcomes = [], []
        for match in matches.itertuples(index=False):
            expected.append(self.expected_home_score(match.home_team, match.away_team))
            outcomes.append(outcome(match.home_goals, match.away_goals))
            self.update(match.home_team, match.away_team, match.home_goals, match.away_goals)
        if outcomes:
            self.draw_factor = fit_draw_factor(np.array(expected), np.array(outcomes))
