from match_data import AWAY_WIN, DRAW, HOME_WIN, load_matches, outcome


def test_loads_matches_in_chronological_order(make_db):
    db = make_db([
        ("2024-03-01", "Alpha", "Beta", 1, 0),
        ("2024-02-01", "Beta", "Alpha", 2, 2),
    ])

    matches = load_matches(db)

    assert list(matches.match_date) == ["2024-02-01", "2024-03-01"]
    assert list(matches.columns) == [
        "season", "competition", "match_date", "home_team", "away_team", "home_goals", "away_goals"]


def test_outcome_is_seen_from_the_home_side():
    assert outcome(2, 1) == HOME_WIN
    assert outcome(1, 1) == DRAW
    assert outcome(0, 3) == AWAY_WIN
