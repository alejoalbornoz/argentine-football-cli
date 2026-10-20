package ar.futbol.data;

import ar.futbol.model.Match;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** All SQL for the {@code matches} table lives here. */
public final class MatchRepository {

    private static final String INSERT = """
            INSERT INTO matches (season, competition, match_date, home_team, away_team, home_goals, away_goals)
            VALUES (?, ?, ?, ?, ?, ?, ?)""";

    private static final String SELECT_MATCHES = """
            SELECT season, competition, match_date, home_team, away_team, home_goals, away_goals
            FROM matches
            """;

    // Matches on the same date keep their CSV order (id), which the converter sorts by kickoff time.
    private static final String CHRONOLOGICAL = " ORDER BY match_date, id";

    private final Database database;

    public MatchRepository(Database database) {
        this.database = database;
    }

    /**
     * Replaces every stored match with {@code matches} in a single transaction: either the whole
     * new set is saved or nothing changes.
     */
    public void replaceAll(List<Match> matches) throws SQLException {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try (Statement delete = connection.createStatement();
                 PreparedStatement insert = connection.prepareStatement(INSERT)) {
                delete.executeUpdate("DELETE FROM matches");
                for (Match match : matches) {
                    insert.setString(1, match.season());
                    insert.setString(2, match.competition());
                    insert.setString(3, match.date().toString());
                    insert.setString(4, match.homeTeam());
                    insert.setString(5, match.awayTeam());
                    insert.setInt(6, match.homeGoals());
                    insert.setInt(7, match.awayGoals());
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** All matches in chronological order. */
    public List<Match> findAll() throws SQLException {
        return queryMatches(SELECT_MATCHES + CHRONOLOGICAL);
    }

    /** The matches of one competition in one season, in chronological order. */
    public List<Match> findBySeasonAndCompetition(String season, String competition) throws SQLException {
        return queryMatches(SELECT_MATCHES + "WHERE season = ? AND competition = ?" + CHRONOLOGICAL,
                season, competition);
    }

    /** Every match the team played, home or away, in chronological order. */
    public List<Match> findByTeam(String team) throws SQLException {
        return queryMatches(SELECT_MATCHES + "WHERE home_team = ? OR away_team = ?" + CHRONOLOGICAL,
                team, team);
    }

    /** Every match between the two teams, whoever was at home, in chronological order. */
    public List<Match> findHeadToHead(String team, String opponent) throws SQLException {
        return queryMatches(SELECT_MATCHES
                        + "WHERE (home_team = ? AND away_team = ?) OR (home_team = ? AND away_team = ?)"
                        + CHRONOLOGICAL,
                team, opponent, opponent, team);
    }

    /** Distinct seasons, sorted. */
    public List<String> findSeasons() throws SQLException {
        return queryStrings("SELECT DISTINCT season FROM matches ORDER BY season");
    }

    /** Competitions played in a season, sorted. */
    public List<String> findCompetitions(String season) throws SQLException {
        return queryStrings(
                "SELECT DISTINCT competition FROM matches WHERE season = ? ORDER BY competition", season);
    }

    /** Every team that appears as home or away, sorted by name. */
    public List<String> findTeams() throws SQLException {
        return queryStrings("""
                SELECT home_team AS team FROM matches
                UNION
                SELECT away_team FROM matches
                ORDER BY team""");
    }

    private List<Match> queryMatches(String sql, String... parameters) throws SQLException {
        List<Match> matches = new ArrayList<>();
        try (Connection connection = database.connect();
             PreparedStatement statement = prepare(connection, sql, parameters);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                matches.add(new Match(
                        rows.getString("season"),
                        rows.getString("competition"),
                        LocalDate.parse(rows.getString("match_date")),
                        rows.getString("home_team"),
                        rows.getString("away_team"),
                        rows.getInt("home_goals"),
                        rows.getInt("away_goals")));
            }
        }
        return matches;
    }

    private List<String> queryStrings(String sql, String... parameters) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = database.connect();
             PreparedStatement statement = prepare(connection, sql, parameters);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }

    private static PreparedStatement prepare(Connection connection, String sql, String... parameters)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < parameters.length; i++) {
            statement.setString(i + 1, parameters[i]);
        }
        return statement;
    }
}
