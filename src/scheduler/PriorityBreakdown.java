package scheduler;

/**
 * Detailed breakdown of the four normalised factors [0, 1], their configured weights,
 * and the resulting final 0..100 priority score.
 */
public record PriorityBreakdown(double stockUrgency, double consumptionUrgency, double criticality,
                                double expiryRisk, PriorityWeights weights, double score) {
    @Override
    public String toString() {
        return String.format("Score: %.2f (StockU: %.3f * %.2f, ConsU: %.3f * %.2f, Crit: %.3f * %.2f, ExpRisk: %.3f * %.2f)",
                score, stockUrgency, weights.stock(), consumptionUrgency, weights.consumption(),
                criticality, weights.criticality(), expiryRisk, weights.expiry());
    }
}
