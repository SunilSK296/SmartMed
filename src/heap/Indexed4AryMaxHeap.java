package heap;

import java.util.*;
import java.util.function.Consumer;

/**
 * Custom <b>Indexed 4-ary Max Heap</b> (4-ary Indexed Priority Queue).
 *
 * <p>Data structure properties:</p>
 * <ul>
 *   <li>Dynamic array ({@link ArrayList}) for tree storage in level order.</li>
 *   <li>Hash map ({@link HashMap}) mapping medicine/item ID to its current index in the array for O(1) average lookup.</li>
 *   <li>Branching factor d = 4 (four children per parent node).</li>
 *   <li>Max Heap property: Parent priority >= each child priority.</li>
 *   <li>Deterministic tie-breaking: Higher priority first; ties broken by older creation timestamp, then ID lexicographically.</li>
 * </ul>
 *
 * <p>Zero-based 4-ary indexing formulas for node at index {@code i}:</p>
 * <pre>
 *   parent = (i - 1) / 4      (for i > 0; root 0 has parent -1)
 *   child1 = 4 * i + 1
 *   child2 = 4 * i + 2
 *   child3 = 4 * i + 3
 *   child4 = 4 * i + 4
 * </pre>
 *
 * <p>Complexity Guarantees:</p>
 * <table border="1">
 *   <tr><th>Operation</th><th>Expected Complexity</th></tr>
 *   <tr><td>insert</td><td>O(log4 n)</td></tr>
 *   <tr><td>peekMax</td><td>O(1)</td></tr>
 *   <tr><td>extractMax</td><td>O(4 log4 n) comparisons</td></tr>
 *   <tr><td>findById / search</td><td>O(1) average</td></tr>
 *   <tr><td>updatePriority</td><td>O(log4 n)</td></tr>
 *   <tr><td>deleteById</td><td>O(log4 n)</td></tr>
 *   <tr><td>rebuildHeap (Floyd)</td><td>O(n)</td></tr>
 * </table>
 *
 * @param <T> Element type extending {@link HeapItem}
 */
public class Indexed4AryMaxHeap<T extends HeapItem> {

    public static final int BRANCHING_FACTOR = 4;

    private final List<T> heap = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();

    // Performance & operation counters
    private long comparisons;
    private long swaps;
    private long inserts;
    private long extracts;
    private long updates;
    private long deletes;

    // Optional trace listener for interactive step-by-step demonstration
    private Consumer<String> stepLogger = null;

    public Indexed4AryMaxHeap() {
    }

    public void setStepLogger(Consumer<String> logger) {
        this.stepLogger = logger;
    }

    private void trace(String message) {
        if (stepLogger != null) {
            stepLogger.accept(message);
        }
    }

    // ------------------------------------------------------------------ Index Formulas

    /**
     * Parent index in a 0-based 4-ary heap: {@code (i - 1) / 4}, or -1 for the root.
     */
    public static int parentOf(int i) {
        return i <= 0 ? -1 : (i - 1) / BRANCHING_FACTOR;
    }

    /** 1st child index in a 0-based 4-ary heap: {@code 4 * i + 1}. */
    public static int child1Of(int i) {
        return BRANCHING_FACTOR * i + 1;
    }

    /** 2nd child index in a 0-based 4-ary heap: {@code 4 * i + 2}. */
    public static int child2Of(int i) {
        return BRANCHING_FACTOR * i + 2;
    }

    /** 3rd child index in a 0-based 4-ary heap: {@code 4 * i + 3}. */
    public static int child3Of(int i) {
        return BRANCHING_FACTOR * i + 3;
    }

    /** 4th child index in a 0-based 4-ary heap: {@code 4 * i + 4}. */
    public static int child4Of(int i) {
        return BRANCHING_FACTOR * i + 4;
    }

    /** All four child indices for node {@code i}. */
    public static int[] childrenOf(int i) {
        return new int[]{
                BRANCHING_FACTOR * i + 1,
                BRANCHING_FACTOR * i + 2,
                BRANCHING_FACTOR * i + 3,
                BRANCHING_FACTOR * i + 4
        };
    }

    // ------------------------------------------------------------------ Core Heap Operations

    /**
     * Inserts a new medicine item into the heap. O(log4 n).
     * The item is appended to the dynamic array, indexed in the hash map,
     * and sifted up to its correct heap position.
     *
     * @param item Item to insert
     * @throws IllegalArgumentException if item is null or duplicate ID exists
     */
    public void insert(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null");
        }
        if (index.containsKey(item.getId())) {
            throw new IllegalArgumentException("Duplicate medicine ID in heap: " + item.getId());
        }

        trace(String.format("--> Inserting '%s' (Priority: %.2f) at array index %d",
                item.getId(), item.getPriority(), heap.size()));

        heap.add(item);
        int i = heap.size() - 1;
        index.put(item.getId(), i);

        trace(String.format("    HashMap updated: index['%s'] = %d", item.getId(), i));

        int finalIndex = siftUp(i);
        inserts++;

        trace(String.format("<-- Insert completed for '%s' at final index %d (Total items: %d)",
                item.getId(), finalIndex, heap.size()));
    }

    /**
     * Returns the highest-priority item at the root without removing it. O(1).
     *
     * @return Optional containing root item, or empty if heap is empty
     */
    public Optional<T> peekMax() {
        return heap.isEmpty() ? Optional.empty() : Optional.of(heap.get(0));
    }

    /**
     * Removes and returns the highest-priority medicine at the root (index 0). O(4 log4 n).
     * The root is replaced by the last element in the dynamic array, and sifted down
     * across all 4 children until the Max Heap property is restored.
     *
     * @return Item with highest priority score
     * @throws NoSuchElementException if heap is empty
     */
    public T extractMax() {
        if (heap.isEmpty()) {
            throw new NoSuchElementException("Heap is empty; cannot extract maximum");
        }

        T max = heap.get(0);
        trace(String.format("--> Extracting Max '%s' (Priority: %.2f) from root (index 0)",
                max.getId(), max.getPriority()));

        removeAt(0);
        extracts++;

        trace(String.format("<-- Extract Max completed. Removed '%s'. Remaining items: %d",
                max.getId(), heap.size()));
        return max;
    }

    /**
     * Updates the priority score of an existing medicine by ID and repositions it. O(log4 n).
     * If priority increases, it sifts up; if priority decreases, it sifts down.
     *
     * @param id          Medicine ID
     * @param newPriority New priority score
     * @throws NoSuchElementException if medicine ID is not found in heap
     */
    public void updatePriority(String id, double newPriority) {
        int i = indexOf(id);
        T item = heap.get(i);
        double oldPriority = item.getPriority();

        trace(String.format("--> Updating priority for '%s' at index %d from %.2f to %.2f",
                id, i, oldPriority, newPriority));

        item.setPriority(newPriority);

        // Reposition: try siftUp first; if position unchanged, try siftDown
        int after = siftUp(i);
        if (after == i) {
            siftDown(i);
        }
        updates++;

        trace(String.format("<-- Priority update completed for '%s'. New index: %d",
                id, indexOf(id)));
    }

    /**
     * Increases the priority of a medicine (increaseKey). O(log4 n).
     *
     * @param id          Medicine ID
     * @param newPriority New priority (must be >= current priority)
     * @throws IllegalArgumentException if newPriority < current
     * @throws NoSuchElementException   if id is not found
     */
    public void increaseKey(String id, double newPriority) {
        int i = indexOf(id);
        T item = heap.get(i);
        if (newPriority < item.getPriority()) {
            throw new IllegalArgumentException(String.format(
                    "increaseKey requires newPriority >= current (new: %.2f < current: %.2f)",
                    newPriority, item.getPriority()));
        }
        item.setPriority(newPriority);
        siftUp(i);
        updates++;
    }

    /**
     * Removes an arbitrary medicine by ID from the heap and returns it. O(log4 n).
     *
     * @param id Medicine ID to delete
     * @return The removed item
     * @throws NoSuchElementException if id is not found
     */
    public T deleteById(String id) {
        int i = indexOf(id);
        T item = heap.get(i);

        trace(String.format("--> Deleting medicine '%s' (Priority: %.2f) at index %d",
                id, item.getPriority(), i));

        removeAt(i);
        deletes++;

        trace(String.format("<-- Deletion completed for '%s'. Remaining items: %d",
                id, heap.size()));
        return item;
    }

    /** Alias for deleteById. */
    public T delete(String id) {
        return deleteById(id);
    }

    /**
     * Finds an item by ID in O(1) average time using the hash map.
     *
     * @param id Medicine ID
     * @return Optional containing item, or empty if not found
     */
    public Optional<T> findById(String id) {
        Integer i = index.get(id);
        return i == null ? Optional.empty() : Optional.of(heap.get(i));
    }

    /** Alias for findById. */
    public Optional<T> getTask(String id) {
        return findById(id);
    }

    public boolean contains(String id) {
        return index.containsKey(id);
    }

    public int indexOfId(String id) {
        Integer i = index.get(id);
        return i == null ? -1 : i;
    }

    public int size() {
        return heap.size();
    }

    public boolean isEmpty() {
        return heap.isEmpty();
    }

    public void clear() {
        heap.clear();
        index.clear();
    }

    public List<T> toList() {
        return Collections.unmodifiableList(new ArrayList<>(heap));
    }

    public HeapStats stats() {
        return new HeapStats(comparisons, swaps, inserts, extracts, updates, deletes);
    }

    // ------------------------------------------------------------------ Sift Up & Sift Down

    /**
     * Sifts up the element at index {@code i} while its priority outranks its 4-ary parent.
     * Parent index = (i - 1) / 4.
     *
     * @param i Starting array index
     * @return Final array index of the element
     */
    public int siftUp(int i) {
        while (i > 0) {
            int p = parentOf(i);
            comparisons++;
            trace(String.format("    siftUp comparison: child[%d] '%s' (p=%.2f) vs parent[%d] '%s' (p=%.2f)",
                    i, heap.get(i).getId(), heap.get(i).getPriority(),
                    p, heap.get(p).getId(), heap.get(p).getPriority()));

            if (ranksBelow(heap.get(p), heap.get(i))) {
                trace(String.format("    --> Child outranks parent! Swapping index %d and %d", i, p));
                swap(p, i);
                i = p;
            } else {
                trace(String.format("    --> Heap property satisfied at index %d (parent >= child)", i));
                break;
            }
        }
        return i;
    }

    /**
     * Sifts down the element at index {@code i} by evaluating all four possible children:
     * child1 = 4*i + 1, child2 = 4*i + 2, child3 = 4*i + 3, child4 = 4*i + 4.
     * Finds the largest among existing children; if that child outranks the current node,
     * swaps and continues sifting down.
     *
     * @param i Starting array index
     */
    public void siftDown(int i) {
        int n = heap.size();
        while (true) {
            int largest = i;
            int firstChild = BRANCHING_FACTOR * i + 1;
            int lastChild = Math.min(n, BRANCHING_FACTOR * i + 5);

            if (firstChild >= n) {
                // Leaf node; no children exist
                break;
            }

            trace(String.format("    siftDown at index %d '%s' (p=%.2f): evaluating children in [%d..%d)",
                    i, heap.get(i).getId(), heap.get(i).getPriority(), firstChild, lastChild));

            for (int c = firstChild; c < lastChild; c++) {
                comparisons++;
                trace(String.format("      comparing child[%d] '%s' (p=%.2f) with current largest[%d] '%s' (p=%.2f)",
                        c, heap.get(c).getId(), heap.get(c).getPriority(),
                        largest, heap.get(largest).getId(), heap.get(largest).getPriority()));

                if (ranksBelow(heap.get(largest), heap.get(c))) {
                    largest = c;
                }
            }

            if (largest == i) {
                trace(String.format("    --> Node at index %d is >= all its 4 children. SiftDown complete.", i));
                return;
            }

            trace(String.format("    --> Largest child is at index %d '%s' (p=%.2f). Swapping with index %d",
                    largest, heap.get(largest).getId(), heap.get(largest).getPriority(), i));
            swap(i, largest);
            i = largest;
        }
    }

    /**
     * Centralized swap operation:
     * 1. Exchanges elements in the dynamic array (heap).
     * 2. Updates the first item's index in the hash map.
     * 3. Updates the second item's index in the hash map.
     * Guarantees that array and index map are always perfectly synchronized.
     */
    public void swap(int a, int b) {
        if (a == b) {
            return;
        }
        T ta = heap.get(a);
        T tb = heap.get(b);

        heap.set(a, tb);
        heap.set(b, ta);

        index.put(tb.getId(), a);
        index.put(ta.getId(), b);

        swaps++;
        trace(String.format("      [SWAP] array[%d] <-> array[%d] | map['%s']=%d, map['%s']=%d",
                a, b, tb.getId(), a, ta.getId(), b));
    }

    // ------------------------------------------------------------------ Internal Removal & Floyd Rebuild

    private void removeAt(int i) {
        int last = heap.size() - 1;
        T removed = heap.get(i);

        if (i != last) {
            swap(i, last);
        }

        heap.remove(last);
        index.remove(removed.getId());

        if (i < heap.size()) {
            // The item swapped into slot i may belong above or below
            int after = siftUp(i);
            if (after == i) {
                siftDown(i);
            }
        }
    }

    private int indexOf(String id) {
        Integer i = index.get(id);
        if (i == null) {
            throw new NoSuchElementException("Unknown medicine ID in heap: " + id);
        }
        return i;
    }

    /**
     * Rebuilds the 4-ary Max Heap bottom-up in O(n) using Floyd's algorithm.
     */
    public void rebuildHeap() {
        index.clear();
        for (int i = 0; i < heap.size(); i++) {
            index.put(heap.get(i).getId(), i);
        }
        if (heap.size() <= 1) {
            return;
        }
        for (int i = parentOf(heap.size() - 1); i >= 0; i--) {
            siftDown(i);
        }
    }

    /**
     * Replaces heap content with given collection and builds 4-ary heap in O(n).
     */
    public void rebuildFrom(Collection<? extends T> items) {
        heap.clear();
        index.clear();
        for (T item : items) {
            if (index.containsKey(item.getId())) {
                throw new IllegalArgumentException("Duplicate ID in collection: " + item.getId());
            }
            index.put(item.getId(), heap.size());
            heap.add(item);
        }
        rebuildHeap();
    }

    /**
     * Returns all items in descending priority order without modifying this heap. O(n log4 n).
     */
    public List<T> sortedDescending() {
        Indexed4AryMaxHeap<T> copy = new Indexed4AryMaxHeap<>();
        copy.heap.addAll(heap);
        for (int i = 0; i < copy.heap.size(); i++) {
            copy.index.put(copy.heap.get(i).getId(), i);
        }
        List<T> out = new ArrayList<>(heap.size());
        while (!copy.isEmpty()) {
            out.add(copy.extractMax());
        }
        return out;
    }

    // ------------------------------------------------------------------ Invariant Validation

    /**
     * True if every node's parent has priority >= child (Max Heap property).
     */
    public boolean validateHeap() {
        for (int i = 1; i < heap.size(); i++) {
            int p = parentOf(i);
            if (ranksBelow(heap.get(p), heap.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if the hash map is an exact bidirectional inverse of the array.
     */
    public boolean isIndexConsistent() {
        if (index.size() != heap.size()) {
            return false;
        }
        for (int i = 0; i < heap.size(); i++) {
            Integer mapped = index.get(heap.get(i).getId());
            if (mapped == null || mapped != i) {
                return false;
            }
        }
        return true;
    }

    /**
     * Comprehensive 4-invariant validation:
     * 1. Max Heap ordering holds across all 4-ary parent-child pairs.
     * 2. Hash map size exactly matches dynamic array size.
     * 3. Every array element ID correctly maps to its exact array index.
     * 4. No duplicate IDs exist in the dynamic array.
     */
    public boolean validate() {
        if (!validateHeap()) {
            return false;
        }
        if (!isIndexConsistent()) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (T item : heap) {
            if (!seen.add(item.getId())) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ Ordering Helper

    /**
     * Total deterministic order used by the heap:
     * 1. Higher priority score first.
     * 2. Older creation time first (smaller epoch millis).
     * 3. Medicine ID lexicographically ascending.
     *
     * @return true if {@code a} ranks below {@code b} (i.e. {@code b} should be served before {@code a})
     */
    public boolean ranksBelow(T a, T b) {
        int c = Double.compare(a.getPriority(), b.getPriority());
        if (c != 0) {
            return c < 0; // a has lower priority -> ranks below b
        }
        c = Long.compare(a.getCreatedAtMillis(), b.getCreatedAtMillis());
        if (c != 0) {
            return c > 0; // a is newer than b -> ranks below b
        }
        return a.getId().compareTo(b.getId()) > 0;
    }
}
