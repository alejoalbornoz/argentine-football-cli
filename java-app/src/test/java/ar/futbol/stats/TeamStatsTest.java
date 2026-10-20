package ar.futbol.stats;

import ar.futbol.model.Match;
import ar.futbol.model.Outcome;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static ar.futbol.model.Outcome.DRAW;
import static ar.futbol.model.Outcome.LOSS;
import static ar.futbol.model.Outcome.WIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TeamStatsTest {

    // From A's point of view: W W D L (other match) W W W
    private static final List<Match> SEASON = List.of(
            match(1, "A", "B", 2, 0),   // W home
            match(2, "C", "A", 0, 1),   // W away
            match(3, "A", "C", 1, 1),   // D home
            match(4, "B", "A", 2, 0),   // L away
            match(5, "B", "C", 1, 0),   // A not playing: ignored
            match(6, "A", "B", 1, 0),   // W home
            match(7, "C", "A", 1, 3),   // W away
            match(8, "A", "C", 2, 1));  // W home

    private final TeamStats stats = TeamStats.of("A", SEASON);

    @Test
    void splitsHomeAndAway() {
        assertEquals(new Totals(4, 3, 1, 0, 6, 2), stats.home());
        assertEquals(new Totals(3, 2, 0, 1, 4, 3), stats.away());
        assertEquals(new Totals(7, 5, 1, 1, 10, 5), stats.total());
    }

    @Test
    void lastFiveAreOldestFirst() {
        assertEquals(List.<Outcome>of(DRAW, LOSS, WIN, WIN, WIN), stats.lastFive());
    }

    @Test
    void currentRunCountsBackFromTheLatestMatch() {
        assertEquals(WIN, stats.currentOutcome());
        assertEquals(new Streak(3, day(6), day(8)), stats.currentRun());
    }

    @Test
    void longestRunsKeepTheEarliestOnATie() {
        assertEquals(new Streak(3, day(6), day(8)), stats.longestWinningRun());
        assertEquals(new Streak(3, day(1), day(3)), stats.longestUnbeatenRun()); // ties with days 6-8
        assertEquals(new Streak(1, day(4), day(4)), stats.longestLosingRun());
    }

    @Test
    void noMatchesGivesEmptyStats() {
        TeamStats empty = TeamStats.of("A", List.of());

        assertEquals(Totals.NONE, empty.total());
        assertEquals(List.of(), empty.lastFive());
        assertNull(empty.currentOutcome());
        assertEquals(Streak.NONE, empty.currentRun());
        assertEquals(Streak.NONE, empty.longestWinningRun());
    }

    private static Match match(int day, String home, String away, int homeGoals, int awayGoals) {
        return new Match("2024", "League", day(day), home, away, homeGoals, awayGoals);
    }

    private static LocalDate day(int day) {
        return LocalDate.of(2024, 3, day);
    }
}
