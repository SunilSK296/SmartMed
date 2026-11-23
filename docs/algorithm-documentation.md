# SmartMed Standalone Priority Scheduling Engine: Algorithm & Data Structure Documentation

## 1. Executive Overview

The **SmartMed Priority Scheduling Engine** is an independent, real-time decision-support component designed to identify critically depleted or expiring hospital pharmacy inventory and rank them into an optimal, priority-ordered restocking queue.

At the core of this engine are two foundational components:
1. **Multi-Factor Normalised Priority Calculation Formula**: Evaluates clinical urgency, current stock buffer, daily consumption rate, and batch expiration proximity on a $[0, 100]$ score scale.
2. **Indexed 4-ary Max Heap**: A cache-friendly, shallow 4-way priority queue stored in a contiguous dynamic array, indexed by a hash map for $O(1)$ average item lookups and $O(\log_4 n)$ arbitrary updates/deletions.

---

## 2. Mathematical Priority Calculation Formula

In hospital pharmacy management, treating restock decisions purely on static stock thresholds (e.g. *stock < minimum*) leads to clinical vulnerabilities. Fast-moving medicines can stock out rapidly, critical life-saving drugs demand higher priority than elective supplements, and stock with impending expiration presents immediate clinical stockout risk.

The SmartMed priority algorithm addresses this via four weighted, normalised factors:

$$\text{Priority Score} = 100 \times \left( W_1 \times \text{StockUrgency} + W_2 \times \text{ConsumptionUrgency} + W_3 \times \text{Criticality} + W_4 \times \text{ExpiryRisk} \right)$$

### 2.1 Configured Weights ($W_1, W_2, W_3, W_4$)
The default production weights configured in SmartMed are:
- $W_1 = 0.40$ (Stock Depletion Urgency — 40%)
- $W_2 = 0.25$ (Consumption Depletion Rate — 25%)
- $W_3 = 0.20$ (Clinical Criticality — 20%)
- $W_4 = 0.15$ (Batch Expiration Risk — 15%)

All weights are strictly normalized to sum to $1.0$:

$$W_i^{\text{norm}} = \frac{W_i}{\sum_{k=1}^4 W_k}$$

Every component factor is bounded within $[0, 1]$, guaranteeing that the final score strictly resides within $[0, 100]$.

---

### 2.2 Factor Definitions

#### Factor 1: Stock Urgency ($\text{StockUrgency} \in [0, 1]$)
Evaluates current usable stock relative to twice the reorder threshold:

$$\text{StockUrgency} = \text{clamp}\left(1 - \frac{\text{stock}}{2 \times \text{reorderLevel}}\right)$$

Where:
- $\text{clamp}(x) = \max(0.0, \min(1.0, x))$
- If $\text{stock} \le 0 \implies \text{StockUrgency} = 1.0$ (complete stockout)
- If $\text{stock} = \text{reorderLevel} \implies \text{StockUrgency} = 0.5$ (threshold alert)
- If $\text{stock} \ge 2 \times \text{reorderLevel} \implies \text{StockUrgency} = 0.0$ (comfortable stock buffer)

#### Factor 2: Consumption Urgency ($\text{ConsumptionUrgency} \in [0, 1]$)
Evaluates the run-out trajectory based on average daily consumption and days of stock cover against a 14-day planning horizon ($H = 14.0$ days):

$$\text{daysOfCover} = \frac{\text{stock}}{\text{avgDailyConsumption}}$$

$$\text{ConsumptionUrgency} = \text{clamp}\left(1 - \frac{\text{daysOfCover}}{H}\right)$$

Where:
- If $\text{avgDailyConsumption} \le 0 \implies \text{ConsumptionUrgency} = 0.0$
- If $\text{stock} \le 0 \implies \text{ConsumptionUrgency} = 1.0$
- If $\text{daysOfCover} \ge 14 \text{ days} \implies \text{ConsumptionUrgency} = 0.0$

#### Factor 3: Clinical Criticality ($\text{Criticality} \in [0, 1]$)
Captures clinical importance categorized by hospital medical guidelines:
- `LOW` ($0.25$): Comfort medications, standard vitamins, OTC antacids.
- `MEDIUM` ($0.50$): Standard prescription maintenance medications, oral antibiotics.
- `HIGH` ($0.75$): Urgent therapeutic drugs, cardiovascular stabilizers, IV antibiotics.
- `CRITICAL` ($1.00$): Life-saving medications, ICU drugs, insulin, epinephrine, anticoagulants.

#### Factor 4: Expiry Risk ($\text{ExpiryRisk} \in [0, 1]$)
Weighted share of unexpired inventory expiring within the 30-day window ($W_{\text{exp}} = 30$ days):

$$\text{proximity}_i = \text{clamp}\left(\frac{30 - \text{daysToExpiry}_i}{30}\right)$$

$$\text{ExpiryRisk} = \frac{\sum_i \left( \text{qty}_i \times \text{proximity}_i \right)}{\sum_i \text{qty}_i}$$

Batches already expired ($\text{daysToExpiry} \le 0$) have proximity $1.0$. Batches expiring beyond 30 days have proximity $0.0$.

---

### 2.3 Note on Supplier Lead Time
In strict accordance with the SmartMed production architecture, **supplier lead time is deliberately NOT part of the priority score formula**. Priority determines clinical urgency of restocking needs; lead time is a supply-chain parameter utilized in purchase order planning and displayed for logistical visibility.

---

## 3. Restocking Trigger & Quantity Determination

### 3.1 Eligibility Condition
A restock task is raised or updated whenever:

$$\text{effectiveStock} \le \text{reorderLevel}$$

Where:
- $\text{usableStock} = \sum_{\text{unexpired}} \text{quantity}$
- $\text{expiringSoonStock} = \sum_{0 \le \text{daysToExpiry} \le 30} \text{quantity}$
- $\text{effectiveStock} = \max(0, \text{usableStock} - \text{expiringSoonStock})$

### 3.2 Suggested Restock Quantity
The recommended order quantity calculates the buffer required to reach the target stock level:

$$\text{targetByLevel} = \lceil \text{reorderLevel} \times 3.0 \rceil$$

$$\text{targetByDemand} = \lceil \text{dailyConsumption} \times 30.0 \rceil$$

$$\text{targetStock} = \max(\text{targetByLevel}, \text{targetByDemand})$$

$$\text{suggestedQuantity} = \max(1, \text{targetStock} - \text{effectiveStock})$$

---

## 4. Indexed 4-ary Max Heap Architecture

### 4.1 Structural Mechanics
Unlike standard binary heaps ($d=2$), a 4-ary heap ($d=4$) features a branching factor of 4. This produces a significantly shallower tree:
- Tree height for $n$ elements is $\approx \log_4 n = \frac{\log_2 n}{2}$ (a **50% reduction in tree depth** compared to binary heaps).
- Sift-up traverses at most $\approx \log_4 n$ levels.
- Sift-down compares 4 children per level, optimizing modern CPU L1/L2 cache prefetching because children are stored contiguously in memory.

### 4.2 Zero-Based Indexing Formulas
For a node located at index $i$:

```java
// Parent index (-1 for root i = 0):
parent = (i - 1) / 4;

// Four children indices:
child1 = 4 * i + 1;
child2 = 4 * i + 2;
child3 = 4 * i + 3;
child4 = 4 * i + 4;
```

### 4.3 Hash Map Indexing for $O(1)$ Lookups
A standard priority queue cannot locate or update arbitrary items in sub-linear time ($O(n)$ search).
The **Indexed 4-ary Max Heap** pairs the dynamic array with a `HashMap<String, Integer>` mapping `medicineId -> arrayIndex`.
Whenever items are moved or swapped in the array (`swap(int a, int b)`):

```java
heap.set(a, tb);
heap.set(b, ta);
index.put(tb.getId(), a);
index.put(ta.getId(), b);
```

Both mappings are updated simultaneously, keeping array positions and hash map indices in 100% lockstep.

### 4.4 Deterministic Ordering & Tie-Breaking
Ties in priority score are resolved deterministically:
1. Higher priority score first: $\text{score}_a > \text{score}_b$.
2. Older creation timestamp first: $\text{time}_a < \text{time}_b$.
3. Lexicographical ID order: $\text{id}_a < \text{id}_b$.

---

## 5. Algorithmic Complexity

| Operation | Expected Complexity | Mathematical Justification |
|---|---|---|
| `insert(item)` | $O(\log_4 n)$ | Item appended at array end; sifts up at most $\lfloor \log_4 n \rfloor$ parent levels. |
| `peekMax()` | $O(1)$ | Immediate read from array index 0. |
| `extractMax()` | $O(4 \log_4 n)$ | Root removed; last element moved to index 0; sifts down evaluating $\le 4$ children per level. |
| `findById(id)` | $O(1)$ average | Single hash map lookup returning array index. |
| `updatePriority(id, p)` | $O(\log_4 n)$ | $O(1)$ hash map lookup, followed by either sift-up or sift-down. |
| `deleteById(id)` | $O(\log_4 n)$ | $O(1)$ lookup, swap with last element, $O(1)$ removal, and sift-up/down. |
| `rebuildHeap()` | $O(n)$ | Floyd's bottom-up algorithm sifting down from index $\lfloor (n-1)/4 \rfloor$ down to 0. |

---

## 6. Comprehensive Invariant Validation

The heap implements a comprehensive 4-invariant validator (`validate()`):
1. **Max Heap Order**: For every child index $i \in [1, n-1]$, $\text{parent}(i)$ outranks or equals child $i$.
2. **Index Map Size Consistency**: $\text{map.size}() == \text{heap.size}()$.
3. **Exact Positional Inversion**: For every index $i$, $\text{map.get}(\text{heap.get}(i).\text{getId}()) == i$.
4. **Key Uniqueness**: No duplicate medicine IDs exist in the dynamic array.
