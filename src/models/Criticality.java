package models;

/**
 * Clinical criticality of a medicine, mapped to a normalised [0, 1] factor.
 * Reused faithfully from the SmartMed core architecture:
 * - LOW: 0.25 (standard non-critical OTC / comfort medicines)
 * - MEDIUM: 0.50 (standard prescription therapeutic drugs)
 * - HIGH: 0.75 (urgent therapeutic drugs, antibiotics, antihypertensives)
 * - CRITICAL: 1.00 (life-saving drugs, ICU, insulin, epinephrine)
 */
public enum Criticality {
    LOW(0.25),
    MEDIUM(0.50),
    HIGH(0.75),
    CRITICAL(1.00);

    private final double factor;

    Criticality(double factor) {
        this.factor = factor;
    }

    public double factor() {
        return factor;
    }

    /**
     * Parses criticality from string (enum name or 1-10 numeric scale).
     */
    public static Criticality parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return MEDIUM;
        }
        String clean = input.trim().toUpperCase();
        try {
            return Criticality.valueOf(clean);
        } catch (IllegalArgumentException ignored) {
        }

        // Try numeric scale 1-10
        try {
            double val = Double.parseDouble(clean);
            if (val >= 9.0) return CRITICAL;
            if (val >= 7.0) return HIGH;
            if (val >= 4.0) return MEDIUM;
            return LOW;
        } catch (NumberFormatException ignored) {
        }

        return MEDIUM;
    }
}
