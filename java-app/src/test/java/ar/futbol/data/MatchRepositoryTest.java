package ar.futbol.data;

import ar.futbol.model.Match;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchRepositoryTest {

    private static final Match FEB_10 = match("2024", "League", "2024-02-10", "Alpha", "Beta", 2, 1);
    private static final Match FEB_17 = match("2024", "Cup", "2024-02-17", "Gamma", "Alpha", 0, 0);
    private static final Match AUG_03 = match("2024/2025", "League", "2024-08-03", "Beta", "Delta", 1, 3);

    private MatchRepository repository;

    @BeforeEach
    void setUp(@TempDir Path dir) throws IOException, SQLException {
        // A real SQLite file per test; the folder is created by Database.open.
        repository = new MatchRepository(Database.open(dir.resolve("nested/test.db")));
    }

    @Test
    void emptyDatabaseHasNoData() throws SQLException {
        assertTrue(repository.findAll().isEmpty());
        assertTrue(repository.findTeams().isEmpty());
        assertTrue(repository.findSeasons().isEmpty());
    }

    @Test
    void findAllReturnsMatchesInChronologicalOrder() throws SQLException {
        repository.replaceAll(List.of(AUG_03, FEB_17, FEB_10));

        assertEquals(List.of(FEB_10, FEB_17, AUG_03), repository.findAll());
    }

    @Test
    void replaceAllDiscardsPreviousMatches() throws SQLException {
        repository.replaceAll(List.of(FEB_10, FEB_17));
        repository.replaceAll(List.of(AUG_03));

        assertEquals(List.of(AUG_03), repository.findAll());
    }

    @Test
    void failedReplaceLeavesPreviousDataUntouched() throws SQLException {
        repository.replaceAll(List.of(FEB_10));

        // The same match twice breaks the UNIQUE constraint halfway through the batch.
        assertThrows(SQLException.class, () -> repository.replaceAll(List.of(AUG_03, AUG_03)));

        assertEquals(List.of(FEB_10), repository.findAll());
    }

    @Test
    void teamsAndSeasonsAreDistinctAndSorted() throws SQLException {
        repository.replaceAll(List.of(AUG_03, FEB_10, FEB_17));

        assertEquals(List.of("Alpha", "Beta", "Delta", "Gamma"), repository.findTeams());
        assertEquals(List.of("2024", "2024/2025"), repository.findSeasons());
    }

    @Test
    void findsTheMatchesOfOneCompetitionInOneSeason() throws SQLException {
        repository.replaceAll(List.of(FEB_10, FEB_17, AUG_03));

        assertEquals(List.of(FEB_10), repository.findBySeasonAndCompetition("2024", "League"));
        assertEquals(List.of("Cup", "League"), repository.findCompetitions("2024"));
        assertEquals(List.of("League"), repository.findCompetitions("2024/2025"));
    }

    @Test
    void findsATeamsHomeAndAwayMatchesInChronologicalOrder() throws SQLException {
        repository.replaceAll(List.of(AUG_03, FEB_17, FEB_10));

        assertEquals(List.of(FEB_10, FEB_17), repository.findByTeam("Alpha"));
        assertEquals(List.of(AUG_03), repository.findByTeam("Delta"));
        assertTrue(repository.findByTeam("Nobody").isEmpty());
    }

    @Test
    void headToHeadFindsMatchesWhoeverWasAtHome() throws SQLException {
        Match rematch = match("2024", "League", "2024-09-01", "Beta", "Alpha", 1, 1);
        repository.replaceAll(List.of(FEB_10, FEB_17, AUG_03, rematch));

        assertEquals(List.of(FEB_10, rematch), repository.findHeadToHead("Alpha", "Beta"));
        assertEquals(List.of(FEB_10, rematch), repository.findHeadToHead("Beta", "Alpha"));
        assertTrue(repository.findHeadToHead("Alpha", "Delta").isEmpty());
    }

    private static Match match(String season, String competition, String date,
                               String home, String away, int hg, int ag) {
        return new Match(season, competition, LocalDate.parse(date), home, away, hg, ag);
    }
}
