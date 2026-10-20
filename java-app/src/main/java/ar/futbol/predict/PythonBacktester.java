package ar.futbol.predict;

import com.google.gson.JsonParseException;

import java.nio.file.Path;
import java.time.Duration;

/** Runs {@code python/backtest.py}, which replays the past seasons date by date. */
public final class PythonBacktester implements Backtester {

    private static final Duration TIMEOUT = Duration.ofMinutes(5); // takes ~15 s on the full data

    private final PythonRunner runner;
    private final Path script;
    private final Path database;

    public PythonBacktester(PythonRunner runner, Path script, Path database) {
        this.runner = runner;
        this.script = script;
        this.database = database;
    }

    @Override
    public BacktestReport run() throws PredictionException {
        return parse(runner.run(script, TIMEOUT, "--db", database.toAbsolutePath().toString()));
    }

    static BacktestReport parse(String json) throws PredictionException {
        BacktestReport report;
        try {
            report = PythonRunner.GSON.fromJson(json, BacktestReport.class);
        } catch (JsonParseException e) {
            throw new PredictionException("could not read the backtest's answer: " + e.getMessage());
        }
        if (report == null || report.actual() == null || report.results() == null || report.results().isEmpty()
                || report.results().stream().anyMatch(score -> score == null || score.model() == null)) {
            throw new PredictionException("the backtest's answer is missing fields: " + json.strip());
        }
        return report;
    }
}
