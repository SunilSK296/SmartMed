package scheduler;

import models.BatchExpiry;
import models.Criticality;
import java.util.List;

/**
 * Raw input parameters for the priority calculation formula.
 *
 * <p>NOTE: In strict accordance with the SmartMed production architecture,
 * supplier lead time is deliberately NOT part of the priority formula inputs.
 * The core formula evaluates clinical need, consumption rate, depletion risk, and expiry.</p>
 */
public record PriorityInputs(long currentStock, long reorderLevel, double avgDailyConsumption,
                             Criticality criticality, List<BatchExpiry> batches) {
}
