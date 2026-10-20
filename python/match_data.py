"""Reading matches from the app's SQLite database, plus the outcome codes the models share."""

import sqlite3
from contextlib import closing
from pathlib import Path

import pandas as pd

HOME_WIN, DRAW, AWAY_WIN = 0, 1, 2

# Same order as the Java app: by date, and same-day matches in kickoff order (insertion id).
QUERY = """
    SELECT season, competition, match_date, home_team, away_team, home_goals, away_goals
    FROM matches
    ORDER BY match_date, id
"""


def load_matches(db_path: Path) -> pd.DataFrame:
    """All matches in chronological order. The database is opened read-only."""
    db_path = Path(db_path)
    if not db_path.is_file():
        # sqlite3.connect would silently create an empty database file instead.
        raise FileNotFoundError(f"database not found: {db_path}")
    # closing(): "with sqlite3.connect(...)" alone only commits, it does not close the connection.
    with closing(sqlite3.connect(db_path.resolve().as_uri() + "?mode=ro", uri=True)) as connection:
        return pd.read_sql_query(QUERY, connection)


def outcome(home_goals: int, away_goals: int) -> int:
    """HOME_WIN, DRAW or AWAY_WIN."""
    if home_goals > away_goals:
        return HOME_WIN
    return DRAW if home_goals == away_goals else AWAY_WIN
