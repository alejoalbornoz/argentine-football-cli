import numpy as np
import pandas as pd
import pytest

from elo import INITIAL_RATING, Elo, fit_draw_factor, outcome_probabilities


def elo_with(ratings, **params):
    model = Elo(**params)
    model.ratings = dict(ratings)
    return model


def test_equal_teams_on_neutral_ground_are_even():
    assert Elo(home_advantage=0).expected_home_score("A", "B") == pytest.approx(0.5)


def test_home_advantage_counts_like_extra_rating_points():
    model = elo_with({"A": 1560, "B": 1620}, home_advantage=60)

    assert model.expected_home_score("A", "B") == pytest.approx(0.5)


def test_a_400_point_gap_means_ten_to_one_odds():
    model = elo_with({"A": 1900, "B": 1500}, home_advantage=0)

    assert model.expected_home_score("A", "B") == pytest.approx(10 / 11)


def test_update_moves_points_from_the_loser_to_the_winner():
    model = Elo(k=20, home_advantage=0)

    model.update("A", "B", 2, 0)

    assert model.rating("A") == pytest.approx(1510)  # 20 * (1 - 0.5)
    assert model.rating("B") == pytest.approx(1490)


def test_a_home_draw_costs_the_home_team_points():
    model = Elo(k=20, home_advantage=60)

    model.update("A", "B", 1, 1)  # the home side was expected to do better than a draw

    assert model.rating("A") < INITIAL_RATING < model.rating("B")


@pytest.mark.parametrize("expected", [0.01, 0.2, 0.5, 0.8, 0.99])
def test_probabilities_are_valid_and_add_up_to_the_expected_score(expected):
    home, draw, away = outcome_probabilities(expected, draw_factor=0.5)

    assert min(home, draw, away) >= 0
    assert home + draw + away == pytest.approx(1)
    assert home + draw / 2 == pytest.approx(expected)


def test_draws_are_likeliest_between_even_teams():
    draws = [outcome_probabilities(e, 0.3)[1] for e in (0.1, 0.3, 0.5, 0.7, 0.9)]

    assert max(draws) == draws[2] == pytest.approx(0.3)
    assert draws[0] == pytest.approx(draws[4])


def test_fit_draw_factor_recovers_the_true_value_from_simulated_matches():
    # Simulate 20,000 matches whose results follow draw_factor = 0.3; the fit should find it.
    rng = np.random.default_rng(seed=42)
    expected = rng.uniform(0.2, 0.8, size=20_000)
    home, draw, _ = outcome_probabilities(expected, 0.3)
    u = rng.random(expected.size)
    outcomes = (u > home).astype(int) + (u > home + draw).astype(int)  # 0 home, 1 draw, 2 away

    assert fit_draw_factor(expected, outcomes) == pytest.approx(0.3, abs=0.02)


def test_fit_rates_matches_in_order_and_unseen_teams_keep_the_initial_rating():
    matches = pd.DataFrame({
        "home_team": ["A", "B"],
        "away_team": ["B", "A"],
        "home_goals": [1, 0],
        "away_goals": [0, 0],
    })
    model = Elo(k=20, home_advantage=0)

    model.fit(matches)

    # A won (+10), then the draw at B gave a little of it back.
    assert INITIAL_RATING < model.rating("A") < INITIAL_RATING + 10
    assert model.rating("A") + model.rating("B") == pytest.approx(2 * INITIAL_RATING)
    assert model.rating("Unseen") == INITIAL_RATING
