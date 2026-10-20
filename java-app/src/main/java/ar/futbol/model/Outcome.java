package ar.futbol.model;

/** A match result from one team's point of view. The letters are the Spanish G/E/P shown to the user. */
public enum Outcome {
    WIN("G"),
    DRAW("E"),
    LOSS("P");

    private final String letter;

    Outcome(String letter) {
        this.letter = letter;
    }

    public String letter() {
        return letter;
    }
}
