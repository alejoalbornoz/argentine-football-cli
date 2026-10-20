package ar.futbol.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** The SQLite file that holds the matches. Hands out short-lived JDBC connections. */
public final class Database {

    private static final String CREATE_MATCHES = """
            CREATE TABLE IF NOT EXISTS matches (
                id          INTEGER PRIMARY KEY,
                season      TEXT    NOT NULL,
                competition TEXT    NOT NULL,
                match_date  TEXT    NOT NULL,  -- ISO yyyy-MM-dd, so text order = date order
                home_team   TEXT    NOT NULL,
                away_team   TEXT    NOT NULL,
                home_goals  INTEGER NOT NULL CHECK (home_goals >= 0),
                away_goals  INTEGER NOT NULL CHECK (away_goals >= 0),
                UNIQUE (match_date, home_team, away_team)
            )""";

    private static final String CREATE_DATE_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_matches_date ON matches (match_date)";

    private final String url;

    private Database(Path file) {
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
    }

    /** Opens the database file (creating it and its folder if needed) and makes sure the schema exists. */
    public static Database open(Path file) throws IOException, SQLException {
        Path folder = file.toAbsolutePath().getParent();
        if (folder != null) {
            Files.createDirectories(folder);
        }
        Database database = new Database(file);
        database.createSchema();
        return database;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void createSchema() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_MATCHES);
            statement.executeUpdate(CREATE_DATE_INDEX);
        }
    }
}
