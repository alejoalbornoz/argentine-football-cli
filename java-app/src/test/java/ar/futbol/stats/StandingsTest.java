package ar.futbol.stats;

import ar.futbol.model.Match;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandingsTest {

    @Test
    void sortsByPointsThenGoalDifferenceThenGoalsScoredThenName() {
        List<Standings.Row> table = Standings.table(List.of(
                match("A", "B", 1, 0),   // A and C: 3 pts, +1; C scored more
                match("C", "D", 3, 2),   // D and B: 0 pts, -1; D scored more
                match("E", "F", 0, 0))); // E and F: identical, so by name

        assertEquals(List.of("C", "A", "E", "F", "D", "B"),
                table.stream().map(Standings.Row::team).toList());
    }

    @Test
    void addsUpEachTeamsHomeAndAwayMatches() {
        List<Standings.Row> table = Standings.table(List.of(
                match("A", "B", 2, 0),
                match("B", "A", 1, 1),
                match("A", "C", 0, 1)));

        Totals a = table.stream().filter(row -> row.team().equals("A")).findFirst().orElseThrow().totals();
        assertEquals(new Totals(3, 1, 1, 1, 3, 2), a);
        assertEquals(4, a.points());
        assertEquals(1, a.goalDifference());
    }

    @Test
    void noMatchesMeansAnEmptyTable() {
        assertEquals(List.of(), Standings.table(List.of()));
    }

    private static Match match(String home, String away, int homeGoals, int awayGoals) {
        return new Match("2024", "League", LocalDate.of(2024, 3, 1), home, away, homeGoals, awayGoals);
    }
}
