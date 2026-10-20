package ar.futbol;

import ar.futbol.cli.ConsoleInput;
import ar.futbol.cli.Menu;
import ar.futbol.data.CsvMatchReader;
import ar.futbol.data.Database;
import ar.futbol.data.MatchRepository;
import ar.futbol.model.Match;
import ar.futbol.predict.Backtester;
import ar.futbol.predict.Predictor;
import ar.futbol.predict.PythonBacktester;
import ar.futbol.predict.PythonPredictor;
import ar.futbol.predict.PythonRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

/**
 * Entry point. Usage: {@code java -jar futbol-cli.jar [matches.csv] [database.db]}
 *
 * <p>The CSV is the source of truth: every start reloads it into SQLite, so the database never
 * drifts from the file. Run it from the project root: the default paths ({@code data/},
 * {@code python/}, {@code .venv/}) are relative to the current directory.
 */
public final class Main {

    private static final Path DEFAULT_CSV = Path.of("data", "matches.csv");
    private static final Path DEFAULT_DB = Path.of("data", "futbol.db");
    private static final Path PREDICT_SCRIPT = Path.of("python", "predict.py");
    private static final Path BACKTEST_SCRIPT = Path.of("python", "backtest.py");
    private static final int MAX_ERRORS_SHOWN = 10;

    public static void main(String[] args) {
        Path csv = args.length > 0 ? Path.of(args[0]) : DEFAULT_CSV;
        Path db = args.length > 1 ? Path.of(args[1]) : DEFAULT_DB;

        if (!Files.isRegularFile(csv)) {
            fail("CSV file not found: " + csv.toAbsolutePath()
                    + System.lineSeparator() + "See data/README.md for the expected format.");
        }

        try {
            CsvMatchReader.Result result = new CsvMatchReader().read(csv);
            printErrors(result.errors());
            if (result.matches().isEmpty()) {
                fail("No valid matches in " + csv + "; the database was not changed.");
            }

            MatchRepository repository = new MatchRepository(Database.open(db));
            repository.replaceAll(result.matches());
            printSummary(repository);

            PythonRunner python = new PythonRunner(PythonRunner.findPython(Path.of("")));
            Predictor predictor = new PythonPredictor(python, PREDICT_SCRIPT, db);
            Backtester backtester = new PythonBacktester(python, BACKTEST_SCRIPT, db);
            new Menu(repository, predictor, backtester, new ConsoleInput(System.in, System.out), System.out)
                    .run();
        } catch (IOException e) {
            fail("Could not read data: " + e.getMessage());
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    private static void printErrors(List<String> errors) {
        if (errors.isEmpty()) {
            return;
        }
        System.out.println("Skipped " + errors.size() + " invalid row(s):");
        errors.stream().limit(MAX_ERRORS_SHOWN).forEach(e -> System.out.println("  " + e));
        if (errors.size() > MAX_ERRORS_SHOWN) {
            System.out.println("  ... and " + (errors.size() - MAX_ERRORS_SHOWN) + " more");
        }
        System.out.println();
    }

    private static void printSummary(MatchRepository repository) throws SQLException {
        List<Match> stored = repository.findAll(); // chronological, unlike the CSV
        System.out.printf("Loaded %d matches of %d teams, %s to %s.%n", stored.size(),
                repository.findTeams().size(), stored.getFirst().date(), stored.getLast().date());
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
