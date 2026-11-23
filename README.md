# SmartMed Standalone Priority Scheduling Engine

> An independent, production-faithful demonstration of the **Priority Scheduling Engine** and **Indexed 4-ary Max Heap** extracted from the SmartMed Hospital Pharmacy Management system.

---

## 1. Project Objective

The primary objective of this project is to provide a clean, standalone, and independently runnable implementation of the **SmartMed Priority Scheduling Engine**. 

It demonstrates the complete algorithmic pipeline:

$$\text{Inventory Input} \longrightarrow \text{Priority Calculation} \longrightarrow \text{Indexed 4-ary Max Heap} \longrightarrow \text{Priority-Based Scheduling} \longrightarrow \text{Restocking Recommendations} \longrightarrow \text{Ranked Output}$$

This standalone engine operates **100% independently** of:
- The SmartMed React frontend
- The SmartMed Spring Boot backend
- The MongoDB database
- JWT authentication and security filters
- Any external third-party scheduling service

---

## 2. Architecture & Design

The engine is built around two algorithmic pillars:
1. **Multi-Factor Normalised Priority Calculator**: Accurately measures clinical urgency by balancing usable stock buffers, consumption trajectories, clinical criticality, and expiring batches.
2. **Custom Indexed 4-ary Max Heap**: A 4-way max heap stored in a dynamic array (`ArrayList`) backed by a synchronized `HashMap<String, Integer>` mapping medicine IDs to array indices. It offers $O(1)$ lookups, $O(\log_4 n)$ arbitrary updates/deletions, and 50% fewer tree levels than conventional binary heaps.

### Architecture Workflow
```
+-----------------------------------+       +------------------------------------+
|     1. Medicine Input Data        |       |   2. Priority Calculation Engine   |
| - JSON: data/sample_medicines.json| ----> | - Stock Urgency (40%)              |
| - CLI: Manual Wizard Entry        |       | - Consumption Urgency (25%)        |
+-----------------------------------+       | - Clinical Criticality (20%)       |
                                            | - Expiry Risk (15%)                |
                                            +------------------------------------+
                                                              |
                                                              v
+-----------------------------------+       +------------------------------------+
|  4. Output & Restock Report       |       |    3. Indexed 4-ary Max Heap       |
| - Ranked Restocking Schedule      | <---- | - Dynamic Array (Branching d = 4)  |
| - Step-by-Step Heap Trace         |       | - HashMap Index Mapping (O(1) find)|
| - Suggested Order Quantities      |       | - Sift-Up & Sift-Down Reposition   |
+-----------------------------------+       +------------------------------------+
```

---

## 3. Folder Structure

```text
SmartMed-Priority-Scheduling-Engine/
│
├── src/
│   ├── models/
│   │   ├── Medicine.java                # Core domain model with stock, consumption, batches
│   │   ├── BatchExpiry.java             # Stock batch record (quantity, daysToExpiry)
│   │   └── Criticality.java             # Clinical criticality enum (LOW, MEDIUM, HIGH, CRITICAL)
│   │
│   ├── heap/
│   │   ├── HeapItem.java                # Interface for heap elements (ID, priority, timestamp)
│   │   ├── HeapStats.java               # Execution metric counters (comparisons, swaps, etc.)
│   │   └── Indexed4AryMaxHeap.java      # Custom 4-ary Indexed Max Heap data structure
│   │
│   ├── scheduler/
│   │   ├── PriorityCalculator.java      # SmartMed priority formula implementation
│   │   ├── PriorityWeights.java         # Normalised configurable weights (40/25/20/15)
│   │   ├── PriorityBreakdown.java       # Factor-by-factor breakdown record
│   │   ├── PriorityInputs.java          # Raw formula inputs
│   │   ├── RestockTaskNode.java         # Heap node for restock tasks (emergency-aware)
│   │   ├── HeapOperation.java           # Operation audit log record
│   │   ├── HeapSnapshot.java            # Level-order structural snapshot record
│   │   └── RestockPriorityScheduler.java# Facade coordinating task lifecycle & heap
│   │
│   ├── service/
│   │   └── InventoryService.java        # Inventory management, restock evaluation, and demo runner
│   │
│   ├── input/
│   │   └── InputHandler.java            # Zero-dependency JSON parser & interactive console wizard
│   │
│   ├── output/
│   │   └── ResultPrinter.java           # Formatted tables, ASCII tree diagrams, and calculation logs
│   │
│   ├── benchmark/
│   │   └── PerformanceBenchmark.java   # Micro-benchmarks for N = 100, 1K, 10K, 50K items
│   │
│   └── Main.java                        # Interactive 13-option CLI demonstration menu
│
├── data/
│   └── sample_medicines.json            # Realistic hospital pharmacy sample dataset
│
├── tests/
│   ├── Indexed4AryMaxHeapTest.java      # 13 dedicated unit tests for 4-ary heap invariants
│   ├── PriorityCalculatorTest.java      # Formula tests including worked Insulin 76.23 proof
│   ├── SchedulerTest.java               # End-to-end scheduling scenarios & edge cases
│   └── TestRunner.java                  # Standalone test runner with zero dependencies
│
├── docs/
│   ├── algorithm-documentation.md       # Full mathematical & algorithmic documentation
│   └── architecture-diagram.md         # Mermaid architecture and tree hierarchy diagrams
│
├── run.bat                              # Windows batch execution script
├── run.ps1                              # PowerShell execution script
├── test.bat                             # Windows batch test script
├── pom.xml                              # Maven configuration
└── README.md                            # Comprehensive project guide
```

---

## 4. Priority Calculation Formula

The engine faithfully preserves the exact formula and business rules from the SmartMed project:

$$\text{Priority Score} = 100 \times \left( W_1 \times \text{StockUrgency} + W_2 \times \text{ConsumptionUrgency} + W_3 \times \text{Criticality} + W_4 \times \text{ExpiryRisk} \right)$$

### Factor Breakdown:
| Factor | Formula | Description & Intuition |
|---|---|---|
| **StockUrgency** | $\text{clamp}\left(1 - \frac{\text{stock}}{2 \times \text{reorderLevel}}\right)$ | $0$ stock $\to 1.0$; at reorder threshold $\to 0.5$; $\ge 2\times$ threshold $\to 0.0$ |
| **ConsumptionUrgency** | $\text{clamp}\left(1 - \frac{\text{daysOfCover}}{14.0}\right)$ | $\text{daysOfCover} = \frac{\text{stock}}{\text{avgDailyConsumption}}$; measures depletion trajectory |
| **Criticality** | `LOW` $0.25$, `MEDIUM` $0.50$, `HIGH` $0.75$, `CRITICAL` $1.00$ | Normalized clinical severity of the medicine |
| **ExpiryRisk** | $\frac{\sum \text{qty}_i \times \text{proximity}_i}{\sum \text{qty}_i}$ | $\text{proximity}_i = \text{clamp}\left(\frac{30 - \text{daysToExpiry}_i}{30}\right)$ over batches |

*Default Weights*: $W_1 = 0.40, W_2 = 0.25, W_3 = 0.20, W_4 = 0.15$ (normalized to 1.0).

> **Important Architecture Note**: Supplier lead time is deliberately **not** part of the priority score formula in SmartMed. Priority assesses clinical urgency; lead time is a supply-chain parameter and is retained on `Medicine` for order display and planning.

### Restocking Trigger & Quantity Logic:
1. **Trigger Condition**: A restock is scheduled if $\text{effectiveStock} \le \text{reorderLevel}$, where $\text{effectiveStock} = \text{usableStock} - \text{expiringSoonStock}$ (units expiring within 30 days).
2. **Suggested Order Quantity**:
   $$\text{suggestedQuantity} = \max\left(1, \max(3 \times \text{reorderLevel}, 30 \times \text{dailyConsumption}) - \text{effectiveStock}\right)$$

---

## 5. Indexed 4-ary Max Heap Details

### 5.1 4-ary Indexing Formulas (0-based)
For any element at index $i$:
- **Parent**: $\text{parent}(i) = \lfloor (i - 1) / 4 \rfloor$ (root at index 0 has parent -1)
- **Child 1**: $4i + 1$
- **Child 2**: $4i + 2$
- **Child 3**: $4i + 3$
- **Child 4**: $4i + 4$

```
                              [Root: Index 0]
                      /        |             |        \
            [Index 1]      [Index 2]     [Index 3]     [Index 4]
          /  |  |  \      /  |  |  \    /  |  |  \    /  |  |  \
         5   6  7   8    9  10 11  12  13 14 15  16  17 18 19  20
```

### 5.2 Key Operational Features
- **$O(1)$ Average ID Lookup**: Backing `HashMap<String, Integer>` maps medicine ID directly to its current array position.
- **$O(\log_4 n)$ Arbitrary Priority Update**: Locate slot in $O(1)$, modify score, and restore heap property via `siftUp` or `siftDown`.
- **$O(\log_4 n)$ Arbitrary Deletion**: Swap target item with last leaf, remove last element in $O(1)$, and sift the swapped element up or down.
- **Centralized `swap()`**: Updates both array elements and both hash map pointers simultaneously, ensuring 100% index map consistency.
- **Tie-Breaking**: Higher score first; ties broken by older creation time, then medicine ID lexicographically.

---

## 6. Input Data Formats

### A. JSON Format (`data/sample_medicines.json`)
```json
{
  "medicines": [
    {
      "medicineId": "MED001",
      "medicineName": "Paracetamol 500mg Tablets",
      "currentStock": 20,
      "minimumStock": 100,
      "dailyConsumption": 15.0,
      "criticality": "MEDIUM",
      "supplierLeadTime": 5,
      "batches": [
        { "quantity": 10, "daysToExpiry": 15 },
        { "quantity": 10, "daysToExpiry": 180 }
      ]
    }
  ]
}
```

### B. Interactive Console Input
The engine provides a guided prompt wizard (`Option 2`) with validation for ID, Name, Current Stock, Reorder Level, Daily Consumption, Criticality (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL` or `1-10`), Lead Time, and Stock Batches.

---

## 7. Sample Scheduling Output

Execution on the 12 sample hospital medicines generates:

```text
==================================================================================================================
                                    FINAL RANKED MEDICINE RESTOCKING SCHEDULE                                     
==================================================================================================================
| Rank | Medicine ID | Medicine Name                | Priority Score | Current Stock | Suggested Restock Quantity |
|------|------------|------------------------------|----------------|---------------|----------------------------|
| 1    | MED010     | Heparin 5000 IU/ml Vial      |          92.93 |             3 |                        150 |
| 2    | MED004     | Epinephrine 1mg/ml Ampoules  |          90.77 |             5 |                        150 |
| 3    | MED003     | Amoxicillin 500mg Capsules   |          80.00 |             0 |                        750 |
| 4    | MED002     | Regular Insulin 100 IU/ml    |          76.23 |            22 |                        338 |
| 5    | MED007     | Salbutamol Inhaler 100mcg    |          76.12 |             8 |                        180 |
| 6    | MED009     | Ceftriaxone 1g Injection     |          75.80 |            12 |                        408 |
| 7    | MED001     | Paracetamol 500mg Tablets    |          72.37 |            20 |                        440 |
| 8    | MED012     | Furosemide 40mg Tablets      |          72.29 |            18 |                        270 |
| 9    | MED006     | Metformin 500mg Tablets      |          69.54 |            45 |                        495 |
| 10   | MED008     | Vitamin C 500mg Chewable     |          59.64 |            15 |                        165 |
==================================================================================================================
SCHEDULING SUMMARY STATISTICS:
  - Total Medicines Evaluated        : 12
  - Medicines Requiring Restocking    : 10
  - Medicines Scheduled in Heap      : 10
  - Total Suggested Restock Quantity : 3346 units
  - Highest Priority Urgent Restock  : Heparin 5000 IU/ml Vial [MED010] (Score: 92.93)
==================================================================================================================
```

---

## 8. Compilation and Execution

### Method 1: Using Included Maven Wrapper (Recommended)

#### Run the Interactive CLI:
```powershell
cd SmartMed-Priority-Scheduling-Engine
.\mvnw.cmd compile exec:java
```

#### Run All Automated Unit Tests:
```powershell
cd SmartMed-Priority-Scheduling-Engine
.\mvnw.cmd test
```

### Method 2: Pure Standard Java (Zero External Dependencies)

#### Compile and Run Interactive CLI:
```powershell
cd SmartMed-Priority-Scheduling-Engine
mkdir bin -Force
javac -d bin (Get-ChildItem -Recurse -Filter *.java src | Select-Object -ExpandProperty FullName)
java -cp bin Main
```

#### Run Standalone Zero-Dependency Test Runner:
```powershell
cd SmartMed-Priority-Scheduling-Engine
javac -cp "bin;target/test-classes;tests" -d bin (Get-ChildItem -Recurse -Filter *.java src,tests | Select-Object -ExpandProperty FullName)
java -cp bin TestRunner
```

### Method 3: One-Click Runner Scripts
- Double click or run `.\run.bat` or `.\run.ps1` to launch the interactive application.
- Double click or run `.\test.bat` to run the test suite.

---

## 9. Time Complexity & Performance Benchmarks

### 9.1 Theoretical Complexity Guarantees
| Operation | Theoretical Complexity | Architectural Mechanism |
|---|---|---|
| **Insert** | $O(\log_4 n)$ | Append to array + sift-up along parent path ($\approx \frac{\log_2 n}{2}$ levels) |
| **Peek Max** | $O(1)$ | Immediate array read at root slot `heap.get(0)` |
| **Extract Max** | $O(4 \log_4 n)$ | Move last leaf to root + sift-down evaluating 4 children per level |
| **Search by Medicine ID** | $O(1)$ average | Direct key lookup via `HashMap<String, Integer>` |
| **Update Priority** | $O(\log_4 n)$ | $O(1)$ lookup + single-direction sift-up or sift-down |
| **Delete by ID** | $O(\log_4 n)$ | $O(1)$ lookup + swap with last item + sift-up/down |
| **Heap Build (Floyd)** | $O(n)$ | Bottom-up sift-down from parent of last item down to root index 0 |

### 9.2 Measured Empirical Benchmark Results
Measured on modern JVM 21 using `System.nanoTime()`:

| Items ($N$) | Floyd Build ($O(n)$) | Sequential Insert ($O(n \log_4 n)$) | Avg Insert Time | Avg $O(1)$ Search | Avg Priority Update | Avg ExtractMax | Full Restock Scheduling |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **100** | 3.806 ms | 3.326 ms | 33.264 µs | 2.493 µs | 46.370 µs | 84.339 µs | 5.835 ms |
| **1,000** | 14.426 ms | 16.138 ms | 16.138 µs | 0.709 µs | 23.675 µs | 76.060 µs | 63.606 ms |
| **10,000** | 76.493 ms | 106.180 ms | 10.618 µs | 0.651 µs | 11.958 µs | 114.102 µs | 534.845 ms |
| **50,000** | **246.410 ms** | **378.776 ms** | 7.576 µs | **0.484 µs** | 10.842 µs | 86.826 µs | 2,906.401 ms |

#### Key Insights from Benchmark Data:
1. **Floyd's Algorithm vs Sequential Insertion**: For large inventories ($N = 50,000$), Floyd's bottom-up construction completes in **246.4 ms** compared to **378.8 ms** for sequential insertion—verifying the theoretical $O(n)$ advantage over $O(n \log_4 n)$.
2. **Sub-Microsecond Search**: Medicine lookup via the indexed hash map consistently clocks in at **0.48 µs to 0.70 µs**, providing genuine $O(1)$ retrieval regardless of heap size.
3. **Cache Efficiency**: The 4-ary branching factor reduces tree depth by half, allowing 50,000 medicine tasks to be scheduled in under 3 seconds.

---

## 10. Interactive Menu Guide

```text
========== SMARTMED PRIORITY SCHEDULING ENGINE ==========
 1. Load Sample Inventory              # Loads data/sample_medicines.json into inventory
 2. Enter Medicine Manually            # Guided wizard with validation for custom medicine
 3. Display Inventory                  # Formatted table of loaded medicines and stock levels
 4. Calculate Medicine Priorities      # Calculates 4-factor scores and explains calculation
 5. Insert Medicine into Heap          # Step-by-step interactive insertion with sift-up logs
 6. Display Heap Structure             # Array layout, parent/children indices, map & ASCII tree
 7. Extract Highest Priority Medicine  # Extracts root, logs sift-down, shows served task
 8. Update Medicine Priority           # Modifies priority score, repositions item dynamically
 9. Delete Medicine by ID              # Arbitrary deletion with invariant preservation
10. Search Medicine by ID              # O(1) hash map search with tree position details
11. Generate Complete Restocking List  # Runs full pipeline and generates ranked recommendation table
12. Run Heap Validation                # Validates all 4 invariants (heap order, map, uniqueness)
13. Run Performance Benchmark          # Executes live benchmarks for 100 to 50,000 items
 0. Exit                               # Clean exit
=========================================================
```

---

## 11. Verification Checklist

- [x] Completely separate project folder: `SmartMed-Priority-Scheduling-Engine`
- [x] Original SmartMed project files left **completely unmodified**
- [x] Zero-based 4-ary indexing formulas verified ($parent = (i-1)/4, children = 4i + \{1,2,3,4\}$)
- [x] All 4 children compared during `siftDown`
- [x] Centralized `swap()` synchronizes dynamic array and hash map index
- [x] Preserves exact SmartMed multi-factor priority formula (including Insulin worked example = 76.23)
- [x] Preserves SmartMed restocking quantity logic
- [x] Validated JSON and interactive manual console inputs
- [x] Comprehensive automated test suite (26/26 tests passing)
- [x] Empirical benchmarks for $N \in \{100, 1000, 10000, 50000\}$ using `System.nanoTime()`
