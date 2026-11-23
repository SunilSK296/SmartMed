package scheduler;

import heap.HeapStats;
import heap.Indexed4AryMaxHeap;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Restock Priority Scheduler facade wrapping the {@link Indexed4AryMaxHeap}.
 * Coordinates task insertions, priority recalculations, emergency overrides,
 * extractions, deletions, and structural heap snapshots.
 */
public class RestockPriorityScheduler {

    private static final int MAX_LOG = 200;

    private final Indexed4AryMaxHeap<RestockTaskNode> heap = new Indexed4AryMaxHeap<>();
    private final Deque<HeapOperation> log = new ArrayDeque<>();
    private final AtomicLong seq = new AtomicLong();
    private final Clock clock;

    public RestockPriorityScheduler(Clock clock) {
        this.clock = clock;
    }

    public RestockPriorityScheduler() {
        this(Clock.systemUTC());
    }

    public void setStepLogger(Consumer<String> logger) {
        heap.setStepLogger(logger);
    }

    public Indexed4AryMaxHeap<RestockTaskNode> getHeap() {
        return heap;
    }

    // ------------------------------------------------------------------ Scheduler Operations

    /**
     * Inserts a restock task into the priority queue. O(log4 n).
     */
    public synchronized void insert(RestockTaskNode node) {
        heap.insert(node);
        record("INSERT", node, null, node.getPriority(), "Computed score: " + node.getComputedScore());
    }

    /**
     * Removes and returns the highest-priority restock task from the root. O(4 log4 n).
     */
    public synchronized Optional<RestockTaskNode> extractHighest() {
        if (heap.isEmpty()) {
            return Optional.empty();
        }
        RestockTaskNode max = heap.extractMax();
        record("EXTRACT_MAX", max, max.getPriority(), null, "Served highest priority task");
        return Optional.of(max);
    }

    /**
     * Returns the highest-priority task without removing it. O(1).
     */
    public synchronized Optional<RestockTaskNode> peekHighest() {
        return heap.peekMax();
    }

    /**
     * Recalculates priority of a task (e.g. following stock or consumption change). O(log4 n).
     */
    public synchronized Optional<RestockTaskNode> recalculate(String taskId, double newComputed, PriorityBreakdown breakdown) {
        Optional<RestockTaskNode> opt = heap.getTask(taskId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        RestockTaskNode n = opt.get();
        double before = n.getPriority();
        n.setComputed(newComputed, breakdown);
        double after = n.effective();
        heap.updatePriority(taskId, after);
        record("UPDATE_PRIORITY", n, before, after, "Recalculated score: " + newComputed);
        return Optional.of(n);
    }

    /**
     * Updates the priority score directly. O(log4 n).
     */
    public synchronized Optional<RestockTaskNode> updatePriority(String taskId, double newPriority) {
        Optional<RestockTaskNode> opt = heap.getTask(taskId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        RestockTaskNode n = opt.get();
        double before = n.getPriority();
        n.setPriority(newPriority);
        heap.updatePriority(taskId, newPriority);
        record("MANUAL_PRIORITY_UPDATE", n, before, newPriority, "Manual update");
        return Optional.of(n);
    }

    /**
     * Marks a task as an emergency with a manual priority override. O(log4 n).
     */
    public synchronized Optional<RestockTaskNode> markEmergency(String taskId, double overrideScore, String reason, String by) {
        Optional<RestockTaskNode> opt = heap.getTask(taskId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        RestockTaskNode n = opt.get();
        double before = n.getPriority();
        n.setEmergency(true, overrideScore, reason, by);
        double after = n.effective();
        if (after >= before) {
            heap.increaseKey(taskId, after);
        } else {
            heap.updatePriority(taskId, after);
        }
        record("EMERGENCY_OVERRIDE", n, before, after, "By: " + by + ", Reason: " + reason);
        return Optional.of(n);
    }

    /**
     * Removes an arbitrary task by ID. O(log4 n).
     */
    public synchronized Optional<RestockTaskNode> remove(String taskId, String reason) {
        if (!heap.contains(taskId)) {
            return Optional.empty();
        }
        RestockTaskNode n = heap.delete(taskId);
        record("DELETE", n, n.getPriority(), null, reason);
        return Optional.of(n);
    }

    public synchronized Optional<RestockTaskNode> get(String taskId) {
        return heap.getTask(taskId);
    }

    public synchronized boolean contains(String taskId) {
        return heap.contains(taskId);
    }

    public synchronized int size() {
        return heap.size();
    }

    public synchronized boolean isEmpty() {
        return heap.isEmpty();
    }

    public synchronized void clear() {
        heap.clear();
        log.clear();
    }

    /**
     * Rebuilds the heap from a collection of tasks using Floyd's algorithm. O(n).
     */
    public synchronized void rebuild(Collection<RestockTaskNode> nodes) {
        for (RestockTaskNode n : nodes) {
            n.setPriority(n.effective());
        }
        heap.rebuildFrom(nodes);
        record("REBUILD", null, null, null, "Rebuilt from " + nodes.size() + " tasks");
    }

    /**
     * Returns all tasks in descending priority order. O(n log4 n).
     */
    public synchronized List<RestockTaskNode> sorted() {
        return heap.sortedDescending();
    }

    public synchronized HeapStats stats() {
        return heap.stats();
    }

    /**
     * Creates a structured snapshot of the 4-ary heap tree.
     */
    public synchronized HeapSnapshot snapshot() {
        List<RestockTaskNode> arr = heap.toList();
        int n = arr.size();
        List<HeapSnapshot.SnapshotNode> nodes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int p = i == 0 ? -1 : (i - 1) / 4;
            int c1 = 4 * i + 1;
            int c2 = 4 * i + 2;
            int c3 = 4 * i + 3;
            int c4 = 4 * i + 4;
            List<Integer> children = new ArrayList<>(4);
            if (c1 < n) children.add(c1);
            if (c2 < n) children.add(c2);
            if (c3 < n) children.add(c3);
            if (c4 < n) children.add(c4);
            nodes.add(new HeapSnapshot.SnapshotNode(i, p,
                    c1 < n ? c1 : -1, c2 < n ? c2 : -1, c3 < n ? c3 : -1, c4 < n ? c4 : -1,
                    children, arr.get(i)));
        }
        return new HeapSnapshot(n, nodes, n == 0 ? null : arr.get(0), heap.validateHeap(),
                heap.isIndexConsistent(), heap.stats(), new ArrayList<>(log));
    }

    private void record(String type, RestockTaskNode n, Double before, Double after, String detail) {
        HeapOperation op = new HeapOperation(seq.incrementAndGet(), Instant.now(clock), type,
                n == null ? null : n.getId(), n == null ? null : n.getMedicineName(), before, after,
                heap.size(), detail);
        log.addFirst(op);
        while (log.size() > MAX_LOG) {
            log.removeLast();
        }
    }
}
