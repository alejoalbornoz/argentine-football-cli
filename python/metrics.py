"""Scores for probabilistic predictions of home win / draw / away win.

probabilities: array of shape (matches, 3), columns in HOME_WIN, DRAW, AWAY_WIN order
outcomes:      array with one outcome code (0, 1 or 2) per match
"""

import numpy as np

_SMALLEST = 1e-15  # log(0) is -infinity; a 0% prediction that happens still gets a (huge) finite cost


def log_loss(probabilities, outcomes) -> float:
    """Average of -log(probability given to what actually happened). Lower is better.

    Confident mistakes cost the most: giving the actual result 50% costs 0.69, giving it 5% costs 3.0.
    Guessing 1/3 for everything scores ln(3), about 1.099.
    """
    probabilities, outcomes = np.asarray(probabilities), np.asarray(outcomes)
    given = probabilities[np.arange(len(outcomes)), outcomes]
    return float(-np.mean(np.log(np.clip(given, _SMALLEST, 1.0))))


def brier_score(probabilities, outcomes) -> float:
    """Average squared gap between the probabilities and what happened (1 for it, 0 for the others).

    From 0 (perfect) to 2 (certain and wrong). Guessing 1/3 for everything scores 2/3.
    """
    probabilities, outcomes = np.asarray(probabilities), np.asarray(outcomes)
    happened = np.eye(3)[outcomes]  # one-hot rows: a draw becomes [0, 1, 0]
    return float(np.mean(np.sum((probabilities - happened) ** 2, axis=1)))


def accuracy(probabilities, outcomes) -> float:
    """Share of matches whose most likely outcome is the one that happened."""
    probabilities, outcomes = np.asarray(probabilities), np.asarray(outcomes)
    return float(np.mean(np.argmax(probabilities, axis=1) == outcomes))
