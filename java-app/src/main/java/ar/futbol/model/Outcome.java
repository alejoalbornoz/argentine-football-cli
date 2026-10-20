package ar.futbol.model;

/** A match result from one team's point of view. */
public enum Outcome {
    WIN("W"),
    DRAW("D"),
    LOSS("L");

    private final String letter;

    Outcome(String letter) {
        this.letter = letter;
    }

    public String letter() {
        return letter;
    }
}
