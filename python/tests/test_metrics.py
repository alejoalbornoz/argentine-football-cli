import math

import numpy as np
import pytest

from match_data import AWAY_WIN, DRAW, HOME_WIN
from metrics import accuracy, brier_score, log_loss

OUTCOMES = [HOME_WIN, DRAW, AWAY_WIN]


def test_perfect_predictions_score_perfectly():
    certain = np.eye(3)  # each row puts 100% on what happened

    assert log_loss(certain, OUTCOMES) == pytest.approx(0, abs=1e-12)
    assert brier_score(certain, OUTCOMES) == 0
    assert accuracy(certain, OUTCOMES) == 1


def test_guessing_a_third_each_gives_the_reference_scores():
    uniform = np.full((3, 3), 1 / 3)

    assert log_loss(uniform, OUTCOMES) == pytest.approx(math.log(3))   # about 1.099
    assert brier_score(uniform, OUTCOMES) == pytest.approx(2 / 3)


def test_scores_for_one_prediction_worked_by_hand():
    # 50% home, 30% draw, 20% away, and it was a draw.
    prediction = [[0.5, 0.3, 0.2]]

    assert log_loss(prediction, [DRAW]) == pytest.approx(-math.log(0.3))
    assert brier_score(prediction, [DRAW]) == pytest.approx(0.5 ** 2 + 0.7 ** 2 + 0.2 ** 2)
    assert accuracy(prediction, [DRAW]) == 0  # the pick was a home win


def test_a_zero_chance_that_happens_is_very_costly_but_finite():
    loss = log_loss([[1.0, 0.0, 0.0]], [AWAY_WIN])

    assert math.isfinite(loss)
    assert loss > 30
