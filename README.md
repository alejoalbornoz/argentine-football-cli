# Argentine Football CLI

Terminal app with an interactive menu for stats and match predictions of the Argentine Liga Profesional.
Java handles the menu, data loading and stats; Python scripts handle the prediction models (Elo, Poisson).

## Menu

Pick an option by typing its number; `0` goes back (or exits from the main menu).

1. **Standings**: league table for a season (and competition, when a season has more than one)
2. **Team stats**: home/away split, goals for/against, last 5 results and streaks,
   for one season or all of them
3. **Head-to-head**: record between two teams by venue, and their latest meetings
4. **Predict a match**: home win / draw / away win probabilities from an Elo or a Poisson model (in Python)
5. **Evaluate models (backtest)**: log loss, Brier score and accuracy of both models against a baseline

## How predictions work

Java runs `python/predict.py` with `ProcessBuilder`, passing the database path and the two teams.
The script reads the matches from SQLite, prints the probabilities as JSON on stdout and Java shows them.
You can also run it by hand:

```bash
python python/predict.py --db data/futbol.db --home "Boca Juniors" --away "River Plate" --model poisson
```

Run `python python/predict.py --help` for the model options (`--k`, `--home-advantage`, `--window-days`).

**Elo** (`python/elo.py`): every team starts at 1500; after each match both ratings move by
`K × (result − expected)`, and the home team gets extra points (default 60) when computing
the expected result. Elo only gives an expected score, so the draw probability is modelled as
highest between even teams, with one factor fitted to the data by minimising log loss.

**Poisson** (`python/poisson.py`): each team gets an attack and a defence strength relative to the
league average, from the matches of the last 730 days. Expected home goals are
`league home average × home attack × away defence` (and the mirror for the away side), so home
advantage comes from home teams scoring more on average. Each side's goals follow a Poisson
distribution; adding up the score grid (0-0, 1-0, ...) gives the three probabilities. Every team
starts with 5 "average" matches, so a team with few results is pulled towards the average.

## Backtest

`python/backtest.py` replays the past date by date. Matches before 2023 only warm the models up;
from then on every match is predicted using only matches of *earlier dates*, and the models learn a
day's results only after predicting all of that day's matches. Tests check this: rewriting a day's
results leaves that day's predictions unchanged, while changing an earlier result does move them.

Results on the data up to 2026-10-20 (2,122 evaluated matches):

| Model                      | Log loss | Brier | Accuracy |
|----------------------------|---------:|------:|---------:|
| Elo                        |    1.067 | 0.645 |    42.2% |
| Poisson                    |    1.070 | 0.647 |    42.8% |
| Baseline: home always wins |    1.078 | 0.652 |    42.6% |
| *(1/3 for every outcome)*  |    1.099 | 0.667 |        - |

- **Log loss** averages −log(probability given to what happened): confident mistakes cost the most.
  **Brier** is the squared gap between the probabilities and the result. Lower is better for both.
- The baseline always picks a home win; for log loss and Brier it uses the home/draw/away rates seen so far.
- Both models beat the baseline on the probability scores, by a small margin. The Argentine league is
  famously even, with 32% draws in this period.
- Accuracy hardly separates them: a draw is almost never the single most likely outcome, so a third of
  matches are "wrong" for every model. That is why probabilistic scores are the ones to compare.

```bash
python python/backtest.py --db data/futbol.db --from 2024-01-01 --k 30
```

The app uses the interpreter in `.venv` if it exists, otherwise `python` on the PATH.
Set the `FUTBOL_PYTHON` environment variable to use a different one.

## Layout

```
java-app/   Java 21 + Maven: menu, CSV -> SQLite loading, stats
python/     data converter, models (elo.py, poisson.py), predict.py and backtest.py
data/       matches.csv (input) and futbol.db (generated). See data/README.md
```

## Requirements

- Java 21 (Maven is not needed: the project ships the Maven Wrapper, `mvnw`)
- Python 3.11

## Setup

```bash
py -3.11 -m venv .venv
.venv\Scripts\activate                 # Windows; on Linux/macOS: source .venv/bin/activate
pip install -r python/requirements.txt
```

Then download and convert the match data as described in [data/README.md](data/README.md#getting-the-data).

## Tests

```bash
cd java-app && ./mvnw test             # Java (JUnit 5)
python -m pytest python                # Python (pytest), from the project root
```

## Build and run

```bash
cd java-app
./mvnw package          # compiles, runs the tests, builds target/futbol-cli.jar
cd ..
java -jar java-app/target/futbol-cli.jar
```

On Windows use `mvnw.cmd` instead of `./mvnw` in `cmd`/PowerShell.

Run from the project root so the default paths (`data/matches.csv`, `data/futbol.db`) resolve,
or pass them explicitly: `java -jar java-app/target/futbol-cli.jar <matches.csv> <database.db>`.

## Status

- [x] Phase 1: project skeleton, CSV loading into SQLite
- [x] Phase 2: menu, standings, team stats
- [x] Phase 3: head-to-head, Elo model
- [x] Phase 4: Poisson model
- [x] Phase 5: backtesting
