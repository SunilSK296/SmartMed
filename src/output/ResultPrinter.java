package output;

import heap.HeapStats;
import heap.Indexed4AryMaxHeap;
import models.Medicine;
import scheduler.HeapSnapshot;
import scheduler.PriorityBreakdown;
import scheduler.RestockTaskNode;
import service.InventoryService;

import java.util.List;

/**
 * Formats and prints formatted console output for inventory tables,
 * priority calculations, heap operations, and scheduling rankings.
 */
public class ResultPrinter {

    // ================================================================== A. Inventory Summary

    public static void printInventorySummary(List<Medicine> medicines) {
        System.out.println("\n==================================================================================================================");
        System.out.println("                                         INVENTORY INPUT SUMMARY                                                  ");
        System.out.println("==================================================================================================================");
        System.out.printf("| %-8s | %-28s | %-8s | %-8s | %-10s | %-10s | %-9s | %-9s |\n",
                "ID", "Medicine Name", "Stock", "MinStock", "DailyRate", "Criticality", "LeadTime", "Restock?");
        System.out.println("|----------|------------------------------|----------|----------|------------|------------|-----------|-----------|");

        for (Medicine m : medicines) {
            boolean req = m.requiresRestock();
            System.out.printf("| %-8s | %-28s | %8d | %8d | %10.1f | %-10s | %8dd | %-9s |\n",
                    m.getMedicineId(),
                    truncate(m.getMedicineName(), 28),
                    m.getCurrentStock(),
                    m.getMinimumStock(),
                    m.getDailyConsumption(),
                    m.getCriticality(),
                    m.getSupplierLeadTime(),
                    req ? "YES (LOW)" : "NO (OK)");
        }
        System.out.println("==================================================================================================================");
        System.out.printf("Total Medicines: %d | Requiring Restock: %d\n",
                medicines.size(), medicines.stream().filter(Medicine::requiresRestock).count());
    }

    // ================================================================== B. Priority Calculations

    public static void printPriorityCalculations(List<Medicine> medicines, InventoryService service) {
        System.out.println("\n==================================================================================================================");
        System.out.println("                                      DETAILED PRIORITY CALCULATIONS                                              ");
        System.out.println("==================================================================================================================");
        System.out.println("Formula: Score = 100 * (0.40 * StockUrgency + 0.25 * ConsumptionUrgency + 0.20 * Criticality + 0.15 * ExpiryRisk)");
        System.out.println("------------------------------------------------------------------------------------------------------------------");
        System.out.printf("| %-8s | %-22s | %-7s | %-7s | %-8s | %-8s | %-8s | %-8s | %-9s |\n",
                "ID", "Medicine Name", "StockU", "ConsU", "CritFact", "ExpRisk", "Effective", "Reorder", "PRIORITY");
        System.out.println("|----------|------------------------|---------|---------|----------|----------|----------|----------|-----------|");

        for (Medicine m : medicines) {
            PriorityBreakdown pb = service.calculatePriority(m);
            System.out.printf("| %-8s | %-22s | %7.3f | %7.3f | %8.2f | %8.3f | %8d | %8d | %8.2f  |\n",
                    m.getMedicineId(),
                    truncate(m.getMedicineName(), 22),
                    pb.stockUrgency(),
                    pb.consumptionUrgency(),
                    pb.criticality(),
                    pb.expiryRisk(),
                    m.getEffectiveStock(),
                    m.getMinimumStock(),
                    pb.score());
        }
        System.out.println("==================================================================================================================");
    }

    public static void printSingleMedicinePriority(Medicine m, PriorityBreakdown pb) {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("                    MEDICINE PRIORITY CALCULATION EXPLANATION                   ");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Medicine: %s [%s]\n", m.getMedicineName(), m.getMedicineId());
        System.out.printf("Current Usable Stock: %d | Minimum Stock (Reorder Level): %d\n", m.getUsableStock(), m.getMinimumStock());
        System.out.printf("Daily Consumption: %.2f units/day | Clinical Criticality: %s (Factor: %.2f)\n",
                m.getDailyConsumption(), m.getCriticality(), m.getCriticality().factor());
        System.out.printf("Expiring Batches Within 30 Days: %d units | Effective Stock: %d\n",
                m.getExpiringSoonStock(), m.getEffectiveStock());
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Factor Calculations:");
        System.out.printf("  1. Stock Urgency       = clamp(1 - %d / (2 * %d)) = %.4f (Weight: %.0f%%)\n",
                m.getUsableStock(), m.getMinimumStock(), pb.stockUrgency(), pb.weights().stock() * 100);

        double daysOfCover = m.getDailyConsumption() > 0 ? (double) m.getUsableStock() / m.getDailyConsumption() : 0.0;
        System.out.printf("  2. Days of Cover       = %d / %.2f = %.2f days\n", m.getUsableStock(), m.getDailyConsumption(), daysOfCover);
        System.out.printf("     Consumption Urgency = clamp(1 - %.2f / 14.0) = %.4f (Weight: %.0f%%)\n",
                daysOfCover, pb.consumptionUrgency(), pb.weights().consumption() * 100);

        System.out.printf("  3. Criticality Factor  = %.2f (Weight: %.0f%%)\n",
                pb.criticality(), pb.weights().criticality() * 100);

        System.out.printf("  4. Expiry Risk Factor  = %.4f (Weight: %.0f%%)\n",
                pb.expiryRisk(), pb.weights().expiry() * 100);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("FINAL PRIORITY SCORE = 100 * (%.2f * %.4f + %.2f * %.4f + %.2f * %.2f + %.2f * %.4f)\n",
                pb.weights().stock(), pb.stockUrgency(),
                pb.weights().consumption(), pb.consumptionUrgency(),
                pb.weights().criticality(), pb.criticality(),
                pb.weights().expiry(), pb.expiryRisk());
        System.out.printf("                     = %.2f / 100.00\n", pb.score());
        System.out.println("--------------------------------------------------------------------------------");
    }

    // ================================================================== C. Heap Structure Visualizer

    public static void printHeapStructure(Indexed4AryMaxHeap<RestockTaskNode> heap) {
        System.out.println("\n==================================================================================================================");
        System.out.println("                               INDEXED 4-ARY MAX HEAP INTERNAL STRUCTURE                                          ");
        System.out.println("==================================================================================================================");

        List<RestockTaskNode> items = heap.toList();
        int n = items.size();
        System.out.printf("Heap Size: %d | Max Capacity (Dynamic): %d | Valid Heap: %b | Index Consistent: %b\n",
                n, n, heap.validateHeap(), heap.isIndexConsistent());

        if (items.isEmpty()) {
            System.out.println("[Heap is currently empty]");
            System.out.println("==================================================================================================================");
            return;
        }

        System.out.println("\n--- Level-Order Dynamic Array & 4-ary Parent/Child Relationships ---");
        System.out.printf("| %-5s | %-10s | %-24s | %-8s | %-6s | %-20s |\n",
                "Index", "Task ID", "Medicine Name", "Priority", "Parent", "Children (c1,c2,c3,c4)");
        System.out.println("|-------|------------|--------------------------|----------|--------|----------------------|");

        for (int i = 0; i < n; i++) {
            RestockTaskNode item = items.get(i);
            int p = Indexed4AryMaxHeap.parentOf(i);
            int c1 = 4 * i + 1;
            int c2 = 4 * i + 2;
            int c3 = 4 * i + 3;
            int c4 = 4 * i + 4;

            StringBuilder childStr = new StringBuilder();
            if (c1 < n) childStr.append(c1);
            if (c2 < n) childStr.append(",").append(c2);
            if (c3 < n) childStr.append(",").append(c3);
            if (c4 < n) childStr.append(",").append(c4);
            if (childStr.length() == 0) childStr.append("-");

            System.out.printf("| %-5d | %-10s | %-24s | %8.2f | %-6s | %-20s |\n",
                    i, item.getId(), truncate(item.getMedicineName(), 24), item.getPriority(),
                    p == -1 ? "ROOT" : String.valueOf(p), childStr.toString());
        }

        System.out.println("\n--- Fast O(1) Medicine ID -> Array Index Hash Map Mapping ---");
        for (int i = 0; i < n; i++) {
            String id = items.get(i).getId();
            int mappedIndex = heap.indexOfId(id);
            System.out.printf("  Map['%s'] -> Index %d %s\n",
                    id, mappedIndex, mappedIndex == i ? "(CONSISTENT)" : "(MISMATCH!)");
        }

        System.out.println("\n--- ASCII Tree Visualization (First 2 Levels: 1 Root + up to 4 Children) ---");
        printAsciiTree(items);
        System.out.println("==================================================================================================================");
    }

    private static void printAsciiTree(List<RestockTaskNode> items) {
        if (items.isEmpty()) return;
        RestockTaskNode root = items.get(0);
        System.out.printf("                   [ROOT: %s (%.2f)]\n", root.getId(), root.getPriority());
        System.out.println("           /               |               |               \\");

        int[] children = Indexed4AryMaxHeap.childrenOf(0);
        String[] labels = new String[4];
        for (int c = 0; c < 4; c++) {
            int idx = children[c];
            if (idx < items.size()) {
                labels[c] = String.format("%s (%.1f)", items.get(idx).getId(), items.get(idx).getPriority());
            } else {
                labels[c] = "   [empty]  ";
            }
        }
        System.out.printf("  %-16s %-16s %-16s %-16s\n", labels[0], labels[1], labels[2], labels[3]);
        if (items.size() > 5) {
            System.out.println("         ... (Grandchildren at indices 5.." + (items.size() - 1) + " omitted for brevity) ...");
        }
    }

    // ================================================================== D. Final Restocking Schedule

    public static void printFinalSchedule(List<RestockTaskNode> schedule, int totalReceived) {
        System.out.println("\n==================================================================================================================");
        System.out.println("                                    FINAL RANKED MEDICINE RESTOCKING SCHEDULE                                     ");
        System.out.println("==================================================================================================================");
        System.out.printf("| %-4s | %-10s | %-28s | %-14s | %-13s | %-26s |\n",
                "Rank", "Medicine ID", "Medicine Name", "Priority Score", "Current Stock", "Suggested Restock Quantity");
        System.out.println("|------|------------|------------------------------|----------------|---------------|----------------------------|");

        long totalRestockUnits = 0;
        int rank = 1;
        for (RestockTaskNode task : schedule) {
            System.out.printf("| %-4d | %-10s | %-28s | %14.2f | %13d | %26d |\n",
                    rank++,
                    task.getMedicineId(),
                    truncate(task.getMedicineName(), 28),
                    task.getPriority(),
                    task.getCurrentStock(),
                    task.getSuggestedQuantity());
            totalRestockUnits += task.getSuggestedQuantity();
        }

        System.out.println("==================================================================================================================");
        System.out.println("SCHEDULING SUMMARY STATISTICS:");
        System.out.printf("  - Total Medicines Evaluated        : %d\n", totalReceived);
        System.out.printf("  - Medicines Requiring Restocking    : %d\n", schedule.size());
        System.out.printf("  - Medicines Scheduled in Heap      : %d\n", schedule.size());
        System.out.printf("  - Total Suggested Restock Quantity : %d units\n", totalRestockUnits);
        if (!schedule.isEmpty()) {
            RestockTaskNode top = schedule.get(0);
            System.out.printf("  - Highest Priority Urgent Restock  : %s [%s] (Score: %.2f)\n",
                    top.getMedicineName(), top.getMedicineId(), top.getPriority());
        }
        System.out.println("==================================================================================================================");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
