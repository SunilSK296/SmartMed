package heap;

/**
 * Interface for elements that can be stored in the {@link Indexed4AryMaxHeap}.
 * Every item must provide a unique identifier for the hash map index,
 * a priority score used for heap ordering, and a creation timestamp for tie-breaking.
 */
public interface HeapItem {

    /** Unique identifier of the item (the key of the heap's index map). */
    String getId();

    /** Current priority score. Higher values are served first. */
    double getPriority();

    /** Sets the priority score. */
    void setPriority(double priority);

    /** Creation time in epoch milliseconds. Used as a tie-breaker (older first). */
    long getCreatedAtMillis();
}
