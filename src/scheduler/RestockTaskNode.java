package scheduler;

import heap.HeapItem;

/**
 * Restock task node stored directly in the Indexed 4-ary Max Heap.
 * Tracks the medicine, computed priority score, manual/emergency override,
 * priority breakdown, current stock, and suggested restocking quantity.
 */
public class RestockTaskNode implements HeapItem {

    private final String id;
    private final String medicineId;
    private final String medicineName;
    private final long createdAtMillis;
    private double priority; // Effective priority (taking emergency into account)
    private double computedScore;
    private boolean emergency;
    private Double manualOverride;
    private String emergencyReason;
    private String emergencyBy;
    private PriorityBreakdown breakdown;
    private long currentStock;
    private long reorderLevel;
    private long suggestedQuantity;

    public RestockTaskNode(String id, String medicineId, String medicineName, long createdAtMillis,
                           double computedScore, PriorityBreakdown breakdown,
                           long currentStock, long reorderLevel, long suggestedQuantity) {
        this.id = id;
        this.medicineId = medicineId;
        this.medicineName = medicineName;
        this.createdAtMillis = createdAtMillis;
        this.computedScore = computedScore;
        this.priority = computedScore;
        this.breakdown = breakdown;
        this.currentStock = currentStock;
        this.reorderLevel = reorderLevel;
        this.suggestedQuantity = suggestedQuantity;
    }

    public RestockTaskNode(String id, String medicineId, String medicineName, long createdAtMillis,
                           double computedScore, PriorityBreakdown breakdown) {
        this(id, medicineId, medicineName, createdAtMillis, computedScore, breakdown, 0, 0, 0);
    }

    // ------------------------------------------------------------------ HeapItem implementation

    @Override
    public String getId() {
        return id;
    }

    @Override
    public double getPriority() {
        return priority;
    }

    @Override
    public void setPriority(double priority) {
        this.priority = priority;
    }

    @Override
    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    // ------------------------------------------------------------------ Getters & Setters

    public String getMedicineId() {
        return medicineId;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public double getComputedScore() {
        return computedScore;
    }

    public boolean isEmergency() {
        return emergency;
    }

    public Double getManualOverride() {
        return manualOverride;
    }

    public String getEmergencyReason() {
        return emergencyReason;
    }

    public String getEmergencyBy() {
        return emergencyBy;
    }

    public PriorityBreakdown getBreakdown() {
        return breakdown;
    }

    public long getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(long currentStock) {
        this.currentStock = currentStock;
    }

    public long getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(long reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public long getSuggestedQuantity() {
        return suggestedQuantity;
    }

    public void setSuggestedQuantity(long suggestedQuantity) {
        this.suggestedQuantity = suggestedQuantity;
    }

    public void setComputed(double computedScore, PriorityBreakdown breakdown) {
        this.computedScore = computedScore;
        this.breakdown = breakdown;
        this.priority = effective();
    }

    public void setEmergency(boolean emergency, Double override, String reason, String by) {
        this.emergency = emergency;
        this.manualOverride = override;
        this.emergencyReason = reason;
        this.emergencyBy = by;
        this.priority = effective();
    }

    /**
     * Effective priority = manual override for emergencies (never below computed score).
     */
    public double effective() {
        return emergency && manualOverride != null ? Math.max(manualOverride, computedScore) : computedScore;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | Priority: %.2f | Stock: %d | Suggested: %d",
                id, medicineName, priority, currentStock, suggestedQuantity);
    }
}
