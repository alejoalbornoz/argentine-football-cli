# Data

The app reads `data/matches.csv` (UTF-8) and loads it into `data/futbol.db` (SQLite) every time it starts.
The CSV is the source of truth; the `.db` file can be deleted at any time.
None of these data files are committed: see [Getting the data](#getting-the-data).

## CSV format

```csv
season,competition,date,home_team,away_team,home_goals,away_goals
2024,Liga Profesional,2024-05-14,Team A,Team B,2,1
```

| column        | type    | notes                                                    |
|---------------|---------|----------------------------------------------------------|
| `season`      | text    | free text, e.g. `2024` or `2019/2020`; sorted as text    |
| `competition` | text    | e.g. `Liga Profesional`, `Copa De La Liga Profesional`   |
| `date`        | date    | ISO `yyyy-MM-dd`                                         |
| `home_team`   | text    | the same team must always be spelled the same way        |
| `away_team`   | text    | must differ from `home_team`                             |
| `home_goals`  | integer | `>= 0`                                                   |
| `away_goals`  | integer | `>= 0`                                                   |

- Column order doesn't matter and extra columns are ignored.
- Plain comma-separated values: no quoting, so names cannot contain commas.
- Invalid rows are skipped and reported with their line number. A missing column stops the load.
- The same `date + home_team + away_team` twice counts as a duplicate.
- Matches on the same date are kept in file order, so the file should be sorted by kickoff time.

## Getting the data

Results come from **[Football-Data.co.uk](https://www.football-data.co.uk/argentina.php)**
(Argentina file, 2012/2013 season onwards), which credits XScores for results.
The site publishes no explicit license, so the files are used locally and not redistributed here.

From the project root (with the Python venv active):

```bash
curl -L -o data/raw/ARG.csv https://www.football-data.co.uk/new/ARG.csv
python python/convert_football_data.py data/raw/ARG.csv data/matches.csv
```

The converter ([python/convert_football_data.py](../python/convert_football_data.py)):
- keeps only played matches and the result columns (the betting odds are dropped),
- turns `dd/mm/yyyy` dates into ISO and sorts by kickoff time,
- strips stray spaces and merges spelling variants of the same club (`Colon Santa FE` → `Colon Santa Fe`).

Known quirk: kickoff times in the source appear to be UK time, so a late-night match in Argentina
can carry the next day's date. The order of matches is still correct.
