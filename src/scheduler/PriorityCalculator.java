package scheduler;

import models.BatchExpiry;
import models.Criticality;
import java.util.List;

/**
 * Core mathematical engine for medicine restocking priority calculation.
 * Preserves the exact formula and business rules from the SmartMed project:
 *
 * <pre>
 * Priority Score = 100 * ( W1 * StockUrgency
 *                        + W2 * ConsumptionUrgency
 *                        + W3 * Criticality
 *                        + W4 * ExpiryRisk )
 * </pre>
 *
 * Every factor is normalised to [0, 1]; weights are normalised to sum to 1.
 * Final score is rounded to 2 decimal places.
 */
public final class PriorityCalculator {

    public static final int EXPIRY_WINDOW_DAYS = 30;
    public static final double DEFAULT_COVER_HORIZON_DAYS = 14.0;

    private final PriorityWeights weights;
    private final double coverHorizonDays;

    public PriorityCalculator(PriorityWeights weights, double coverHorizonDays) {
        if (coverHorizonDays <= 0) {
            throw new IllegalArgumentException("coverHorizonDays must be > 0");
        }
        this.weights = weights.normalised();
        this.coverHorizonDays = coverHorizonDays;
    }

    public PriorityCalculator(PriorityWeights weights) {
        this(weights, DEFAULT_COVER_HORIZON_DAYS);
    }

    public PriorityCalculator() {
        this(PriorityWeights.DEFAULT);
    }

    public PriorityWeights weights() {
        return weights;
    }

    public double coverHorizonDays() {
        return coverHorizonDays;
    }

    /**
     * Calculates the priority breakdown and overall score for the given inputs.
     */
    public PriorityBreakdown calculate(PriorityInputs in) {
        double stockU = stockUrgency(in.currentStock(), in.reorderLevel());
        double consU = consumptionUrgency(in.currentStock(), in.avgDailyConsumption());
        double crit = in.criticality() == null ? Criticality.MEDIUM.factor() : in.criticality().factor();
        double exp = expiryRisk(in.batches());

        double rawScore = 100.0 * (weights.stock() * stockU
                + weights.consumption() * consU
                + weights.criticality() * crit
                + weights.expiry() * exp);

        return new PriorityBreakdown(stockU, consU, crit, exp, weights, round(rawScore));
    }

    /**
     * StockUrgency = clamp( 1 - stock / (2 * reorderLevel) )
     * - Stock <= 0: 1.0 (empty stock)
     * - Stock == reorderLevel: 0.5
     * - Stock >= 2 * reorderLevel: 0.0 (safe buffer)
     */
    public static double stockUrgency(long stock, long reorderLevel) {
        if (stock <= 0) {
            return 1.0;
        }
        if (reorderLevel <= 0) {
            return 0.0;
        }
        return clamp(1.0 - (double) stock / (2.0 * reorderLevel));
    }

    /**
     * ConsumptionUrgency = clamp( 1 - daysOfCover / coverHorizonDays )
     * daysOfCover = stock / avgDailyConsumption
     * - No consumption: 0.0
     * - Stock <= 0: 1.0
     * - daysOfCover >= 14 days: 0.0
     */
    public double consumptionUrgency(long stock, double avgDailyConsumption) {
        if (avgDailyConsumption <= 0) {
            return 0.0;
        }
        if (stock <= 0) {
            return 1.0;
        }
        double daysOfCover = (double) stock / avgDailyConsumption;
        return clamp(1.0 - daysOfCover / coverHorizonDays);
    }

    /**
     * ExpiryRisk = sum(qty_i * proximity_i) / sum(qty_i)
     * proximity_i = clamp( (30 - daysToExpiry_i) / 30 )
     * - Expired batches have proximity 1.0
     * - Batches > 30 days away have proximity 0.0
     */
    public static double expiryRisk(List<BatchExpiry> batches) {
        if (batches == null || batches.isEmpty()) {
            return 0.0;
        }
        double total = 0;
        double weighted = 0;
        for (BatchExpiry b : batches) {
            if (b.quantity() <= 0) {
                continue;
            }
            total += b.quantity();
            double proximity = clamp((EXPIRY_WINDOW_DAYS - (double) b.daysToExpiry()) / EXPIRY_WINDOW_DAYS);
            weighted += b.quantity() * proximity;
        }
        return total == 0 ? 0.0 : clamp(weighted / total);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
