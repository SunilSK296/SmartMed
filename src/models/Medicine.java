package models;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Medicine domain entity representing an item in the pharmacy inventory.
 * Contains inventory attributes, consumption parameters, criticality, batches,
 * and supplier lead time (preserved as an informative field).
 */
public class Medicine {

    private final String medicineId;
    private final String medicineName;
    private long currentStock;
    private long minimumStock; // Reorder level
    private long criticalLevel;
    private double dailyConsumption;
    private Criticality criticality;
    private final List<BatchExpiry> batches = new ArrayList<>();
    private int supplierLeadTime; // Preserved informative field

    public Medicine(String medicineId, String medicineName, long currentStock, long minimumStock,
                    long criticalLevel, double dailyConsumption, Criticality criticality,
                    List<BatchExpiry> batches, int supplierLeadTime) {
        if (medicineId == null || medicineId.isBlank()) {
            throw new IllegalArgumentException("Medicine ID cannot be null or empty");
        }
        this.medicineId = medicineId.trim();
        this.medicineName = medicineName == null || medicineName.isBlank() ? medicineId : medicineName.trim();
        this.currentStock = Math.max(0, currentStock);
        this.minimumStock = Math.max(0, minimumStock);
        this.criticalLevel = Math.max(0, criticalLevel);
        this.dailyConsumption = Math.max(0.0, dailyConsumption);
        this.criticality = criticality == null ? Criticality.MEDIUM : criticality;
        if (batches != null) {
            this.batches.addAll(batches);
        }
        this.supplierLeadTime = Math.max(0, supplierLeadTime);
    }

    public Medicine(String medicineId, String medicineName, long currentStock, long minimumStock,
                    double dailyConsumption, Criticality criticality) {
        this(medicineId, medicineName, currentStock, minimumStock, minimumStock / 2, dailyConsumption, criticality, null, 0);
    }

    // ------------------------------------------------------------------ Getters & Setters

    public String getMedicineId() {
        return medicineId;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public long getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(long currentStock) {
        this.currentStock = Math.max(0, currentStock);
    }

    public long getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(long minimumStock) {
        this.minimumStock = Math.max(0, minimumStock);
    }

    public long getCriticalLevel() {
        return criticalLevel;
    }

    public void setCriticalLevel(long criticalLevel) {
        this.criticalLevel = Math.max(0, criticalLevel);
    }

    public double getDailyConsumption() {
        return dailyConsumption;
    }

    public void setDailyConsumption(double dailyConsumption) {
        this.dailyConsumption = Math.max(0.0, dailyConsumption);
    }

    public Criticality getCriticality() {
        return criticality;
    }

    public void setCriticality(Criticality criticality) {
        this.criticality = criticality == null ? Criticality.MEDIUM : criticality;
    }

    public List<BatchExpiry> getBatches() {
        return Collections.unmodifiableList(batches);
    }

    public void addBatch(BatchExpiry batch) {
        if (batch != null) {
            batches.add(batch);
        }
    }

    public void addBatch(long quantity, LocalDate expiryDate) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        batches.add(new BatchExpiry(quantity, days));
    }

    public void clearBatches() {
        batches.clear();
    }

    public int getSupplierLeadTime() {
        return supplierLeadTime;
    }

    public void setSupplierLeadTime(int supplierLeadTime) {
        this.supplierLeadTime = Math.max(0, supplierLeadTime);
    }

    // ------------------------------------------------------------------ Business Logic Helpers

    /**
     * Usable stock represents unexpired inventory on hand.
     * If explicit batches are specified, returns the sum of unexpired batch quantities;
     * otherwise falls back to currentStock.
     */
    public long getUsableStock() {
        if (batches.isEmpty()) {
            return currentStock;
        }
        long usable = 0;
        for (BatchExpiry b : batches) {
            if (b.daysToExpiry() >= 0) {
                usable += b.quantity();
            }
        }
        return usable;
    }

    /**
     * Quantity of stock expiring within the 30-day window.
     */
    public long getExpiringSoonStock() {
        long soon = 0;
        for (BatchExpiry b : batches) {
            if (b.daysToExpiry() >= 0 && b.daysToExpiry() <= 30) {
                soon += b.quantity();
            }
        }
        return soon;
    }

    /**
     * Effective stock is usable stock minus stock expiring within 30 days.
     * This represents the reliable stock that will remain available over the upcoming month.
     */
    public long getEffectiveStock() {
        return Math.max(0, getUsableStock() - getExpiringSoonStock());
    }

    /**
     * Restocking condition per SmartMed business rule:
     * A restock is required when effective stock is at or below the minimum stock (reorderLevel).
     */
    public boolean requiresRestock() {
        return getEffectiveStock() <= minimumStock;
    }

    /**
     * Calculates suggested restock quantity per SmartMed formula:
     * targetStock = max(ceil(reorderLevel * multiplier), ceil(dailyConsumption * targetDaysOfCover))
     * suggestedQuantity = max(1, targetStock - effectiveStock)
     */
    public long calculateSuggestedQuantity(double targetStockMultiplier, double targetDaysOfCover) {
        double byLevel = Math.ceil(minimumStock * targetStockMultiplier);
        double byDemand = Math.ceil(dailyConsumption * targetDaysOfCover);
        long target = (long) Math.max(byLevel, byDemand);
        return Math.max(1L, target - getEffectiveStock());
    }

    public long calculateSuggestedQuantity() {
        // SmartMed default multiplier = 3.0, target days of cover = 30.0
        return calculateSuggestedQuantity(3.0, 30.0);
    }

    @Override
    public String toString() {
        return String.format("%s [%s] - Stock: %d (Min: %d, Daily: %.1f, Crit: %s, LeadTime: %dd)",
                medicineName, medicineId, currentStock, minimumStock, dailyConsumption, criticality, supplierLeadTime);
    }
}
