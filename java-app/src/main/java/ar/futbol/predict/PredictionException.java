package ar.futbol.predict;

/** A model could not be run (a prediction or a backtest). The message is meant to be shown to the user. */
public final class PredictionException extends Exception {

    public PredictionException(String message) {
        super(message);
    }
}
