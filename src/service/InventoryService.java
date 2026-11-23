package service;

import heap.Indexed4AryMaxHeap;
import models.BatchExpiry;
import models.Criticality;
import models.Medicine;
import scheduler.*;

import java.util.*;

/**
 * Service orchestrating inventory management, priority calculation,
 * restock task generation, and priority-based scheduling.
 */
public class InventoryService {

    private final Map<String, Medicine> inventory = new LinkedHashMap<>();
    private final PriorityCalculator calculator;
    private final RestockPriorityScheduler scheduler;

    public InventoryService(PriorityCalculator calculator, RestockPriorityScheduler scheduler) {
        this.calculator = calculator;
        this.scheduler = scheduler;
    }

    public InventoryService() {
        this(new PriorityCalculator(), new RestockPriorityScheduler());
    }

    public PriorityCalculator getCalculator() {
        return calculator;
    }

    public RestockPriorityScheduler getScheduler() {
        return scheduler;
    }

    // ------------------------------------------------------------------ Inventory Operations

    public void addMedicine(Medicine medicine) {
        if (medicine == null) {
            throw new IllegalArgumentException("Medicine cannot be null");
        }
        inventory.put(medicine.getMedicineId(), medicine);
    }

    public void addMedicines(Collection<Medicine> medicines) {
        for (Medicine m : medicines) {
            addMedicine(m);
        }
    }

    public Optional<Medicine> getMedicine(String medicineId) {
        return Optional.ofNullable(inventory.get(medicineId));
    }

    public List<Medicine> getAllMedicines() {
        return new ArrayList<>(inventory.values());
    }

    public void clearInventory() {
        inventory.clear();
        scheduler.clear();
    }

    public int getInventorySize() {
        return inventory.size();
    }

    // ------------------------------------------------------------------ Priority & Restock Logic

    /**
     * Converts a Medicine entity into PriorityInputs for the calculator.
     */
    public PriorityInputs toPriorityInputs(Medicine m) {
        return new PriorityInputs(
                m.getUsableStock(),
                m.getMinimumStock(),
                m.getDailyConsumption(),
                m.getCriticality(),
                m.getBatches()
        );
    }

    /**
     * Calculates the detailed priority breakdown for a medicine.
     */
    public PriorityBreakdown calculatePriority(Medicine m) {
        PriorityInputs inputs = toPriorityInputs(m);
        return calculator.calculate(inputs);
    }

    /**
     * Evaluates all inventory medicines and identifies those requiring restocking:
     * Condition: effectiveStock <= minimumStock (reorderLevel).
     */
    public List<Medicine> getMedicinesRequiringRestock() {
        List<Medicine> needingRestock = new ArrayList<>();
        for (Medicine m : inventory.values()) {
            if (m.requiresRestock()) {
                needingRestock.add(m);
            }
        }
        return needingRestock;
    }

    /**
     * Creates a RestockTaskNode for an eligible medicine.
     */
    public RestockTaskNode createTaskNode(Medicine m) {
        PriorityBreakdown breakdown = calculatePriority(m);
        long suggestedQty = m.calculateSuggestedQuantity();
        long now = System.currentTimeMillis();

        return new RestockTaskNode(
                "TASK-" + m.getMedicineId(),
                m.getMedicineId(),
                m.getMedicineName(),
                now,
                breakdown.score(),
                breakdown,
                m.getCurrentStock(),
                m.getMinimumStock(),
                suggestedQty
        );
    }

    /**
     * Populates the scheduler heap with all medicines currently requiring restocking.
     * Clears the previous heap and inserts eligible tasks.
     *
     * @return Number of tasks inserted
     */
    public int populateScheduler() {
        scheduler.clear();
        int count = 0;
        for (Medicine m : inventory.values()) {
            if (m.requiresRestock()) {
                RestockTaskNode task = createTaskNode(m);
                scheduler.insert(task);
                count++;
            }
        }
        return count;
    }

    /**
     * Generates a ranked list of medicines that require restocking, ordered by priority score descending.
     * This uses the Indexed 4-ary Max Heap's sorted order.
     */
    public List<RestockTaskNode> generateRestockSchedule() {
        populateScheduler();
        return scheduler.sorted();
    }

    /**
     * Executes a step-by-step demonstration of Indexed 4-ary Max Heap operations:
     * 1. Empty heap state
     * 2. Step-by-step insertions with parent-child comparisons & sift-up
     * 3. Array state and HashMap index tracking
     * 4. Step-by-step extract-max with child comparisons & sift-down
     * 5. Final heap state
     */
    public void demonstrateHeapOperations(List<Medicine> demoMedicines, java.util.function.Consumer<String> out) {
        Indexed4AryMaxHeap<RestockTaskNode> demoHeap = new Indexed4AryMaxHeap<>();
        demoHeap.setStepLogger(out);

        out.accept("\n================================================================================");
        out.accept("            STEP-BY-STEP INDEXED 4-ARY MAX HEAP DEMONSTRATION                   ");
        out.accept("================================================================================");

        out.accept("\n--- [Phase 1: Initial Empty Heap State] ---");
        out.accept("Heap Size: " + demoHeap.size() + ", Is Empty: " + demoHeap.isEmpty());
        out.accept("Dynamic Array: []");
        out.accept("Index Map: {}");

        out.accept("\n--- [Phase 2: Step-by-Step Insertions & Sift-Up] ---");
        long baseTime = System.currentTimeMillis();
        int seq = 0;
        for (Medicine m : demoMedicines) {
            PriorityBreakdown pb = calculatePriority(m);
            RestockTaskNode node = new RestockTaskNode(
                    m.getMedicineId(), m.getMedicineId(), m.getMedicineName(),
                    baseTime + (seq++ * 100), pb.score(), pb,
                    m.getCurrentStock(), m.getMinimumStock(), m.calculateSuggestedQuantity()
            );

            out.accept(String.format("\n[Action] Inserting %s (%s) with Priority Score %.2f",
                    node.getId(), node.getMedicineName(), node.getPriority()));

            demoHeap.insert(node);

            out.accept("  -> Current Backing Array (Level-order):");
            List<RestockTaskNode> arr = demoHeap.toList();
            for (int i = 0; i < arr.size(); i++) {
                int p = Indexed4AryMaxHeap.parentOf(i);
                out.accept(String.format("     Index %d [Parent: %s]: %s (Priority: %.2f)",
                        i, p >= 0 ? String.valueOf(p) : "NONE (ROOT)", arr.get(i).getId(), arr.get(i).getPriority()));
            }
            out.accept("  -> Index Map Consistency Check: " + demoHeap.isIndexConsistent());
            out.accept("  -> 4-ary Max Heap Property Check: " + demoHeap.validateHeap());
        }

        out.accept("\n--- [Phase 3: Current Root (Peek Max)] ---");
        demoHeap.peekMax().ifPresent(root -> {
            out.accept(String.format("Root Medicine: %s (%s) with Highest Priority: %.2f",
                    root.getId(), root.getMedicineName(), root.getPriority()));
        });

        out.accept("\n--- [Phase 4: Priority Update Demonstration] ---");
        if (demoHeap.size() > 1) {
            String victimId = demoMedicines.get(demoMedicines.size() - 1).getMedicineId();
            out.accept(String.format("\n[Action] Updating '%s' Priority to Emergency 999.00 (Heapify Up to Root)", victimId));
            demoHeap.updatePriority(victimId, 999.0);
            out.accept("  -> New Root after Update: " + demoHeap.peekMax().orElseThrow().getId());
            out.accept("  -> Index Map Consistency Check: " + demoHeap.isIndexConsistent());

            out.accept(String.format("\n[Action] Demoting '%s' Priority down to 5.00 (Heapify Down across 4 children)", victimId));
            demoHeap.updatePriority(victimId, 5.0);
            out.accept("  -> New Root after Demotion: " + demoHeap.peekMax().orElseThrow().getId());
            out.accept("  -> New Index of '" + victimId + "': " + demoHeap.indexOfId(victimId));
            out.accept("  -> 4-ary Max Heap Property Check: " + demoHeap.validateHeap());
        }

        out.accept("\n--- [Phase 5: Step-by-Step Extract Max & Sift-Down] ---");
        int rank = 1;
        while (!demoHeap.isEmpty()) {
            out.accept(String.format("\n[Action] Extracting Rank #%d highest priority medicine...", rank));
            RestockTaskNode served = demoHeap.extractMax();
            out.accept(String.format("  -> SERVED: Rank #%d | ID: %s | Name: %s | Priority: %.2f | Stock: %d | Suggested Restock: %d",
                    rank++, served.getId(), served.getMedicineName(), served.getPriority(),
                    served.getCurrentStock(), served.getSuggestedQuantity()));
            out.accept(String.format("  -> Heap remaining size: %d, Valid Heap: %b, Index Map Consistent: %b",
                    demoHeap.size(), demoHeap.validateHeap(), demoHeap.isIndexConsistent()));
        }

        out.accept("\n--- [Phase 6: Final Heap State] ---");
        out.accept("Heap is now empty: " + demoHeap.isEmpty());
        out.accept("Execution Statistics: " + demoHeap.stats());
        out.accept("================================================================================\n");
    }
}
