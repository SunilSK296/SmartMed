# SmartMed Priority Scheduling Engine Architecture

```mermaid
flowchart TD
    subgraph Input["1. Input Sources"]
        JSON["JSON Dataset<br/>(sample_medicines.json)"]
        CLI["Interactive CLI Prompt<br/>(Manual Entry Wizard)"]
    end

    subgraph Service["2. Domain & Business Logic"]
        Parser["InputHandler<br/>Zero-Dependency JSON Parser"]
        Model["Medicine Model<br/>Stock, Criticality, Batches"]
        Filter{"Restock Eligible?<br/>effectiveStock <= reorderLevel"}
    end

    subgraph Calc["3. Multi-Factor Priority Calculation"]
        SU["Stock Urgency (40%)<br/>clamp(1 - stock / 2*minStock)"]
        CU["Consumption Urgency (25%)<br/>clamp(1 - daysOfCover / 14)"]
        CR["Clinical Criticality (20%)<br/>LOW: 0.25 | MED: 0.5 | HIGH: 0.75 | CRIT: 1.0"]
        ER["Expiry Risk (15%)<br/>weighted proximity (30-day window)"]
        Formula["Score = 100 * (0.40*SU + 0.25*CU + 0.20*CR + 0.15*ER)"]
    end

    subgraph Heap["4. Indexed 4-ary Max Heap Engine"]
        Array["Dynamic Array (ArrayList)<br/>Branching Factor d = 4<br/>Parent: (i-1)/4<br/>Children: 4i+{1,2,3,4}"]
        Map["Hash Map (HashMap)<br/>Medicine ID -> Array Index<br/>O(1) Average Lookup"]
        Ops["Heap Operations<br/>- insert(): O(log4 n)<br/>- peekMax(): O(1)<br/>- extractMax(): O(4 log4 n)<br/>- updatePriority(): O(log4 n)<br/>- deleteById(): O(log4 n)"]
    end

    subgraph Output["5. Output & Restocking Recommendations"]
        Printer["ResultPrinter"]
        RankedTable["Final Ranked Restocking Schedule<br/>Rank #1 = Highest Priority Clinical Need"]
        Summary["Restock Statistics & Total Suggested Order Quantity"]
    end

    JSON --> Parser
    CLI --> Parser
    Parser --> Model
    Model --> Filter
    Filter -- "Yes" --> SU & CU & CR & ER
    Filter -- "No (Stock Buffer OK)" --> Discard["Remain in Normal Inventory"]
    SU & CU & CR & ER --> Formula
    Formula --> Ops
    Ops --> Array
    Ops <--> Map
    Array --> Printer
    Printer --> RankedTable
    Printer --> Summary
```

## 4-ary Max Heap Tree Hierarchy (Level Order)

```
                              [Root: Index 0]
                      /        |             |        \
            [Index 1]      [Index 2]     [Index 3]     [Index 4]
          /  |  |  \      /  |  |  \    /  |  |  \    /  |  |  \
         5   6  7   8    9  10 11  12  13 14 15  16  17 18 19  20
```

- **Height**: $\approx \log_4 n = \frac{\log_2 n}{2}$ (50% reduction in tree depth compared to binary heaps).
- **Cache Locality**: All 4 sibling children are stored contiguously in memory at indices $4i+1, 4i+2, 4i+3, 4i+4$, maximizing hardware cacheline prefetching.
- **Index Map Synchronization**: In every `swap(a, b)` operation, `map.put(id_a, b)` and `map.put(id_b, a)` execute simultaneously to guarantee bidirectional index consistency.
