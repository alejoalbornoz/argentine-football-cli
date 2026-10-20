package ar.futbol.predict;

import com.google.gson.JsonParseException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.DoubleStream;

/**
 * Predicts with {@code python/predict.py}. Python reads the matches straight from the SQLite
 * file, so only the database path, the model and the two team names cross the process boundary.
 */
public final class PythonPredictor implements Predictor {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final double TOLERANCE = 1e-6;

    private final PythonRunner runner;
    private final Path script;
    private final Path database;

    public PythonPredictor(PythonRunner runner, Path script, Path database) {
        this.runner = runner;
        this.script = script;
        this.database = database;
    }

    @Override
    public Prediction predict(Model model, String homeTeam, String awayTeam) throws PredictionException {
        return parse(runner.run(script, TIMEOUT, "--db", database.toAbsolutePath().toString(),
                "--home", homeTeam, "--away", awayTeam, "--model", model.id()));
    }

    /** Turns the script's JSON into a {@link Prediction}, rejecting anything inconsistent. */
    static Prediction parse(String json) throws PredictionException {
        Prediction prediction;
        try {
            prediction = PythonRunner.GSON.fromJson(json, Prediction.class);
        } catch (JsonParseException e) {
            throw new PredictionException("no se pudo leer la respuesta de Python: " + e.getMessage());
        }
        if (prediction == null || prediction.model() == null
                || prediction.homeTeam() == null || prediction.awayTeam() == null) {
            throw new PredictionException("a la respuesta de Python le faltan datos: " + json.strip());
        }

        double[] chances = {prediction.homeWin(), prediction.draw(), prediction.awayWin()};
        boolean eachValid = DoubleStream.of(chances).allMatch(p -> p >= 0 && p <= 1);
        if (!eachValid || Math.abs(DoubleStream.of(chances).sum() - 1) > TOLERANCE) {
            throw new PredictionException("las probabilidades de Python no suman 1: " + json.strip());
        }
        return prediction;
    }
}
