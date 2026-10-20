package ar.futbol.predict;

/** The prediction models in {@code python/predict.py}. */
public enum Model {
    ELO("elo", "Elo", "ratings que se ajustan después de cada resultado"),
    POISSON("poisson", "Poisson", "goles esperados según ataque y defensa");

    private final String id;
    private final String label;
    private final String description;

    Model(String id, String label, String description) {
        this.id = id;
        this.label = label;
        this.description = description;
    }

    /** The value of the script's {@code --model} option. */
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** One line for the menu, e.g. "Elo - ratings que se ajustan después de cada resultado". */
    public String menuText() {
        return label + " - " + description;
    }
}
