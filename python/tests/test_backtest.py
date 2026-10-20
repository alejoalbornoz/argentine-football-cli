import json

import numpy as np
import pandas as pd
import pytest

from backtest import BASELINE, main, walk_forward

START = "2023-01-01"

# Four warm-up matches in 2022, then two evaluated dates with two matches each.
ROWS = [
    ("2022-03-01", "A", "B", 2, 0),
    ("2022-03-01", "C", "D", 1, 1),
    ("2022-03-08", "B", "C", 0, 1),
    ("2022-03-08", "D", "A", 0, 2),
    ("2023-03-01", "A", "C", 1, 0),  # first evaluated date
    ("2023-03-01", "B", "D", 2, 2),
    ("2023-03-08", "C", "A", 0, 0),  # last evaluated date
    ("2023-03-08", "D", "B", 1, 0),
]


def frame(rows):
    return pd.DataFrame(rows, columns=["match_date", "home_team", "away_team", "home_goals", "away_goals"])


def replace(rows, index, row):
    return rows[:index] + [row] + rows[index + 1:]


def test_predicts_every_evaluated_match_with_valid_probabilities():
    predictions = walk_forward(frame(ROWS), START)

    assert set(predictions) == {"Elo", "Poisson", BASELINE}
    for probabilities in predictions.values():
        assert probabilities.shape == (4, 3)
        np.testing.assert_allclose(probabilities.sum(axis=1), 1)


def test_a_days_predictions_do_not_depend_on_that_days_results():
    # No leakage: rewriting both results of the last day must not move its predictions at all.
    changed = ROWS[:6] + [("2023-03-08", "C", "A", 5, 0), ("2023-03-08", "D", "B", 0, 3)]

    original, other = walk_forward(frame(ROWS), START), walk_forward(frame(changed), START)

    for model in original:
        np.testing.assert_array_equal(original[model], other[model], err_msg=model)


def test_earlier_results_do_change_later_predictions():
    # The models keep learning during the evaluation: a different first-day result moves day two.
    changed = replace(ROWS, 4, ("2023-03-01", "A", "C", 0, 4))

    original, other = walk_forward(frame(ROWS), START), walk_forward(frame(changed), START)

    for model in original:
        np.testing.assert_array_equal(original[model][:2], other[model][:2], err_msg=model)
        assert not np.allclose(original[model][2:], other[model][2:]), model


def test_baseline_uses_the_outcome_rates_seen_so_far():
    predictions = walk_forward(frame(ROWS), START)[BASELINE]

    # Warm-up: 1 home win, 1 draw, 2 away wins. After day one: +1 home win, +1 draw.
    np.testing.assert_allclose(predictions[0], [1 / 4, 1 / 4, 2 / 4])
    np.testing.assert_allclose(predictions[2], [2 / 6, 2 / 6, 2 / 6])


@pytest.mark.parametrize("start, message", [
    ("2030-01-01", "no hay partidos desde el 2030-01-01 para evaluar"),
    ("2000-01-01", "no hay partidos antes del 2000-01-01 para calentar los modelos"),
])
def test_needs_matches_on_both_sides_of_the_start_date(start, message):
    with pytest.raises(ValueError, match=message):
        walk_forward(frame(ROWS), start)


def test_main_prints_the_scores_of_each_model_and_the_baseline(make_db, capsys):
    exit_code = main(["--db", str(make_db(ROWS))])  # default --from is 2023-01-01

    assert exit_code == 0
    result = json.loads(capsys.readouterr().out)
    assert (result["evaluated_from"], result["evaluated_to"]) == ("2023-03-01", "2023-03-08")
    assert (result["matches"], result["warm_up_matches"]) == (4, 4)
    assert result["actual"] == {"home_win": 0.5, "draw": 0.5, "away_win": 0.0}
    assert [r["model"] for r in result["results"]] == ["Elo", "Poisson", BASELINE]

    baseline = result["results"][2]
    assert baseline["accuracy"] == 0.5  # always picks a home win; 2 of the 4 were home wins
    for scores in result["results"]:
        assert scores["log_loss"] > 0
        assert 0 <= scores["brier"] <= 2


def test_main_reports_errors_on_stderr(make_db, capsys):
    exit_code = main(["--db", str(make_db(ROWS)), "--from", "2030-01-01"])

    assert exit_code == 1
    assert "no hay partidos desde el 2030-01-01" in capsys.readouterr().err
