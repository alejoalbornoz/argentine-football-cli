import sqlite3
from contextlib import closing

import pytest

# Same table as java-app's Database.java creates.
SCHEMA = """
    CREATE TABLE matches (
        id          INTEGER PRIMARY KEY,
        season      TEXT    NOT NULL,
        competition TEXT    NOT NULL,
        match_date  TEXT    NOT NULL,
        home_team   TEXT    NOT NULL,
        away_team   TEXT    NOT NULL,
        home_goals  INTEGER NOT NULL,
        away_goals  INTEGER NOT NULL
    )
"""

INSERT = """
    INSERT INTO matches (season, competition, match_date, home_team, away_team, home_goals, away_goals)
    VALUES ('2024', 'League', ?, ?, ?, ?, ?)
"""


@pytest.fixture
def make_db(tmp_path):
    """A function that saves (date, home, away, home_goals, away_goals) rows to a new database."""

    def make(rows):
        path = tmp_path / "test.db"
        with closing(sqlite3.connect(path)) as connection:
            connection.execute(SCHEMA)
            connection.executemany(INSERT, rows)
            connection.commit()
        return path

    return make
