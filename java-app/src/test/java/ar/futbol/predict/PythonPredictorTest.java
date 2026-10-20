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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonPredictorTest {

    private static final String JSON = """
            {"model": "Elo", "home_team": "Alpha", "away_team": "Beta",
             "home_win": 0.5, "draw": 0.3, "away_win": 0.2,
             "details": {"Alpha rating": "1550", "Beta rating": "1450", "K factor": "20"}}""";

    @Test
    void parsesTheJsonPrintedByPredictPy() throws PredictionException {
        Prediction prediction = PythonPredictor.parse(JSON);

        assertEquals(new Prediction("Elo", "Alpha", "Beta", 0.5, 0.3, 0.2, prediction.details()), prediction);
        assertEquals(List.of("Alpha rating", "Beta rating", "K factor"),
                List.copyOf(prediction.details().keySet()), "details keep the script's order");
        assertEquals("1550", prediction.details().get("Alpha rating"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "not json",
            "{}",
            "{\"model\": \"Elo\", \"home_team\": \"A\", \"away_team\": \"B\", \"home_win\": 0.5, \"draw\": 0.5, \"away_win\": 0.5}",
            "{\"model\": \"Elo\", \"home_team\": \"A\", \"away_team\": \"B\", \"home_win\": 1.2, \"draw\": 0.0, \"away_win\": -0.2}"})
    void rejectsBrokenOrInconsistentAnswers(String json) {
        assertThrows(PredictionException.class, () -> PythonPredictor.parse(json));
    }

    /** Runs the real python/predict.py with both models. Skipped without the project's .venv. */
    @Test
    void realScriptPredictsFromTheDatabaseAndReportsItsErrors(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("test.db");
        new MatchRepository(Database.open(db)).replaceAll(List.of(
                match("2024-02-10", "Alpha", "Beta", 2, 0),
                match("2024-02-17", "Beta", "Gamma", 1, 1),
                match("2024-02-24", "Gamma", "Alpha", 0, 3),
                match("2024-03-02", "Alpha", "Gamma", 1, 0)));
        var predictor = new PythonPredictor(
                new PythonRunner(projectPython()), PROJECT_ROOT.resolve("python/predict.py"), db);

        Prediction elo = predictor.predict(Model.ELO, "Alpha", "Gamma");
        Prediction poisson = predictor.predict(Model.POISSON, "Alpha", "Gamma");

        assertEquals("Elo", elo.model());
        assertEquals("4", elo.details().get("Matches used"));
        assertEquals("Poisson", poisson.model());
        assertTrue(poisson.details().containsKey("Expected goals"));
        for (Prediction prediction : List.of(elo, poisson)) {
            assertEquals(1.0, prediction.homeWin() + prediction.draw() + prediction.awayWin(), 1e-9);
            assertTrue(prediction.homeWin() > prediction.awayWin(), prediction.model() + ": Alpha won every match");
        }

        var error = assertThrows(PredictionException.class,
                () -> predictor.predict(Model.ELO, "Alpha", "Nobody"));
        assertEquals("unknown team: Nobody", error.getMessage());
    }

    private static Match match(String date, String home, String away, int homeGoals, int awayGoals) {
        return new Match("2024", "League", LocalDate.parse(date), home, away, homeGoals, awayGoals);
    }
}
