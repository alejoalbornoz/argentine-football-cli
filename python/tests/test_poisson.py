import math

import pandas as pd
import pytest

from poisson import PRIOR_MATCHES, PoissonModel, Strength, outcome_probabilities, score_matrix


def matches(*rows):
    """A DataFrame from (date, home, away, home_goals, away_goals) rows."""
    return pd.DataFrame(rows, columns=["match_date", "home_team", "away_team", "home_goals", "away_goals"])


def fitted(rows, **params):
    model = PoissonModel(**params)
    model.fit(matches(*rows))
    return model


def test_score_matrix_is_a_probability_table_with_home_goals_as_rows():
    matrix = score_matrix(2.0, 0.5)

    assert matrix.sum() == pytest.approx(1)
    assert matrix[1, 0] > matrix[0, 1]  # 1-0 is likelier than 0-1 for the stronger home side


def test_even_teams_have_symmetric_chances_and_the_textbook_draw_rate():
    home, draw, away = outcome_probabilities(1.0, 1.0)

    assert home == pytest.approx(away)
    assert home + draw + away == pytest.approx(1)
    # P(draw) = sum over k of P(k goals)^2, about 0.3085 when each side expects one goal
    assert draw == pytest.approx(math.exp(-2) * sum(1 / math.factorial(k) ** 2 for k in range(20)))


def test_more_expected_goals_means_more_wins():
    assert outcome_probabilities(2.0, 1.0)[0] > outcome_probabilities(1.2, 1.0)[0]


def test_identical_teams_differ_only_by_home_advantage():
    # Each team scored 3 and conceded 3, but home sides scored twice as much as away sides.
    model = fitted([
        ("2024-01-01", "A", "B", 2, 1),
        ("2024-01-08", "B", "A", 2, 1),
    ])

    assert (model.home_avg, model.away_avg) == (2.0, 1.0)
    assert model.strength("A") == model.strength("B") == Strength(attack=1.0, defence=1.0, matches=2)
    assert model.expected_goals("A", "B") == pytest.approx((2.0, 1.0))


def test_strengths_are_relative_to_the_league_and_shrunk_towards_average():
    model = fitted([
        ("2024-01-01", "A", "B", 3, 0),
        ("2024-01-08", "C", "A", 0, 3),
        ("2024-01-15", "B", "C", 1, 1),
    ])
    league_avg = 8 / 6  # 8 goals, 6 team-matches

    a = model.strength("A")  # scored 6 and conceded 0 in 2 matches
    assert a.attack == pytest.approx((6 + PRIOR_MATCHES * league_avg) / ((2 + PRIOR_MATCHES) * league_avg))
    assert 1 < a.attack < (6 / 2) / league_avg  # better than average, but less than the raw ratio
    assert a.defence < 1


def test_only_matches_inside_the_window_count():
    model = fitted([
        ("2020-01-01", "A", "B", 9, 0),  # years before the window: ignored
        ("2024-01-01", "A", "B", 1, 1),
        ("2024-01-20", "B", "A", 1, 1),
    ], window_days=30)

    assert model.window_matches == 2
    assert model.strength("A").attack == pytest.approx(1.0)


def test_teams_without_recent_matches_are_assumed_average():
    model = fitted([("2024-01-01", "A", "B", 1, 0)])

    assert model.strength("Z") == Strength(attack=1.0, defence=1.0, matches=0)


def test_fitting_without_matches_is_an_error():
    with pytest.raises(ValueError):
        fitted([])
