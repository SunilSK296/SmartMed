package input;

import models.BatchExpiry;
import models.Criticality;
import models.Medicine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Handles medicine data input from both JSON files and interactive console prompt.
 * Contains a lightweight, zero-dependency JSON parser to guarantee standalone execution
 * without requiring external JARs or dependencies.
 */
public class InputHandler {

    private final Scanner scanner;

    public InputHandler(Scanner scanner) {
        this.scanner = scanner;
    }

    public InputHandler() {
        this(new Scanner(System.in));
    }

    // ================================================================== JSON Input

    /**
     * Loads medicines from a JSON file.
     *
     * @param filePath Path to the JSON file
     * @return List of parsed medicines
     * @throws IOException if file cannot be read
     */
    public List<Medicine> loadFromJson(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + filePath);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim());
            }
        }

        String json = sb.toString();
        return parseMedicinesJson(json);
    }

    /**
     * Simple, robust JSON parser tailored for medicine records.
     */
    private List<Medicine> parseMedicinesJson(String json) {
        List<Medicine> medicines = new ArrayList<>();

        // Locate "medicines" array
        int arrayStart = json.indexOf('[');
        int arrayEnd = json.lastIndexOf(']');
        if (arrayStart == -1 || arrayEnd == -1 || arrayEnd <= arrayStart) {
            return medicines;
        }

        String arrayContent = json.substring(arrayStart + 1, arrayEnd).trim();
        List<String> objectBlocks = splitJsonObjects(arrayContent);

        for (String block : objectBlocks) {
            try {
                Medicine m = parseMedicineObject(block);
                if (m != null) {
                    medicines.add(m);
                }
            } catch (Exception e) {
                System.err.println("Warning: failed to parse medicine block: " + e.getMessage());
            }
        }

        return medicines;
    }

    private List<String> splitJsonObjects(String content) {
        List<String> objects = new ArrayList<>();
        int braceDepth = 0;
        int start = -1;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                if (braceDepth == 0) {
                    start = i;
                }
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0 && start != -1) {
                    objects.add(content.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    private Medicine parseMedicineObject(String objStr) {
        String id = extractStringField(objStr, "medicineId");
        String name = extractStringField(objStr, "medicineName");
        long currentStock = extractLongField(objStr, "currentStock", 0);
        long minimumStock = extractLongField(objStr, "minimumStock", 0);
        long criticalLevel = extractLongField(objStr, "criticalLevel", minimumStock / 2);
        double consumption = extractDoubleField(objStr, "dailyConsumption", 0.0);
        String critStr = extractStringField(objStr, "criticality");
        Criticality criticality = Criticality.parse(critStr);
        int leadTime = (int) extractLongField(objStr, "supplierLeadTime", 0);

        List<BatchExpiry> batches = parseBatches(objStr);

        return new Medicine(id, name, currentStock, minimumStock, criticalLevel, consumption, criticality, batches, leadTime);
    }

    private List<BatchExpiry> parseBatches(String objStr) {
        List<BatchExpiry> batches = new ArrayList<>();
        int batchKey = objStr.indexOf("\"batches\"");
        if (batchKey == -1) {
            // Check single expiryDate field as fallback
            String singleExp = extractStringField(objStr, "expiryDate");
            if (singleExp != null && !singleExp.isBlank()) {
                // If simple days or YYYY-MM-DD
                try {
                    long days = Long.parseLong(singleExp.trim());
                    batches.add(new BatchExpiry(extractLongField(objStr, "currentStock", 0), days));
                } catch (NumberFormatException ignored) {
                }
            }
            return batches;
        }

        int start = objStr.indexOf('[', batchKey);
        int end = objStr.indexOf(']', start);
        if (start != -1 && end != -1) {
            String batchArr = objStr.substring(start + 1, end).trim();
            List<String> batchObjs = splitJsonObjects(batchArr);
            for (String bo : batchObjs) {
                long qty = extractLongField(bo, "quantity", 0);
                long days = extractLongField(bo, "daysToExpiry", 90);
                batches.add(new BatchExpiry(qty, days));
            }
        }
        return batches;
    }

    private String extractStringField(String obj, String field) {
        String search = "\"" + field + "\"";
        int idx = obj.indexOf(search);
        if (idx == -1) return null;

        int colon = obj.indexOf(':', idx + search.length());
        if (colon == -1) return null;

        int firstQuote = obj.indexOf('"', colon);
        if (firstQuote == -1) return null;

        int secondQuote = obj.indexOf('"', firstQuote + 1);
        if (secondQuote == -1) return null;

        return obj.substring(firstQuote + 1, secondQuote);
    }

    private long extractLongField(String obj, String field, long def) {
        String search = "\"" + field + "\"";
        int idx = obj.indexOf(search);
        if (idx == -1) return def;

        int colon = obj.indexOf(':', idx + search.length());
        if (colon == -1) return def;

        int i = colon + 1;
        while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) i++;
        int start = i;
        while (i < obj.length() && (Character.isDigit(obj.charAt(i)) || obj.charAt(i) == '-')) i++;
        if (start == i) return def;

        try {
            return Long.parseLong(obj.substring(start, i));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private double extractDoubleField(String obj, String field, double def) {
        String search = "\"" + field + "\"";
        int idx = obj.indexOf(search);
        if (idx == -1) return def;

        int colon = obj.indexOf(':', idx + search.length());
        if (colon == -1) return def;

        int i = colon + 1;
        while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) i++;
        int start = i;
        while (i < obj.length() && (Character.isDigit(obj.charAt(i)) || obj.charAt(i) == '.' || obj.charAt(i) == '-')) i++;
        if (start == i) return def;

        try {
            return Double.parseDouble(obj.substring(start, i));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ================================================================== Console Input

    /**
     * Prompts the user to enter a medicine manually with rigorous input validation.
     *
     * @return Validated Medicine instance
     */
    public Medicine promptMedicine() {
        System.out.println("\n--------------------------------------------------");
        System.out.println("          MANUAL MEDICINE ENTRY WIZARD            ");
        System.out.println("--------------------------------------------------");

        String id = promptNonBlank("Enter Medicine ID (e.g., MED001): ");
        String name = promptNonBlank("Enter Medicine Name (e.g., Paracetamol 500mg): ");
        long currentStock = promptLong("Enter Current Stock on hand (e.g., 20): ", 0, Long.MAX_VALUE);
        long minimumStock = promptLong("Enter Minimum Stock / Reorder Threshold (e.g., 100): ", 1, Long.MAX_VALUE);
        double consumption = promptDouble("Enter Daily Consumption Rate (e.g., 15.0): ", 0.0, 100000.0);
        Criticality criticality = promptCriticality();
        int leadTime = (int) promptLong("Enter Supplier Lead Time in days [optional, 0 for default]: ", 0, 365);

        Medicine medicine = new Medicine(id, name, currentStock, minimumStock, consumption, criticality);
        medicine.setSupplierLeadTime(leadTime);

        System.out.print("Do you want to enter stock batch expiry information? (y/n) [n]: ");
        String batchChoice = scanner.nextLine().trim();
        if (batchChoice.equalsIgnoreCase("y") || batchChoice.equalsIgnoreCase("yes")) {
            promptBatches(medicine);
        }

        System.out.println("Medicine successfully created: " + medicine.getMedicineName() + " [" + medicine.getMedicineId() + "]");
        return medicine;
    }

    private void promptBatches(Medicine medicine) {
        long batchCount = promptLong("How many stock batches? ", 1, 10);
        for (int b = 1; b <= batchCount; b++) {
            System.out.printf("  Batch #%d:\n", b);
            long qty = promptLong("    Quantity: ", 1, Long.MAX_VALUE);
            long days = promptLong("    Days until expiry (can be negative if expired): ", -3650, 3650);
            medicine.addBatch(new BatchExpiry(qty, days));
        }
    }

    private String promptNonBlank(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("  [Error] Value cannot be blank. Please try again.");
        }
    }

    private long promptLong(String prompt, long min, long max) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                long val = Long.parseLong(input);
                if (val >= min && val <= max) {
                    return val;
                }
                System.out.printf("  [Error] Value must be between %d and %d. Please try again.\n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("  [Error] Invalid integer number. Please try again.");
            }
        }
    }

    private double promptDouble(String prompt, double min, double max) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                double val = Double.parseDouble(input);
                if (val >= min && val <= max) {
                    return val;
                }
                System.out.printf("  [Error] Value must be between %.2f and %.2f. Please try again.\n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("  [Error] Invalid decimal number. Please try again.");
            }
        }
    }

    private Criticality promptCriticality() {
        while (true) {
            System.out.print("Enter Criticality (LOW, MEDIUM, HIGH, CRITICAL or numeric 1-10) [MEDIUM]: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return Criticality.MEDIUM;
            }
            Criticality c = Criticality.parse(input);
            if (c != null) {
                return c;
            }
            System.out.println("  [Error] Unrecognized criticality. Valid options: LOW, MEDIUM, HIGH, CRITICAL.");
        }
    }
}
