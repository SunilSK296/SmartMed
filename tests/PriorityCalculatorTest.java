import models.BatchExpiry;
import models.Criticality;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import scheduler.PriorityBreakdown;
import scheduler.PriorityCalculator;
import scheduler.PriorityInputs;
import scheduler.PriorityWeights;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying the exact mathematical priority formula from the SmartMed project.
 */
public class PriorityCalculatorTest {

    private final PriorityCalculator calc = new PriorityCalculator();

    private static PriorityInputs in(long stock, long reorder, double rate, Criticality c, BatchExpiry... b) {
        return new PriorityInputs(stock, reorder, rate, c, List.of(b));
    }

    @Test
    @DisplayName("Stock urgency is normalised between 0 and 1 with 0.5 at threshold")
    public void testStockUrgency() {
        assertEquals(1.0, PriorityCalculator.stockUrgency(0, 100));
        assertEquals(0.5, PriorityCalculator.stockUrgency(100, 100), 1e-9);
        assertEquals(0.0, PriorityCalculator.stockUrgency(200, 100), 1e-9);
        assertEquals(0.0, PriorityCalculator.stockUrgency(5000, 100));
        assertEquals(1.0, PriorityCalculator.stockUrgency(-10, 100));
    }

    @Test
    @DisplayName("Consumption urgency uses days of cover over 14-day horizon")
    public void testConsumptionUrgency() {
        assertEquals(0.0, calc.consumptionUrgency(100, 0));
        assertEquals(1.0, calc.consumptionUrgency(0, 5));
        assertEquals(0.5, calc.consumptionUrgency(70, 10), 1e-9); // 7 days cover of 14-day horizon
        assertEquals(0.0, calc.consumptionUrgency(1000, 10), 1e-9);
    }

    @Test
    @DisplayName("Expiry risk weighs proximity within 30-day window by batch quantity")
    public void testExpiryRisk() {
        assertEquals(0.0, PriorityCalculator.expiryRisk(List.of()));
        assertEquals(0.0, PriorityCalculator.expiryRisk(List.of(new BatchExpiry(10, 90))));
        assertEquals(1.0, PriorityCalculator.expiryRisk(List.of(new BatchExpiry(10, -3))), 1e-9);
        assertEquals(0.5, PriorityCalculator.expiryRisk(List.of(new BatchExpiry(10, 15))), 1e-9);

        // Half stock expires today (risk 1.0), half safe (risk 0.0) -> 0.5
        assertEquals(0.5, PriorityCalculator.expiryRisk(List.of(
                new BatchExpiry(50, 0),
                new BatchExpiry(50, 200)
        )), 1e-9);
    }

    @Test
    @DisplayName("Priority score is on a 0-100 scale using default weights (40/25/20/15)")
    public void testScoreScale() {
        PriorityBreakdown worst = calc.calculate(in(0, 100, 10, Criticality.CRITICAL, new BatchExpiry(0, -1)));
        // stockU=1.0 (40), consU=1.0 (25), crit=1.0 (20), expiry=0 (0) -> 85.0
        assertEquals(85.0, worst.score(), 1e-9);

        PriorityBreakdown calm = calc.calculate(in(1000, 100, 1, Criticality.LOW, new BatchExpiry(1000, 300)));
        // stockU=0, consU=0, crit=0.25 (5), expiry=0 -> 5.0
        assertEquals(5.0, calm.score(), 1e-9);
    }

    @Test
    @DisplayName("Worked Insulin example from the SmartMed production documentation")
    public void testWorkedInsulinExample() {
        // Insulin: CRITICAL, reorder 80, stock 22, 12 units/day, nearest expiry 60 days
        PriorityBreakdown b = calc.calculate(in(22, 80, 12, Criticality.CRITICAL, new BatchExpiry(22, 60)));
        assertEquals(0.8625, b.stockUrgency(), 1e-9);
        assertEquals(1 - (22.0 / 12) / 14.0, b.consumptionUrgency(), 1e-9);
        assertEquals(0.0, b.expiryRisk(), 1e-9);
        assertEquals(76.23, b.score(), 0.005);
    }

    @Test
    @DisplayName("Weights normalization and safety validation")
    public void testWeightsNormalization() {
        PriorityCalculator custom = new PriorityCalculator(new PriorityWeights(10, 10, 10, 10));
        assertEquals(0.25, custom.weights().stock(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> new PriorityWeights(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PriorityWeights(-1, 1, 1, 1));
    }
}
