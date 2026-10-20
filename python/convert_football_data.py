"""Convert football-data.co.uk's Argentina file (new/ARG.csv) into the app's matches.csv format.

Usage, from the project root:
    python python/convert_football_data.py data/raw/ARG.csv data/matches.csv
"""

import argparse
from pathlib import Path

import pandas as pd

OUTPUT_COLUMNS = ["season", "competition", "date", "home_team", "away_team", "home_goals", "away_goals"]

# The source spells some clubs in more than one way; every variant maps to a single name.
TEAM_ALIASES = {
    "Colon Santa FE": "Colon Santa Fe",
}


def read_source(path: Path) -> pd.DataFrame:
    # utf-8-sig drops the byte-order mark. dtype=str stops pandas from guessing types,
    # so a season like "2014" stays text, just like "2012/2013".
    return pd.read_csv(path, encoding="utf-8-sig", dtype=str)


def convert(raw: pd.DataFrame) -> pd.DataFrame:
    """Return the played matches in the app's format, in kickoff order."""
    played = raw.dropna(subset=["HG", "AG"])  # fixtures without a result yet

    kickoff = pd.to_datetime(played["Date"] + " " + played["Time"], format="%d/%m/%Y %H:%M")
    matches = pd.DataFrame({
        "season": played["Season"].str.strip(),
        "competition": played["League"].str.strip(),
        "date": kickoff.dt.strftime("%Y-%m-%d"),
        "home_team": played["Home"].str.strip().replace(TEAM_ALIASES),
        "away_team": played["Away"].str.strip().replace(TEAM_ALIASES),
        "home_goals": played["HG"].astype(int),
        "away_goals": played["AG"].astype(int),
    })
    # Sort by full kickoff, not just the date: the Java app keeps same-day matches in file order.
    order = kickoff.sort_values(kind="stable").index
    return matches.loc[order, OUTPUT_COLUMNS].reset_index(drop=True)


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("source", type=Path, help="ARG.csv downloaded from football-data.co.uk")
    parser.add_argument("output", type=Path, help="where to write matches.csv")
    args = parser.parse_args(argv)

    raw = read_source(args.source)
    matches = convert(raw)
    matches.to_csv(args.output, index=False, lineterminator="\n")

    skipped = len(raw) - len(matches)
    print(f"Wrote {len(matches)} matches to {args.output} ({skipped} fixtures without a result skipped)")


if __name__ == "__main__":
    main()
