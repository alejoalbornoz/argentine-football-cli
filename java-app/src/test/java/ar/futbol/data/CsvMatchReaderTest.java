package ar.futbol.data;

import ar.futbol.model.Match;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvMatchReaderTest {

    private static final String HEADER =
            "season,competition,date,home_team,away_team,home_goals,away_goals\n";

    private final CsvMatchReader reader = new CsvMatchReader();

    private CsvMatchReader.Result parse(String csv) throws IOException {
        return reader.read(new BufferedReader(new StringReader(csv)));
    }

    @Test
    void readsValidRows() throws IOException {
        var result = parse(HEADER
                + "2024,League,2024-02-10,Alpha,Beta,2,1\n"
                + "2024, Cup ,2024-02-17, Beta , Gamma ,0,0\n");

        assertEquals(List.of(
                new Match("2024", "League", LocalDate.of(2024, 2, 10), "Alpha", "Beta", 2, 1),
                new Match("2024", "Cup", LocalDate.of(2024, 2, 17), "Beta", "Gamma", 0, 0)),
                result.matches());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void acceptsAnyColumnOrderAndIgnoresExtraColumns() throws IOException {
        var result = parse("home_team,away_team,referee,home_goals,away_goals,date,competition,season\n"
                + "Alpha,Beta,Someone,3,2,2024-03-01,League,2024\n");

        assertEquals(List.of(new Match("2024", "League", LocalDate.of(2024, 3, 1), "Alpha", "Beta", 3, 2)),
                result.matches());
    }

    @Test
    void skipsBlankLinesAndStripsByteOrderMark() throws IOException {
        var result = parse("﻿" + HEADER + "\n2024,League,2024-02-10,Alpha,Beta,2,1\n   \n");

        assertEquals(1, result.matches().size());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void reportsEachInvalidRowWithItsLineNumberAndKeepsTheValidOnes() throws IOException {
        var result = parse(HEADER
                + "2024,League,2024-02-10,Alpha,Beta,2,1\n"       // line 2: ok
                + "2024,League,10/02/2024,Alpha,Gamma,2,1\n"      // line 3: bad date
                + "2024,League,2024-02-11,Alpha,Gamma,two,1\n"    // line 4: goals not a number
                + "2024,League,2024-02-12,Alpha,Gamma,-1,1\n"     // line 5: negative goals
                + "2024,League,2024-02-13,Alpha,Alpha,1,1\n"      // line 6: same team twice
                + "2024,League,2024-02-14,Alpha,Gamma,1\n"        // line 7: missing field
                + "2024,League,2024-02-15,,Gamma,1,0\n"           // line 8: empty team
                + "2024,,2024-02-15,Alpha,Gamma,1,0\n"            // line 9: empty competition
                + "2024,Cup,2024-02-10,Alpha,Beta,0,0\n"          // line 10: duplicate of line 2
                + "2024,League,2024-02-16,Beta,Gamma,1,0\n");     // line 11: ok

        assertEquals(2, result.matches().size());
        assertEquals(List.of(
                "línea 3: la fecha '10/02/2024' no tiene el formato yyyy-MM-dd",
                "línea 4: home_goals 'two' no es un número entero",
                "línea 5: los goles no pueden ser negativos",
                "línea 6: un equipo no puede jugar contra sí mismo (Alpha)",
                "línea 7: se esperaban 7 campos pero hay 6",
                "línea 8: home_team está vacío",
                "línea 9: competition está vacío",
                "línea 10: partido repetido: Alpha vs Beta el 2024-02-10"),
                result.errors());
    }

    @Test
    void missingRequiredColumnIsFatal() {
        var e = assertThrows(IOException.class,
                () -> parse("season,date,home_team,away_team,home_goals\n"));
        assertEquals("al encabezado del CSV le faltan columnas: competition, away_goals", e.getMessage());
    }

    @Test
    void emptyFileIsFatal() {
        assertThrows(IOException.class, () -> parse(""));
    }

    @Test
    void readsUtf8TeamNamesFromAFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("matches.csv");
        Files.writeString(file, HEADER + "2024,League,2024-02-10,Club Ñandú,Unión del Sur,1,0\n",
                StandardCharsets.UTF_8);

        Match match = reader.read(file).matches().getFirst();

        assertEquals("Club Ñandú", match.homeTeam());
        assertEquals("Unión del Sur", match.awayTeam());
    }
}
