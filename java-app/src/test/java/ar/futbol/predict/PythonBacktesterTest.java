package ar.futbol.predict;

import ar.futbol.data.Database;
import ar.futbol.data.MatchRepository;
import ar.futbol.model.Match;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static ar.futbol.predict.PythonRunnerTest.PROJECT_ROOT;
import static ar.futbol.predict.PythonRunnerTest.projectPython;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PythonBacktesterTest {

    private static final String JSON = """
            {"evaluated_from": "2023-01-27", "evaluated_to": "2026-10-20", "matches": 2122,
             "warm_up_matches": 4308, "actual": {"home_win": 0.43, "draw": 0.32, "away_win": 0.25},
             "results": [{"model": "Elo", "log_loss": 1.067, "brier": 0.645, "accuracy": 0.422}]}""";

    @Test
    void parsesTheJsonPrintedByBacktestPy() throws PredictionException {
        BacktestReport report = PythonBacktester.parse(JSON);

        assertEquals(new BacktestReport("2023-01-27", "2026-10-20", 2122, 4308,
                new BacktestReport.Outcomes(0.43, 0.32, 0.25),
                List.of(new BacktestReport.Score("Elo", 1.067, 0.645, 0.422))), report);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "[]", "{}", "{\"actual\": {}, \"results\": []}", "{\"actual\": {}, \"results\": [{}]}"})
    void rejectsBrokenAnswers(String json) {
        assertThrows(PredictionException.class, () -> PythonBacktester.parse(json));
    }

    /** Runs the real python/backtest.py on a tiny database. Skipped without the project's .venv. */
    @Test
    void realScriptScoresTheModelsAgainstTheBaseline(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("test.db");
        new MatchRepository(Database.open(db)).replaceAll(List.of(
                match("2022-03-01", "Alpha", "Beta", 2, 0),   // warm-up (the default start is 2023-01-01)
                match("2022-03-08", "Beta", "Gamma", 1, 1),
                match("2023-03-01", "Gamma", "Alpha", 0, 1),  // evaluated
                match("2023-03-08", "Alpha", "Beta", 1, 1)));
        var backtester = new PythonBacktester(
                new PythonRunner(projectPython()), PROJECT_ROOT.resolve("python/backtest.py"), db);

        BacktestReport report = backtester.run();

        assertEquals(2, report.matches());
        assertEquals(2, report.warmUpMatches());
        assertEquals(List.of("Elo", "Poisson", "Base: siempre gana el local"),
                report.results().stream().map(BacktestReport.Score::model).toList());
        assertEquals(0.0, report.results().get(2).accuracy(), "neither evaluated match was a home win");
    }

    private static Match match(String date, String home, String away, int homeGoals, int awayGoals) {
        return new Match("2023", "League", LocalDate.parse(date), home, away, homeGoals, awayGoals);
    }
}
