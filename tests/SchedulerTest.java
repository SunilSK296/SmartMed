import models.BatchExpiry;
import models.Criticality;
import models.Medicine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import scheduler.RestockTaskNode;
import service.InventoryService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for inventory scheduling scenarios, restock triggers,
 * criticality ordering, suggested quantity formulas, and invalid inputs.
 */
public class SchedulerTest {

    @Test
    @DisplayName("Scenario 1: No medicine requires restocking when stock is sufficient")
    public void testNoMedicineRequiresRestock() {
        InventoryService service = new InventoryService();
        service.addMedicine(new Medicine("M1", "Med 1", 300, 100, 5.0, Criticality.LOW));
        service.addMedicine(new Medicine("M2", "Med 2", 500, 150, 10.0, Criticality.MEDIUM));

        List<RestockTaskNode> schedule = service.generateRestockSchedule();
        assertTrue(schedule.isEmpty(), "Schedule should be empty when all medicines are well-stocked");
    }

    @Test
    @DisplayName("Scenario 2: Single medicine requires restocking")
    public void testSingleMedicineRequiresRestock() {
        InventoryService service = new InventoryService();
        service.addMedicine(new Medicine("M1", "Med Well-stocked", 250, 100, 5.0, Criticality.LOW));
        service.addMedicine(new Medicine("M2", "Med Depleted", 15, 100, 12.0, Criticality.HIGH));

        List<RestockTaskNode> schedule = service.generateRestockSchedule();
        assertEquals(1, schedule.size());
        assertEquals("M2", schedule.get(0).getMedicineId());
        assertTrue(schedule.get(0).getPriority() > 50.0);
    }

    @Test
    @DisplayName("Scenario 3: Multiple medicines ranked in descending priority order")
    public void testMultipleMedicinesDescendingOrder() {
        InventoryService service = new InventoryService();
        service.addMedicine(new Medicine("M1", "Low Priority", 80, 100, 2.0, Criticality.LOW));
        service.addMedicine(new Medicine("M2", "Critical Urgent", 5, 100, 20.0, Criticality.CRITICAL));
        service.addMedicine(new Medicine("M3", "Medium Priority", 40, 100, 8.0, Criticality.MEDIUM));

        List<RestockTaskNode> schedule = service.generateRestockSchedule();
        assertEquals(3, schedule.size());

        // M2 must be #1, followed by M3, then M1
        assertEquals("M2", schedule.get(0).getMedicineId());
        assertEquals("M3", schedule.get(1).getMedicineId());
        assertEquals("M1", schedule.get(2).getMedicineId());

        for (int i = 1; i < schedule.size(); i++) {
            assertTrue(schedule.get(i - 1).getPriority() >= schedule.get(i).getPriority(),
                    "Schedule must strictly descend in priority score");
        }
    }

    @Test
    @DisplayName("Scenario 4: Clinical criticality correctly differentiates priority when stock is identical")
    public void testCriticalityDifferentiation() {
        InventoryService service = new InventoryService();
        service.addMedicine(new Medicine("M-LOW", "Low Crit", 10, 100, 10.0, Criticality.LOW));
        service.addMedicine(new Medicine("M-CRIT", "Critical Crit", 10, 100, 10.0, Criticality.CRITICAL));

        List<RestockTaskNode> schedule = service.generateRestockSchedule();
        assertEquals(2, schedule.size());
        assertEquals("M-CRIT", schedule.get(0).getMedicineId());
        assertEquals("M-LOW", schedule.get(1).getMedicineId());
        assertTrue(schedule.get(0).getPriority() > schedule.get(1).getPriority());
    }

    @Test
    @DisplayName("Scenario 5: Expiry-triggered restocking when usable stock is high but effective stock is depleted")
    public void testExpiryTriggeredRestock() {
        InventoryService service = new InventoryService();
        // Usable stock is 120, reorderLevel is 100.
        // However, 50 units expire in 10 days! Effective stock = 120 - 50 = 70 <= 100.
        Medicine m = new Medicine("M-EXP", "Expiring Drug", 120, 100, 50, 10.0, Criticality.HIGH, null, 3);
        m.addBatch(new BatchExpiry(50, 10)); // expires in 10 days
        m.addBatch(new BatchExpiry(70, 120)); // expires in 120 days
        service.addMedicine(m);

        assertTrue(m.requiresRestock(), "Medicine should require restock because effective stock (70) <= reorder level (100)");

        List<RestockTaskNode> schedule = service.generateRestockSchedule();
        assertEquals(1, schedule.size());
        assertEquals("M-EXP", schedule.get(0).getMedicineId());
    }

    @Test
    @DisplayName("Scenario 6: Correct calculation of suggested restock quantity")
    public void testSuggestedRestockQuantityCalculation() {
        // reorderLevel = 100 -> targetByLevel = 100 * 3 = 300
        // dailyConsumption = 15 -> targetByDemand = 15 * 30 = 450
        // targetStock = max(300, 450) = 450
        // currentStock = 20 -> suggested = 450 - 20 = 430
        Medicine m = new Medicine("MED-RESTOCK", "Restock Med", 20, 100, 15.0, Criticality.MEDIUM);
        assertEquals(430, m.calculateSuggestedQuantity());
    }

    @Test
    @DisplayName("Scenario 7: Invalid input handling")
    public void testInvalidInputHandling() {
        assertThrows(IllegalArgumentException.class, () -> new Medicine(null, "Name", 10, 10, 1.0, Criticality.LOW));
        assertThrows(IllegalArgumentException.class, () -> new Medicine("   ", "Name", 10, 10, 1.0, Criticality.LOW));
        assertThrows(IllegalArgumentException.class, () -> new BatchExpiry(-5, 10));
    }
}
