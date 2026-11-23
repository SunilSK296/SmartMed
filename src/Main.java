import benchmark.PerformanceBenchmark;
import heap.Indexed4AryMaxHeap;
import input.InputHandler;
import models.Medicine;
import output.ResultPrinter;
import scheduler.PriorityBreakdown;
import scheduler.RestockPriorityScheduler;
import scheduler.RestockTaskNode;
import service.InventoryService;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Main executable entry point for the standalone SmartMed Priority Scheduling Engine.
 * Provides the interactive demonstration CLI with 13 functional operations.
 */
public class Main {

    private static final String DEFAULT_SAMPLE_DATA = "data/sample_medicines.json";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        InputHandler inputHandler = new InputHandler(scanner);
        InventoryService inventoryService = new InventoryService();
        RestockPriorityScheduler scheduler = inventoryService.getScheduler();

        // Enable step-by-step console logging for heap operations
        scheduler.setStepLogger(System.out::println);

        System.out.println("================================================================================");
        System.out.println("            WELCOME TO SMARTMED PRIORITY SCHEDULING ENGINE                      ");
        System.out.println("                 Standalone DAA Demonstration Suite                             ");
        System.out.println("================================================================================");

        // Preload sample inventory on startup if available
        File sampleFile = new File(DEFAULT_SAMPLE_DATA);
        if (sampleFile.exists()) {
            try {
                List<Medicine> initial = inputHandler.loadFromJson(DEFAULT_SAMPLE_DATA);
                inventoryService.addMedicines(initial);
                System.out.printf("Auto-loaded %d sample medicines from '%s'.\n", initial.size(), DEFAULT_SAMPLE_DATA);
            } catch (Exception e) {
                System.out.println("Could not auto-load sample data: " + e.getMessage());
            }
        }

        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Select an option [0-13]: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> handleLoadSampleInventory(inputHandler, inventoryService);
                case "2" -> handleManualMedicineEntry(inputHandler, inventoryService);
                case "3" -> handleDisplayInventory(inventoryService);
                case "4" -> handleCalculatePriorities(inventoryService, scanner);
                case "5" -> handleInsertIntoHeap(inventoryService, scanner);
                case "6" -> handleDisplayHeap(scheduler);
                case "7" -> handleExtractHighest(scheduler);
                case "8" -> handleUpdatePriority(scheduler, scanner);
                case "9" -> handleDeleteMedicine(scheduler, scanner);
                case "10" -> handleSearchMedicine(scheduler, scanner);
                case "11" -> handleGenerateSchedule(inventoryService, scanner);
                case "12" -> handleValidateHeap(scheduler);
                case "13" -> PerformanceBenchmark.runAll();
                case "0" -> {
                    System.out.println("\nExiting SmartMed Standalone Priority Scheduling Engine. Goodbye!");
                    running = false;
                }
                default -> System.out.println("Invalid selection. Please choose a number between 0 and 13.");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n========== SMARTMED PRIORITY SCHEDULING ENGINE ==========");
        System.out.println(" 1. Load Sample Inventory");
        System.out.println(" 2. Enter Medicine Manually");
        System.out.println(" 3. Display Inventory");
        System.out.println(" 4. Calculate Medicine Priorities");
        System.out.println(" 5. Insert Medicine into Heap");
        System.out.println(" 6. Display Heap Structure");
        System.out.println(" 7. Extract Highest Priority Medicine");
        System.out.println(" 8. Update Medicine Priority");
        System.out.println(" 9. Delete Medicine by ID");
        System.out.println("10. Search Medicine by ID");
        System.out.println("11. Generate Complete Restocking Schedule");
        System.out.println("12. Run Heap Validation");
        System.out.println("13. Run Performance Benchmark");
        System.out.println(" 0. Exit");
        System.out.println("=========================================================");
    }

    // ------------------------------------------------------------------ Menu Action Handlers

    private static void handleLoadSampleInventory(InputHandler inputHandler, InventoryService service) {
        try {
            List<Medicine> medicines = inputHandler.loadFromJson(DEFAULT_SAMPLE_DATA);
            service.clearInventory();
            service.addMedicines(medicines);
            System.out.printf("Successfully loaded %d medicines from '%s'.\n", medicines.size(), DEFAULT_SAMPLE_DATA);
            ResultPrinter.printInventorySummary(service.getAllMedicines());
        } catch (IOException e) {
            System.err.println("Error loading sample inventory: " + e.getMessage());
        }
    }

    private static void handleManualMedicineEntry(InputHandler inputHandler, InventoryService service) {
        Medicine m = inputHandler.promptMedicine();
        service.addMedicine(m);
        System.out.println("Medicine stored in inventory. Total medicines: " + service.getInventorySize());
    }

    private static void handleDisplayInventory(InventoryService service) {
        List<Medicine> all = service.getAllMedicines();
        if (all.isEmpty()) {
            System.out.println("Inventory is currently empty. Please load or add medicines first.");
            return;
        }
        ResultPrinter.printInventorySummary(all);
    }

    private static void handleCalculatePriorities(InventoryService service, Scanner scanner) {
        List<Medicine> all = service.getAllMedicines();
        if (all.isEmpty()) {
            System.out.println("Inventory is empty. Please load sample data first.");
            return;
        }
        ResultPrinter.printPriorityCalculations(all, service);

        System.out.print("\nEnter a Medicine ID to see its detailed step-by-step calculation breakdown (or press Enter to skip): ");
        String id = scanner.nextLine().trim();
        if (!id.isEmpty()) {
            Optional<Medicine> m = service.getMedicine(id);
            if (m.isPresent()) {
                PriorityBreakdown pb = service.calculatePriority(m.get());
                ResultPrinter.printSingleMedicinePriority(m.get(), pb);
            } else {
                System.out.println("Medicine ID '" + id + "' not found in inventory.");
            }
        }
    }

    private static void handleInsertIntoHeap(InventoryService service, Scanner scanner) {
        List<Medicine> all = service.getAllMedicines();
        if (all.isEmpty()) {
            System.out.println("Inventory is empty. Please load sample data first.");
            return;
        }

        System.out.println("\nSelect a medicine to insert into the 4-ary Max Heap:");
        for (int i = 0; i < all.size(); i++) {
            Medicine m = all.get(i);
            System.out.printf("  [%d] %-8s - %s (Stock: %d, Min: %d)\n",
                    i + 1, m.getMedicineId(), m.getMedicineName(), m.getCurrentStock(), m.getMinimumStock());
        }
        System.out.print("Enter choice (1-" + all.size() + ") or 'all' to insert all requiring restock: ");
        String choice = scanner.nextLine().trim();

        if (choice.equalsIgnoreCase("all")) {
            int inserted = service.populateScheduler();
            System.out.printf("Successfully inserted %d eligible restock tasks into the 4-ary Max Heap.\n", inserted);
            ResultPrinter.printHeapStructure(service.getScheduler().getHeap());
            return;
        }

        try {
            int idx = Integer.parseInt(choice) - 1;
            if (idx >= 0 && idx < all.size()) {
                Medicine m = all.get(idx);
                RestockTaskNode task = service.createTaskNode(m);
                System.out.println("\nInitiating step-by-step insertion into Indexed 4-ary Max Heap:");
                service.getScheduler().insert(task);
                System.out.println("Task successfully inserted into heap.");
            } else {
                System.out.println("Invalid selection.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
        } catch (IllegalArgumentException e) {
            System.err.println("Insertion failed: " + e.getMessage());
        }
    }

    private static void handleDisplayHeap(RestockPriorityScheduler scheduler) {
        ResultPrinter.printHeapStructure(scheduler.getHeap());
    }

    private static void handleExtractHighest(RestockPriorityScheduler scheduler) {
        if (scheduler.isEmpty()) {
            System.out.println("Heap is empty; no medicine tasks to extract.");
            return;
        }

        System.out.println("\nExtracting maximum priority medicine from root...");
        Optional<RestockTaskNode> served = scheduler.extractHighest();
        served.ifPresent(task -> {
            System.out.println("\n========================================================");
            System.out.println("             SERVED RESTOCK TASK DETAILS                ");
            System.out.println("========================================================");
            System.out.printf("Task ID                    : %s\n", task.getId());
            System.out.printf("Medicine ID                : %s\n", task.getMedicineId());
            System.out.printf("Medicine Name              : %s\n", task.getMedicineName());
            System.out.printf("Effective Priority Score   : %.2f\n", task.getPriority());
            System.out.printf("Current Stock              : %d units\n", task.getCurrentStock());
            System.out.printf("Suggested Restock Quantity : %d units\n", task.getSuggestedQuantity());
            System.out.println("========================================================");
        });
    }

    private static void handleUpdatePriority(RestockPriorityScheduler scheduler, Scanner scanner) {
        if (scheduler.isEmpty()) {
            System.out.println("Heap is empty; no tasks available to update.");
            return;
        }

        System.out.print("Enter Task/Medicine ID to update: ");
        String id = scanner.nextLine().trim();

        if (!scheduler.contains(id)) {
            System.out.println("ID '" + id + "' is not present in the heap.");
            return;
        }

        RestockTaskNode current = scheduler.get(id).orElseThrow();
        System.out.printf("Current priority for '%s' is %.2f.\n", id, current.getPriority());
        System.out.print("Enter new priority score (e.g., 95.0 or 999.0 for emergency): ");

        try {
            double newScore = Double.parseDouble(scanner.nextLine().trim());
            System.out.println("\nApplying priority update and repositioning across 4-ary tree:");
            scheduler.updatePriority(id, newScore);
            System.out.println("Priority successfully updated.");
        } catch (NumberFormatException e) {
            System.out.println("Invalid numeric score.");
        }
    }

    private static void handleDeleteMedicine(RestockPriorityScheduler scheduler, Scanner scanner) {
        if (scheduler.isEmpty()) {
            System.out.println("Heap is empty.");
            return;
        }

        System.out.print("Enter Task/Medicine ID to delete: ");
        String id = scanner.nextLine().trim();

        if (!scheduler.contains(id)) {
            System.out.println("ID '" + id + "' is not in the heap.");
            return;
        }

        System.out.println("\nDeleting medicine from heap and restoring 4-ary invariants:");
        scheduler.remove(id, "User requested deletion");
        System.out.println("Medicine '" + id + "' successfully deleted from heap.");
    }

    private static void handleSearchMedicine(RestockPriorityScheduler scheduler, Scanner scanner) {
        System.out.print("Enter Medicine/Task ID to search: ");
        String id = scanner.nextLine().trim();

        int index = scheduler.getHeap().indexOfId(id);
        if (index == -1) {
            System.out.println("Medicine '" + id + "' was NOT found in the heap (O(1) Hash Map Search).");
            return;
        }

        RestockTaskNode task = scheduler.get(id).orElseThrow();
        int parent = Indexed4AryMaxHeap.parentOf(index);
        int[] children = Indexed4AryMaxHeap.childrenOf(index);

        System.out.println("\n========================================================");
        System.out.println("             O(1) HASH MAP SEARCH RESULT                ");
        System.out.println("========================================================");
        System.out.printf("Task ID                : %s\n", task.getId());
        System.out.printf("Medicine Name          : %s\n", task.getMedicineName());
        System.out.printf("Priority Score         : %.2f\n", task.getPriority());
        System.out.printf("Dynamic Array Index    : %d\n", index);
        System.out.printf("Parent Index           : %s\n", parent == -1 ? "NONE (ROOT)" : String.valueOf(parent));
        System.out.printf("4-ary Child Indices    : [%d, %d, %d, %d]\n",
                children[0], children[1], children[2], children[3]);
        System.out.printf("Current Stock          : %d\n", task.getCurrentStock());
        System.out.printf("Suggested Restock Qty  : %d\n", task.getSuggestedQuantity());
        System.out.println("========================================================");
    }

    private static void handleGenerateSchedule(InventoryService service, Scanner scanner) {
        List<Medicine> all = service.getAllMedicines();
        if (all.isEmpty()) {
            System.out.println("Inventory is empty. Please load sample inventory first.");
            return;
        }

        System.out.println("\nEvaluating inventory, calculating priorities, and building Indexed 4-ary Max Heap...");
        List<RestockTaskNode> schedule = service.generateRestockSchedule();
        ResultPrinter.printFinalSchedule(schedule, all.size());

        System.out.print("\nDo you want to run the step-by-step heap operation demonstration? (y/n) [n]: ");
        String choice = scanner.nextLine().trim();
        if (choice.equalsIgnoreCase("y") || choice.equalsIgnoreCase("yes")) {
            service.demonstrateHeapOperations(service.getMedicinesRequiringRestock(), System.out::println);
        }
    }

    private static void handleValidateHeap(RestockPriorityScheduler scheduler) {
        Indexed4AryMaxHeap<RestockTaskNode> heap = scheduler.getHeap();
        System.out.println("\n========================================================");
        System.out.println("            HEAP INVARIANTS VALIDATION REPORT           ");
        System.out.println("========================================================");
        boolean maxHeapValid = heap.validateHeap();
        boolean indexConsistent = heap.isIndexConsistent();
        boolean allInvariants = heap.validate();

        System.out.printf("  1. 4-ary Max Heap Property (Parent >= 4 Children) : %s\n",
                maxHeapValid ? "PASSED [OK]" : "FAILED [VIOLATION]");
        System.out.printf("  2. Index Map Exact 1-to-1 Consistency             : %s\n",
                indexConsistent ? "PASSED [OK]" : "FAILED [VIOLATION]");
        System.out.printf("  3. Duplicate ID Detection                         : %s\n",
                allInvariants ? "PASSED [OK]" : "FAILED [VIOLATION]");
        System.out.printf("  OVERALL 4-INVARIANT VALIDATION                    : %s\n",
                allInvariants ? "ALL PASSED [100% HEALTHY]" : "VALIDATION FAILED");
        System.out.println("========================================================");
    }
}
