package heap;

/**
 * Immutable metrics tracking execution counters for the Indexed 4-ary Max Heap.
 */
public record HeapStats(long comparisons, long swaps, long inserts, long extracts, long updates, long deletes) {
    @Override
    public String toString() {
        return String.format("HeapStats[comparisons=%d, swaps=%d, inserts=%d, extracts=%d, updates=%d, deletes=%d]",
                comparisons, swaps, inserts, extracts, updates, deletes);
    }
}
