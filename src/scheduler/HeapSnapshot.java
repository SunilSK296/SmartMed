package scheduler;

import heap.HeapStats;
import java.util.List;

/**
 * Point-in-time visual snapshot of the Indexed 4-ary Max Heap structure.
 */
public record HeapSnapshot(int size, List<SnapshotNode> nodes, RestockTaskNode highest,
                           boolean validHeap, boolean indexConsistent, HeapStats stats,
                           List<HeapOperation> recentOperations) {

    public record SnapshotNode(int index, int parent, int child1, int child2, int child3, int child4,
                               List<Integer> children, RestockTaskNode task) {
    }
}
