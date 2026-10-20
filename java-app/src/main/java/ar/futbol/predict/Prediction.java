package ar.futbol.predict;

import java.util.Map;

/**
 * A model's probabilities for one match, as printed by {@code python/predict.py}.
 *
 * @param model   display name of the model, e.g. "Elo"
 * @param details model-specific facts for the user (label to value), in display order
 */
public record Prediction(
        String model,
        String homeTeam,
        String awayTeam,
        double homeWin,
        double draw,
        double awayWin,
        Map<String, String> details) {

    public Prediction {
        details = details == null ? Map.of() : details;
    }
}
