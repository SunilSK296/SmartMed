package benchmark;

import heap.HeapItem;
import heap.Indexed4AryMaxHeap;
import models.Criticality;
import models.Medicine;
import scheduler.PriorityCalculator;
import scheduler.RestockTaskNode;
import service.InventoryService;

import java.util.*;

/**
 * Performance benchmarking suite for the Indexed 4-ary Max Heap and Priority Scheduling Engine.
 * Rigorously measures execution time across inventory sizes (100, 1,000, 10,000, 50,000)
 * using {@link System#nanoTime()}.
 */
public class PerformanceBenchmark {

    static final class BenchmarkItem implements HeapItem {
        private final String id;
        private double priority;
        private final long createdAtMillis;

        BenchmarkItem(String id, double priority, long createdAtMillis) {
            this.id = id;
            this.priority = priority;
            this.createdAtMillis = createdAtMillis;
        }

        @Override public String getId() { return id; }
        @Override public double getPriority() { return priority; }
        @Override public void setPriority(double priority) { this.priority = priority; }
        @Override public long getCreatedAtMillis() { return createdAtMillis; }
    }

    public record BenchmarkResult(
            int size,
            double floydBuildMs,
            double sequentialInsertMs,
            double avgInsertUs,
            double avgSearchUs,
            double avgUpdatePriorityUs,
            double avgExtractMaxUs,
            double totalSchedulingMs
    ) {}

    /**
     * Executes the comprehensive benchmark across 100, 1,000, 10,000, and 50,000 items.
     */
    public static List<BenchmarkResult> runAll() {
        System.out.println("\n==================================================================================================================");
        System.out.println("                         PERFORMANCE BENCHMARK FOR INDEXED 4-ARY MAX HEAP                                         ");
        System.out.println("==================================================================================================================");
        System.out.println("Running warmup JVM passes...");
        runSingleBenchmark(500, false); // Warmup JVM JIT compiler

        int[] sizes = {100, 1000, 10000, 50000};
        List<BenchmarkResult> results = new ArrayList<>();

        for (int size : sizes) {
            System.out.printf("Executing benchmark for N = %,d medicines...\n", size);
            BenchmarkResult r = runSingleBenchmark(size, true);
            results.add(r);
        }

        printResultsSummary(results);
        printComplexityComparisonTable();
        return results;
    }

    private static BenchmarkResult runSingleBenchmark(int n, boolean verbose) {
        Random rng = new Random(42);

        // Prepare synthetic test items
        List<BenchmarkItem> rawItems = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rawItems.add(new BenchmarkItem("MED-" + i, rng.nextDouble() * 100.0, i));
        }

        // 1. Measure Floyd's Bottom-Up Heap Construction O(n)
        Indexed4AryMaxHeap<BenchmarkItem> floydHeap = new Indexed4AryMaxHeap<>();
        long t0 = System.nanoTime();
        floydHeap.rebuildFrom(rawItems);
        long t1 = System.nanoTime();
        double floydBuildMs = (t1 - t0) / 1_000_000.0;

        // 2. Measure Sequential Insertion O(n log4 n)
        Indexed4AryMaxHeap<BenchmarkItem> insertHeap = new Indexed4AryMaxHeap<>();
        long t2 = System.nanoTime();
        for (BenchmarkItem item : rawItems) {
            insertHeap.insert(item);
        }
        long t3 = System.nanoTime();
        double sequentialInsertMs = (t3 - t2) / 1_000_000.0;
        double avgInsertUs = ((t3 - t2) / (double) n) / 1_000.0;

        // 3. Measure O(1) Hash Map Search / Lookup
        int sampleSize = Math.min(n, 1000);
        long t4 = System.nanoTime();
        for (int i = 0; i < sampleSize; i++) {
            String targetId = "MED-" + rng.nextInt(n);
            insertHeap.findById(targetId);
        }
        long t5 = System.nanoTime();
        double avgSearchUs = ((t5 - t4) / (double) sampleSize) / 1_000.0;

        // 4. Measure Update Priority O(log4 n) (mix of increases and decreases)
        long t6 = System.nanoTime();
        for (int i = 0; i < sampleSize; i++) {
            String targetId = "MED-" + rng.nextInt(n);
            insertHeap.updatePriority(targetId, rng.nextDouble() * 100.0);
        }
        long t7 = System.nanoTime();
        double avgUpdateUs = ((t7 - t6) / (double) sampleSize) / 1_000.0;

        // 5. Measure Extract Max O(4 log4 n)
        long t8 = System.nanoTime();
        int extractCount = Math.min(n, 1000);
        for (int i = 0; i < extractCount; i++) {
            insertHeap.extractMax();
        }
        long t9 = System.nanoTime();
        double avgExtractUs = ((t9 - t8) / (double) extractCount) / 1_000.0;

        // 6. Complete Scheduling Pipeline (Inventory -> Priority Calc -> Heap -> Ranked Schedule)
        InventoryService schedService = new InventoryService();
        for (int i = 0; i < n; i++) {
            long stock = rng.nextInt(150);
            long minStock = 100;
            double cons = 5.0 + rng.nextDouble() * 20.0;
            Criticality crit = Criticality.values()[rng.nextInt(Criticality.values().length)];
            Medicine m = new Medicine("MED-" + i, "Medicine-" + i, stock, minStock, cons, crit);
            schedService.addMedicine(m);
        }

        long t10 = System.nanoTime();
        List<RestockTaskNode> schedule = schedService.generateRestockSchedule();
        long t11 = System.nanoTime();
        double totalSchedulingMs = (t11 - t10) / 1_000_000.0;

        return new BenchmarkResult(
                n, floydBuildMs, sequentialInsertMs, avgInsertUs,
                avgSearchUs, avgUpdateUs, avgExtractUs, totalSchedulingMs
        );
    }

    private static void printResultsSummary(List<BenchmarkResult> results) {
        System.out.println("\n------------------------------------------------------------------------------------------------------------------");
        System.out.println("                                      EMPIRICAL BENCHMARK MEASUREMENTS                                            ");
        System.out.println("------------------------------------------------------------------------------------------------------------------");
        System.out.printf("| %-8s | %-14s | %-16s | %-12s | %-12s | %-14s | %-14s | %-16s |\n",
                "Items (N)", "Floyd Build", "Seq Insert", "Avg Insert", "Avg Search", "Avg Update", "Avg ExtractMax", "Full Schedule");
        System.out.println("|----------|----------------|------------------|--------------|--------------|----------------|----------------|------------------|");

        for (BenchmarkResult r : results) {
            System.out.printf("| %8d | %11.3f ms | %13.3f ms | %9.3f µs | %9.3f µs | %11.3f µs | %11.3f µs | %13.3f ms |\n",
                    r.size(),
                    r.floydBuildMs(),
                    r.sequentialInsertMs(),
                    r.avgInsertUs(),
                    r.avgSearchUs(),
                    r.avgUpdatePriorityUs(),
                    r.avgExtractMaxUs(),
                    r.totalSchedulingMs());
        }
        System.out.println("------------------------------------------------------------------------------------------------------------------");
    }

    private static void printComplexityComparisonTable() {
        System.out.println("\n==================================================================================================================");
        System.out.println("                               THEORETICAL COMPLEXITY VS EMPIRICAL ARCHITECTURE                                   ");
        System.out.println("==================================================================================================================");
        System.out.printf("| %-22s | %-24s | %-48s |\n",
                "Operation", "Expected Complexity", "Observed Architectural Characteristics");
        System.out.println("|------------------------|--------------------------|--------------------------------------------------|");
        System.out.printf("| %-22s | %-24s | %-48s |\n", "Insert", "O(log4 n)", "Single sift-up path; 50% fewer tree levels than binary");
        System.out.printf("| %-22s | %-24s | %-48s |\n", "Peek Max", "O(1)", "Immediate access to array index 0 root element");
        System.out.printf("| %-22s | %-24s | %-48s |\n", "Extract Max", "O(4 log4 n) comparisons", "Compares all 4 children per level during sift-down");
        System.out.printf("| %-22s | %-24s | %-48s |\n", "Search by Medicine ID", "O(1) average", "Direct lookup via backing HashMap<String, Integer>");
        System.out.printf("| %-22s | %-24s | %-48s |\n", "Update Priority", "O(log4 n)", "O(1) index map lookup followed by sift-up or sift-down");
        System.out.printf("| %-22s | %-24s | %-48s |\n", "Delete by ID", "O(log4 n)", "O(1) lookup + swap with last item + sift-up/down");
        System.out.printf("| %-22s | %-24s | %-48s |\n", "Heap Construction (Floyd)", "O(n)", "Bottom-up siftDown from parent of last element to root");
        System.out.println("==================================================================================================================");
    }
}
