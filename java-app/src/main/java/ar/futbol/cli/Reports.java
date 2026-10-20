package ar.futbol.cli;

import ar.futbol.model.Match;
import ar.futbol.model.Outcome;
import ar.futbol.predict.BacktestReport;
import ar.futbol.predict.Prediction;
import ar.futbol.stats.Standings;
import ar.futbol.stats.Streak;
import ar.futbol.stats.TeamStats;
import ar.futbol.stats.Totals;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Turns computed stats into the text shown in the terminal. */
final class Reports {

    private static final String STANDINGS_NOTES = """
            3 points per win, 1 per draw. Ties: goal difference, then goals scored.
            Counts every match of the competition that season, so stages (Apertura/Clausura,
            groups, playoffs) are combined into one table.
            """;
    private static final String BACKTEST_NOTES = """
            Log loss and Brier score: lower is better. Accuracy: higher is better.
            Guessing 1/3 for every outcome would score log loss 1.099 and Brier 0.667.
            The baseline always picks a home win; its probabilities are the outcome rates seen so far.
            """;
    private static final int RECENT_MEETINGS = 10;
    private static final int BAR_WIDTH = 40; // a 100% chance is 40 '#' characters

    private Reports() {}

    static String standings(String title, List<Standings.Row> rows, int matchCount) {
        TextTable table = new TextTable().right("#").left("Team")
                .right("P").right("W").right("D").right("L")
                .right("GF").right("GA").right("GD").right("Pts");
        for (int i = 0; i < rows.size(); i++) {
            Totals t = rows.get(i).totals();
            table.row(i + 1, rows.get(i).team(), t.played(), t.won(), t.drawn(), t.lost(),
                    t.goalsFor(), t.goalsAgainst(), signed(t.goalDifference()), t.points());
        }
        return title + " - " + count(matchCount, "match", "matches") + "\n\n"
                + table.render() + "\n" + STANDINGS_NOTES;
    }

    static String teamStats(TeamStats stats, String scope) {
        TextTable table = new TextTable().left("")
                .right("P").right("W").right("D").right("L")
                .right("GF").right("GA").right("GD").right("Pts").right("GF/P").right("GA/P");
        addTotals(table, "Home", stats.home());
        addTotals(table, "Away", stats.away());
        addTotals(table, "Total", stats.total());

        String form = stats.lastFive().stream().map(Outcome::letter).collect(Collectors.joining(" "));
        return stats.team() + " - " + scope + " (all competitions)\n\n"
                + table.render() + "\n"
                + line("Last 5 (oldest first)", form.isEmpty() ? "-" : form)
                + line("Current run", currentRun(stats))
                + line("Longest winning run", describe(stats.longestWinningRun()))
                + line("Longest unbeaten run", describe(stats.longestUnbeatenRun()))
                + line("Longest losing run", describe(stats.longestLosingRun()));
    }

    static String headToHead(String team, String opponent, List<Match> matches) {
        if (matches.isEmpty()) {
            return team + " and " + opponent + " have not played each other in this data.\n";
        }
        TextTable totals = new TextTable().left("")
                .right("P").right("W").right("D").right("L").right("GF").right("GA");
        addRecord(totals, team + " at home", Totals.of(team, onlyAtHome(team, matches)));
        addRecord(totals, opponent + " at home", Totals.of(team, onlyAtHome(opponent, matches)));
        addRecord(totals, "Total", Totals.of(team, matches));

        List<Match> recent = matches.reversed().subList(0, Math.min(RECENT_MEETINGS, matches.size()));
        TextTable meetings = new TextTable().left("Date").left("Competition")
                .right("Home").left("Score").left("Away");
        for (Match m : recent) {
            meetings.row(m.date(), m.competition(), m.homeTeam(), m.homeGoals() + "-" + m.awayGoals(), m.awayTeam());
        }

        return team + " vs " + opponent + " - " + count(matches.size(), "match", "matches") + ", "
                + matches.getFirst().date() + " to " + matches.getLast().date() + "\n\n"
                + totals.render()
                + "W, D, L and goals are counted for " + team + ".\n\n"
                + "Last " + count(recent.size(), "meeting", "meetings") + " (most recent first):\n"
                + meetings.render();
    }

    static String prediction(Prediction prediction) {
        TextTable table = new TextTable().left("Outcome").right("Probability").left("");
        addChance(table, prediction.homeTeam() + " win", prediction.homeWin());
        addChance(table, "Draw", prediction.draw());
        addChance(table, prediction.awayTeam() + " win", prediction.awayWin());

        StringBuilder text = new StringBuilder()
                .append(prediction.homeTeam()).append(" (home) vs ").append(prediction.awayTeam())
                .append(" - ").append(prediction.model()).append(" model\n\n")
                .append(table.render()).append('\n');
        int width = prediction.details().keySet().stream().mapToInt(String::length).max().orElse(0);
        prediction.details().forEach((label, value) -> text.append(line(label, value, width)));
        return text.toString();
    }

    static String backtest(BacktestReport report) {
        TextTable table = new TextTable().left("Model").right("Log loss").right("Brier").right("Accuracy");
        for (BacktestReport.Score score : report.results()) {
            table.row(score.model(), decimals(score.logLoss()), decimals(score.brier()), percent(score.accuracy()));
        }
        BacktestReport.Outcomes actual = report.actual();
        return "Backtest: " + count(report.matches(), "match", "matches") + " from "
                + report.evaluatedFrom() + " to " + report.evaluatedTo() + "\n"
                + "Each was predicted only from matches of earlier dates (plus "
                + report.warmUpMatches() + " older matches to warm up).\n\n"
                + table.render() + "\n"
                + BACKTEST_NOTES
                + "What actually happened: home wins " + percent(actual.homeWin())
                + ", draws " + percent(actual.draw()) + ", away wins " + percent(actual.awayWin()) + ".\n";
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

    private static String percent(double share) {
        return String.format(Locale.ROOT, "%.1f%%", 100 * share);
    }

    private static String decimals(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
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
            case WIN -> count(run.length(), "win", "wins");
            case DRAW -> count(run.length(), "draw", "draws");
            case LOSS -> count(run.length(), "loss", "losses");
        };
        return text + " (since " + run.from() + ")";
    }

    private static String describe(Streak streak) {
        return switch (streak.length()) {
            case 0 -> "-";
            case 1 -> "1 match (" + streak.from() + ")";
            default -> streak.length() + " matches (" + streak.from() + " to " + streak.to() + ")";
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

    private static String perMatch(int goals, int played) {
        // Locale.ROOT: always "1.50", never "1,50" on a Spanish-language Windows.
        return played == 0 ? "-" : String.format(Locale.ROOT, "%.2f", (double) goals / played);
    }
}
