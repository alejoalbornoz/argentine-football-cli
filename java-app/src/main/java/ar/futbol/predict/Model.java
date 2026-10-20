package ar.futbol.predict;

/** The prediction models in {@code python/predict.py}. */
public enum Model {
    ELO("elo", "Elo", "team ratings that move after every result"),
    POISSON("poisson", "Poisson", "expected goals from attack and defence strength");

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

    /** One line for the menu, e.g. "Elo - team ratings that move after every result". */
    public String menuText() {
        return label + " - " + description;
    }
}
