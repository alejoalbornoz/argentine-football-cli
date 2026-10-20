package ar.futbol.cli;

import ar.futbol.model.Match;
import ar.futbol.model.Outcome;
import ar.futbol.predict.BacktestReport;
import ar.futbol.predict.Prediction;
import ar.futbol.stats.Standings;
import ar.futbol.stats.Streak;
import ar.futbol.stats.TeamStats;
import ar.futbol.stats.Totals;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Turns computed stats into the (Spanish) text shown in the terminal. */
final class Reports {

    private static final String STANDINGS_NOTES = """
            3 puntos por victoria y 1 por empate. Desempate: diferencia de gol y después goles a favor.
            Suma todos los partidos de la competición en esa temporada, así que las etapas
            (Apertura/Clausura, grupos, playoffs) quedan combinadas en una sola tabla.
            """;
    private static final String BACKTEST_NOTES = """
            Log loss y Brier: cuanto más bajo, mejor. Acierto: cuanto más alto, mejor.
            Dar 1/3 a cada resultado en todos los partidos daría log loss 1,099 y Brier 0,667.
            La base siempre elige la victoria local; como probabilidades usa las frecuencias
            de local, empate y visitante vistas hasta ese momento.
            """;
    private static final int RECENT_MEETINGS = 10;
    private static final int BAR_WIDTH = 40; // a 100% chance is 40 '#' characters

    // Fixed instead of the computer's defaults, so the output is always "1,50" and "20/10/2026".
    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Reports() {}

    static String standings(String title, List<Standings.Row> rows, int matchCount) {
        TextTable table = new TextTable().right("#").left("Equipo")
                .right("PJ").right("G").right("E").right("P")
                .right("GF").right("GC").right("DG").right("Pts");
        for (int i = 0; i < rows.size(); i++) {
            Totals t = rows.get(i).totals();
            table.row(i + 1, rows.get(i).team(), t.played(), t.won(), t.drawn(), t.lost(),
                    t.goalsFor(), t.goalsAgainst(), signed(t.goalDifference()), t.points());
        }
        return title + " - " + count(matchCount, "partido", "partidos") + "\n\n"
                + table.render() + "\n" + STANDINGS_NOTES;
    }

    static String teamStats(TeamStats stats, String scope) {
        TextTable table = new TextTable().left("")
                .right("PJ").right("G").right("E").right("P")
                .right("GF").right("GC").right("DG").right("Pts").right("GF/PJ").right("GC/PJ");
        addTotals(table, "Local", stats.home());
        addTotals(table, "Visitante", stats.away());
        addTotals(table, "Total", stats.total());

        String form = stats.lastFive().stream().map(Outcome::letter).collect(Collectors.joining(" "));
        return stats.team() + " - " + scope + " (todas las competiciones)\n\n"
                + table.render() + "\n"
                + line("Últimos 5 partidos", form.isEmpty() ? "-" : form + "  (el más reciente a la derecha)")
                + line("Racha actual", currentRun(stats))
                + line("Mayor racha ganadora", describe(stats.longestWinningRun()))
                + line("Mayor racha invicta", describe(stats.longestUnbeatenRun()))
                + line("Mayor racha perdedora", describe(stats.longestLosingRun()));
    }

    static String headToHead(String team, String opponent, List<Match> matches) {
        if (matches.isEmpty()) {
            return team + " y " + opponent + " no se enfrentaron en estos datos.\n";
        }
        TextTable totals = new TextTable().left("")
                .right("PJ").right("G").right("E").right("P").right("GF").right("GC");
        addRecord(totals, team + " de local", Totals.of(team, onlyAtHome(team, matches)));
        addRecord(totals, opponent + " de local", Totals.of(team, onlyAtHome(opponent, matches)));
        addRecord(totals, "Total", Totals.of(team, matches));

        List<Match> recent = matches.reversed().subList(0, Math.min(RECENT_MEETINGS, matches.size()));
        TextTable meetings = new TextTable().left("Fecha").left("Competición")
                .right("Local").left("Resultado").left("Visitante");
        for (Match m : recent) {
            meetings.row(date(m.date()), m.competition(), m.homeTeam(),
                    m.homeGoals() + "-" + m.awayGoals(), m.awayTeam());
        }

        return team + " vs " + opponent + " - " + count(matches.size(), "partido", "partidos")
                + ", del " + date(matches.getFirst().date()) + " al " + date(matches.getLast().date()) + "\n\n"
                + totals.render()
                + "G, E, P y goles se cuentan para " + team + ".\n\n"
                + "Cruces más recientes (" + recent.size() + "):\n"
                + meetings.render();
    }

    static String prediction(Prediction prediction) {
        TextTable table = new TextTable().left("Resultado").right("Probabilidad").left("");
        addChance(table, "Gana " + prediction.homeTeam(), prediction.homeWin());
        addChance(table, "Empate", prediction.draw());
        addChance(table, "Gana " + prediction.awayTeam(), prediction.awayWin());

        StringBuilder text = new StringBuilder()
                .append(prediction.homeTeam()).append(" (local) vs ").append(prediction.awayTeam())
                .append(" - modelo ").append(prediction.model()).append("\n\n")
                .append(table.render()).append('\n');
        int width = prediction.details().keySet().stream().mapToInt(String::length).max().orElse(0);
        prediction.details().forEach((label, value) -> text.append(line(label, value, width)));
        return text.toString();
    }

    static String backtest(BacktestReport report) {
        TextTable table = new TextTable().left("Modelo").right("Log loss").right("Brier").right("Acierto");
        for (BacktestReport.Score score : report.results()) {
            table.row(score.model(), decimals(score.logLoss()), decimals(score.brier()), percent(score.accuracy()));
        }
        BacktestReport.Outcomes actual = report.actual();
        return "Backtest: " + count(report.matches(), "partido", "partidos")
                + " del " + date(LocalDate.parse(report.evaluatedFrom()))
                + " al " + date(LocalDate.parse(report.evaluatedTo())) + "\n"
                + "Cada partido se predijo usando solo partidos de fechas anteriores\n"
                + "(los " + report.warmUpMatches() + " partidos previos sirvieron solo para calentar los modelos).\n\n"
                + table.render() + "\n"
                + BACKTEST_NOTES
                + "Resultados reales: victorias locales " + percent(actual.homeWin())
                + ", empates " + percent(actual.draw())
                + ", victorias visitantes " + percent(actual.awayWin()) + ".\n";
    }

    private static List<Match> onlyAtHome(String team, List<Match> matches) {
        return matches.stream().filter(m -> m.isHomeTeam(team)).toList();
    }

    private static void addRecord(TextTable table, String label, Totals t) {
        table.row(label, t.played(), t.won(), t.drawn(), t.lost(), t.goalsFor(), t.goalsAgainst());
    }

    private static void addChance(TextTable table, String label, double probability) {
        table.row(label, percent(probability), "#".repeat((int) Math.round(probability * BAR_WIDTH)));
    }

    private static void addTotals(TextTable table, String label, Totals t) {
        table.row(label, t.played(), t.won(), t.drawn(), t.lost(), t.goalsFor(), t.goalsAgainst(),
                signed(t.goalDifference()), t.points(),
                perMatch(t.goalsFor(), t.played()), perMatch(t.goalsAgainst(), t.played()));
    }

    private static String currentRun(TeamStats stats) {
        Streak run = stats.currentRun();
        if (run.length() == 0) {
            return "-";
        }
        String text = switch (stats.currentOutcome()) {
            case WIN -> count(run.length(), "victoria", "victorias");
            case DRAW -> count(run.length(), "empate", "empates");
            case LOSS -> count(run.length(), "derrota", "derrotas");
        };
        return text + " (desde el " + date(run.from()) + ")";
    }

    private static String describe(Streak streak) {
        return switch (streak.length()) {
            case 0 -> "-";
            case 1 -> "1 partido (" + date(streak.from()) + ")";
            default -> streak.length() + " partidos (del " + date(streak.from()) + " al " + date(streak.to()) + ")";
        };
    }

    private static String line(String label, String value) {
        return line(label, value, 22);
    }

    private static String line(String label, String value, int labelWidth) {
        return String.format("%-" + labelWidth + "s %s", label, value) + "\n";
    }

    private static String count(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }

    private static String signed(int n) {
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    private static String date(LocalDate date) {
        return date.format(DAY_MONTH_YEAR);
    }

    private static String percent(double share) {
        return String.format(ARGENTINA, "%.1f%%", 100 * share);
    }

    private static String decimals(double value) {
        return String.format(ARGENTINA, "%.3f", value);
    }

    private static String perMatch(int goals, int played) {
        return played == 0 ? "-" : String.format(ARGENTINA, "%.2f", (double) goals / played);
    }
}
