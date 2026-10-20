import json

import pytest

from predict import main, number

MATCHES = [
    ("2024-02-10", "Alpha", "Beta", 2, 0),
    ("2024-02-17", "Beta", "Gamma", 1, 1),
    ("2024-02-24", "Gamma", "Alpha", 0, 3),
]


def run(db, *options):
    return main(["--db", str(db), *options])


def test_prints_the_probabilities_as_json(make_db, capsys):
    exit_code = run(make_db(MATCHES), "--home", "Alpha", "--away", "Gamma")

    assert exit_code == 0
    result = json.loads(capsys.readouterr().out)
    assert result["model"] == "Elo"
    assert (result["home_team"], result["away_team"]) == ("Alpha", "Gamma")
    assert result["home_win"] + result["draw"] + result["away_win"] == pytest.approx(1)
    assert result["home_win"] > result["away_win"]  # Alpha won both its matches and plays at home
    assert result["details"]["Último partido de Alpha"] == "24/02/2024"
    assert result["details"]["Partidos usados"] == "3"


def test_k_and_home_advantage_can_be_changed(make_db, capsys):
    run(make_db(MATCHES), "--home", "Alpha", "--away", "Gamma", "--k", "40", "--home-advantage", "0")

    details = json.loads(capsys.readouterr().out)["details"]
    assert details["Factor K"] == "40"
    assert details["Ventaja de local"] == "0 puntos"


def test_poisson_model_reports_expected_goals(make_db, capsys):
    exit_code = run(make_db(MATCHES), "--home", "Alpha", "--away", "Gamma", "--model", "poisson")

    assert exit_code == 0
    result = json.loads(capsys.readouterr().out)
    assert result["model"] == "Poisson"
    assert result["home_win"] + result["draw"] + result["away_win"] == pytest.approx(1)
    assert result["home_win"] > result["away_win"]
    assert result["details"]["Goles esperados"].startswith("Alpha ")
    assert result["details"]["Ventana"] == "últimos 730 días hasta el 24/02/2024 (3 partidos)"


def test_numbers_are_shown_with_a_decimal_comma():
    assert number(1.333, 2) == "1,33"
    assert number(60.0) == "60"
    assert number(2.5) == "2,5"


def test_unknown_model_is_rejected_by_the_argument_parser(make_db):
    with pytest.raises(SystemExit) as exit_info:
        run(make_db(MATCHES), "--home", "Alpha", "--away", "Gamma", "--model", "magic")

    assert exit_info.value.code == 2


@pytest.mark.parametrize("home, away, message", [
    ("Alpha", "Nobody", "equipo desconocido: Nobody"),
    ("Alpha", "Alpha", "el local y el visitante tienen que ser equipos distintos"),
])
def test_bad_teams_are_reported_on_stderr(make_db, capsys, home, away, message):
    exit_code = run(make_db(MATCHES), "--home", home, "--away", away)

    captured = capsys.readouterr()
    assert exit_code == 1
    assert captured.out == ""
    assert message in captured.err


def test_missing_database_is_reported_and_not_created(tmp_path, capsys):
    db = tmp_path / "missing.db"

    exit_code = run(db, "--home", "Alpha", "--away", "Beta")

    assert exit_code == 1
    assert "no se encontró la base de datos" in capsys.readouterr().err
    assert not db.exists()
