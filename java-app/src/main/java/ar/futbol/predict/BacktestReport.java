package ar.futbol.predict;

import java.util.List;

/**
 * How well each model would have predicted past matches, as printed by {@code python/backtest.py}.
 *
 * @param warmUpMatches earlier matches the models learned from before the evaluation started
 * @param actual        how often each outcome really happened in the evaluated matches
 */
public record BacktestReport(
        String evaluatedFrom,
        String evaluatedTo,
        int matches,
        int warmUpMatches,
        Outcomes actual,
        List<Score> results) {

    /** Shares of home wins, draws and away wins. */
    public record Outcomes(double homeWin, double draw, double awayWin) {}

    public record Score(String model, double logLoss, double brier, double accuracy) {}
}
