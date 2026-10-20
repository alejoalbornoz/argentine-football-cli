package ar.futbol.data;

import ar.futbol.model.Match;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads matches from a UTF-8 CSV file with this header (column order does not matter,
 * extra columns are ignored):
 *
 * <pre>season,competition,date,home_team,away_team,home_goals,away_goals</pre>
 *
 * <p>Dates are ISO ({@code yyyy-MM-dd}). Fields are plain comma-separated values: quoting is not
 * supported, so team names must not contain commas.
 *
 * <p>A bad row does not stop the load: it is skipped and reported in {@link Result#errors()}
 * with its line number. A missing or incomplete header is fatal and throws {@link IOException}.
 */
public final class CsvMatchReader {

    public static final List<String> REQUIRED_COLUMNS = List.of(
            "season", "competition", "date", "home_team", "away_team", "home_goals", "away_goals");

    private static final char BOM = '﻿';

    public record Result(List<Match> matches, List<String> errors) {}

    public Result read(Path file) throws IOException {
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return read(in);
        }
    }

    public Result read(BufferedReader in) throws IOException {
        String header = in.readLine();
        if (header == null || header.isBlank()) {
            throw new IOException("el archivo CSV está vacío (falta la fila de encabezado)");
        }
        if (header.charAt(0) == BOM) {
            header = header.substring(1); // Excel likes to add a byte-order mark
        }
        String[] headerFields = header.split(",", -1);
        Map<String, Integer> columns = indexColumns(headerFields);

        List<Match> matches = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String line;
        int lineNumber = 1;
        while ((line = in.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            try {
                Match match = parseRow(line, headerFields.length, columns);
                String key = match.date() + "|" + match.homeTeam() + "|" + match.awayTeam();
                if (!seen.add(key)) {
                    throw new IllegalArgumentException("partido repetido: " + match.homeTeam()
                            + " vs " + match.awayTeam() + " el " + match.date());
                }
                matches.add(match);
            } catch (IllegalArgumentException e) {
                errors.add("línea " + lineNumber + ": " + e.getMessage());
            }
        }
        return new Result(List.copyOf(matches), List.copyOf(errors));
    }

    private static Map<String, Integer> indexColumns(String[] headerFields) throws IOException {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < headerFields.length; i++) {
            columns.put(headerFields[i].trim().toLowerCase(), i);
        }
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !columns.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new IOException("al encabezado del CSV le faltan columnas: " + String.join(", ", missing));
        }
        return columns;
    }

    private static Match parseRow(String line, int expectedFields, Map<String, Integer> columns) {
        String[] fields = line.split(",", -1);
        if (fields.length != expectedFields) {
            throw new IllegalArgumentException(
                    "se esperaban " + expectedFields + " campos pero hay " + fields.length);
        }
        return new Match(
                field(fields, columns, "season"),
                field(fields, columns, "competition"),
                parseDate(field(fields, columns, "date")),
                field(fields, columns, "home_team"),
                field(fields, columns, "away_team"),
                parseGoals(field(fields, columns, "home_goals"), "home_goals"),
                parseGoals(field(fields, columns, "away_goals"), "away_goals"));
    }

    private static String field(String[] fields, Map<String, Integer> columns, String name) {
        return fields[columns.get(name)].trim();
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("la fecha '" + value + "' no tiene el formato yyyy-MM-dd");
        }
    }

    private static int parseGoals(String value, String column) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(column + " '" + value + "' no es un número entero");
        }
    }
}
