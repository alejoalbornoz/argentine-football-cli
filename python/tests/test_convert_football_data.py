from convert_football_data import OUTPUT_COLUMNS, convert, main, read_source

# Same layout as the real ARG.csv, with a single odds column standing in for the rest.
HEADER = "Country,League,Season,Date,Time,Home,Away,HG,AG,Res,AvgCH"


def write_source(tmp_path, *rows):
    source = tmp_path / "ARG.csv"
    source.write_text("﻿" + "\n".join([HEADER, *rows]) + "\n", encoding="utf-8")
    return source


def convert_rows(tmp_path, *rows):
    return convert(read_source(write_source(tmp_path, *rows)))


def test_maps_source_columns_to_the_app_format(tmp_path):
    matches = convert_rows(
        tmp_path,
        "Argentina,Liga Profesional ,2012/2013,03/08/2012,23:00,Arsenal Sarandi,Union de Santa Fe,1,0,H,1.76",
    )

    assert list(matches.columns) == OUTPUT_COLUMNS
    assert matches.iloc[0].to_dict() == {
        "season": "2012/2013",
        "competition": "Liga Profesional",  # trailing space removed
        "date": "2012-08-03",
        "home_team": "Arsenal Sarandi",
        "away_team": "Union de Santa Fe",
        "home_goals": 1,
        "away_goals": 0,
    }


def test_sorts_by_kickoff_time_not_just_by_date(tmp_path):
    matches = convert_rows(
        tmp_path,
        "Argentina,Liga Profesional,2024,04/08/2024,18:10,Racing Club,Tigre,1,1,D,2.0",
        "Argentina,Liga Profesional,2024,04/08/2024,01:10,Velez Sarsfield,Lanus,3,0,H,2.0",
        "Argentina,Liga Profesional,2024,03/08/2024,23:00,Boca Juniors,Huracan,2,2,D,2.0",
    )

    assert list(matches["home_team"]) == ["Boca Juniors", "Velez Sarsfield", "Racing Club"]


def test_merges_spelling_variants_of_the_same_club(tmp_path):
    matches = convert_rows(
        tmp_path,
        "Argentina,Liga Profesional,2015,01/03/2015,20:00,Colon Santa FE,Tigre,1,0,H,2.0",
        "Argentina,Liga Profesional,2015,08/03/2015,20:00,Tigre,Colon Santa Fe,0,0,D,2.0",
    )

    assert list(matches["home_team"]) == ["Colon Santa Fe", "Tigre"]
    assert list(matches["away_team"]) == ["Tigre", "Colon Santa Fe"]


def test_skips_fixtures_without_a_result(tmp_path):
    matches = convert_rows(
        tmp_path,
        "Argentina,Liga Profesional,2026,19/10/2026,23:00,Platense,Tigre,2,1,H,2.0",
        "Argentina,Liga Profesional,2026,26/10/2026,21:00,Boca Juniors,River Plate,,,,",
    )

    assert list(matches["home_team"]) == ["Platense"]


def test_main_writes_the_csv_the_java_app_reads(tmp_path, capsys):
    source = write_source(
        tmp_path,
        "Argentina,Copa De La Liga Profesional,2024,27/01/2024,22:00,Banfield,Huracan,1,2,A,2.5",
        "Argentina,Liga Profesional,2026,26/10/2026,21:00,Boca Juniors,River Plate,,,,",
    )
    output = tmp_path / "matches.csv"

    main([str(source), str(output)])

    assert output.read_text(encoding="utf-8") == (
        "season,competition,date,home_team,away_team,home_goals,away_goals\n"
        "2024,Copa De La Liga Profesional,2024-01-27,Banfield,Huracan,1,2\n"
    )
    assert "1 fixtures without a result skipped" in capsys.readouterr().out
