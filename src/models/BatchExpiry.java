package models;

/**
 * Quantity of a stock batch together with the number of days until it expires.
 * Negative days indicates an already expired batch.
 */
public record BatchExpiry(long quantity, long daysToExpiry) {
    public BatchExpiry {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative: " + quantity);
        }
    }
}
