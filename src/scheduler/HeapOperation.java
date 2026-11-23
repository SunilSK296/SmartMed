package scheduler;

import java.time.Instant;

/**
 * Audit record of an operation performed on the scheduler's heap.
 */
public record HeapOperation(long sequence, Instant timestamp, String type, String taskId,
                            String medicineName, Double priorityBefore, Double priorityAfter,
                            int heapSizeAfter, String detail) {
    @Override
    public String toString() {
        return String.format("#%d [%s] %s - Task: %s (%s) | Priority: %s -> %s | HeapSize: %d | %s",
                sequence, timestamp, type, taskId, medicineName,
                priorityBefore == null ? "N/A" : String.format("%.2f", priorityBefore),
                priorityAfter == null ? "N/A" : String.format("%.2f", priorityAfter),
                heapSizeAfter, detail);
    }
}
