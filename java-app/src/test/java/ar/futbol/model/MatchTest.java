package ar.futbol.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchTest {

    private static final LocalDate DATE = LocalDate.of(2024, 2, 10);

    @Test
    void goalsAndOutcomeDependOnWhichSideYouAsk() {
        Match match = new Match("2024", "League", DATE, "Alpha", "Beta", 3, 1);

        assertTrue(match.isHomeTeam("Alpha"));
        assertEquals(3, match.goalsFor("Alpha"));
        assertEquals(1, match.goalsAgainst("Alpha"));
        assertEquals(Outcome.WIN, match.outcomeFor("Alpha"));

        assertFalse(match.isHomeTeam("Beta"));
        assertEquals(1, match.goalsFor("Beta"));
        assertEquals(3, match.goalsAgainst("Beta"));
        assertEquals(Outcome.LOSS, match.outcomeFor("Beta"));
    }

    @Test
    void aDrawIsADrawForBothTeams() {
        Match match = new Match("2024", "League", DATE, "Alpha", "Beta", 1, 1);

        assertEquals(Outcome.DRAW, match.outcomeFor("Alpha"));
        assertEquals(Outcome.DRAW, match.outcomeFor("Beta"));
    }

    @Test
    void askingAboutATeamThatDidNotPlayFails() {
        Match match = new Match("2024", "League", DATE, "Alpha", "Beta", 1, 0);

        assertFalse(match.involves("Gamma"));
        assertThrows(IllegalArgumentException.class, () -> match.outcomeFor("Gamma"));
    }
}
