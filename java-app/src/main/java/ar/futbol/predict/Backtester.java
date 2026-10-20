package ar.futbol.predict;

/** Evaluates the models on past matches. Like {@link Predictor}, an interface so tests can fake it. */
public interface Backtester {

    BacktestReport run() throws PredictionException;
}
