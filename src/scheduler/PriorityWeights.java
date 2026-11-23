package scheduler;

/**
 * Configurable weights W1..W4 of the priority formula.
 * Normalised to sum to 1.0 so that the final score is always on a [0, 100] scale.
 */
public record PriorityWeights(double stock, double consumption, double criticality, double expiry) {

    /** Default SmartMed weights: 40% stock urgency, 25% consumption, 20% criticality, 15% expiry risk. */
    public static final PriorityWeights DEFAULT = new PriorityWeights(0.40, 0.25, 0.20, 0.15);

    public PriorityWeights {
        if (stock < 0 || consumption < 0 || criticality < 0 || expiry < 0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }
        if (stock + consumption + criticality + expiry <= 0) {
            throw new IllegalArgumentException("At least one weight must be positive");
        }
    }

    public double sum() {
        return stock + consumption + criticality + expiry;
    }

    public PriorityWeights normalised() {
        double s = sum();
        return new PriorityWeights(stock / s, consumption / s, criticality / s, expiry / s);
    }
}
