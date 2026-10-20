package ar.futbol.stats;

import ar.futbol.model.Match;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** League table: 3 points per win, 1 per draw. */
public final class Standings {

    public record Row(String team, Totals totals) {}

    /** Points, then goal difference, then goals scored (all highest first), then team name. */
    private static final Comparator<Row> ORDER = Comparator
            .comparingInt((Row row) -> row.totals().points())
            .thenComparingInt(row -> row.totals().goalDifference())
            .thenComparingInt(row -> row.totals().goalsFor())
            .reversed()
            .thenComparing(Row::team);

    private Standings() {}

    public static List<Row> table(List<Match> matches) {
        Map<String, Totals> totalsByTeam = new HashMap<>();
        for (Match match : matches) {
            for (String team : List.of(match.homeTeam(), match.awayTeam())) {
                totalsByTeam.compute(team, (key, totals) -> (totals == null ? Totals.NONE : totals).plus(match, key));
            }
        }
        return totalsByTeam.entrySet().stream()
                .map(entry -> new Row(entry.getKey(), entry.getValue()))
                .sorted(ORDER)
                .toList();
    }
}
