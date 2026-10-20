package ar.futbol.predict;

/** Predicts a match. The menu only knows this interface, so tests can use a fake instead of Python. */
public interface Predictor {

    Prediction predict(Model model, String homeTeam, String awayTeam) throws PredictionException;
}
