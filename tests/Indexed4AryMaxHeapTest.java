import heap.HeapItem;
import heap.Indexed4AryMaxHeap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for the Indexed 4-ary Max Heap.
 * Verifies all 4-ary indexing formulas, child comparisons, heapify up/down,
 * index map consistency, tie-breaking, and invariant validation.
 */
public class Indexed4AryMaxHeapTest {

    static final class TestMedicine implements HeapItem {
        private final String id;
        private double priority;
        private final long createdAtMillis;

        TestMedicine(String id, double priority, long createdAtMillis) {
            this.id = id;
            this.priority = priority;
            this.createdAtMillis = createdAtMillis;
        }

        TestMedicine(String id, double priority) {
            this(id, priority, 0L);
        }

        @Override public String getId() { return id; }
        @Override public double getPriority() { return priority; }
        @Override public void setPriority(double priority) { this.priority = priority; }
        @Override public long getCreatedAtMillis() { return createdAtMillis; }
    }

    private static void assertInvariants(Indexed4AryMaxHeap<TestMedicine> h) {
        assertTrue(h.validateHeap(), "Heap property violated: parent < child");
        assertTrue(h.isIndexConsistent(), "Hash map index is inconsistent with array positions");
        assertTrue(h.validate(), "Comprehensive 4-invariant validation failed");
    }

    // 0. Formula verification
    @Test
    @DisplayName("0. 4-ary Indexing formulas: parent = (i-1)/4 and children = 4*i + {1,2,3,4}")
    public void testIndexingFormulas() {
        assertEquals(-1, Indexed4AryMaxHeap.parentOf(0));
        assertEquals(0, Indexed4AryMaxHeap.parentOf(1));
        assertEquals(0, Indexed4AryMaxHeap.parentOf(2));
        assertEquals(0, Indexed4AryMaxHeap.parentOf(3));
        assertEquals(0, Indexed4AryMaxHeap.parentOf(4));

        assertEquals(1, Indexed4AryMaxHeap.parentOf(5));
        assertEquals(1, Indexed4AryMaxHeap.parentOf(6));
        assertEquals(1, Indexed4AryMaxHeap.parentOf(7));
        assertEquals(1, Indexed4AryMaxHeap.parentOf(8));

        for (int i = 0; i < 50; i++) {
            int c1 = Indexed4AryMaxHeap.child1Of(i);
            int c2 = Indexed4AryMaxHeap.child2Of(i);
            int c3 = Indexed4AryMaxHeap.child3Of(i);
            int c4 = Indexed4AryMaxHeap.child4Of(i);

            assertEquals(4 * i + 1, c1);
            assertEquals(4 * i + 2, c2);
            assertEquals(4 * i + 3, c3);
            assertEquals(4 * i + 4, c4);

            assertEquals(i, Indexed4AryMaxHeap.parentOf(c1));
            assertEquals(i, Indexed4AryMaxHeap.parentOf(c2));
            assertEquals(i, Indexed4AryMaxHeap.parentOf(c3));
            assertEquals(i, Indexed4AryMaxHeap.parentOf(c4));

            int[] ch = Indexed4AryMaxHeap.childrenOf(i);
            assertArrayEquals(new int[]{c1, c2, c3, c4}, ch);
        }
    }

    // 1. Empty heap
    @Test
    @DisplayName("1. Empty heap behavior")
    public void testEmptyHeap() {
        Indexed4AryMaxHeap<TestMedicine> h = new Indexed4AryMaxHeap<>();
        assertTrue(h.isEmpty());
        assertEquals(0, h.size());
        assertTrue(h.peekMax().isEmpty());
        assertThrows(NoSuchElementException.class, h::extractMax);
        assertFalse(h.contains("ANY"));
        assertEquals(-1, h.indexOfId("ANY"));
        assertTrue(h.validate());
    }

    // 2. Single-element insertion
    @Test
    @DisplayName("2. Single element insertion and deletion")
    public void testSingleElement() {
        Indexed4AryMaxHeap<TestMedicine> h = new Indexed4AryMaxHeap<>();
        TestMedicine m = new TestMedicine("MED-001", 50.0);
        h.insert(m);

        assertFalse(h.isEmpty());
        assertEquals(1, h.size());
        assertEquals(0, h.indexOfId("MED-001"));
        assertEquals("MED-001", h.peekMax().orElseThrow().getId());
        assertInvariants(h);

        TestMedicine extracted = h.extractMax();
        assertEquals("MED-001", extracted.getId());
        assertTrue(h.isEmpty());
        assertInvariants(h);
    }

    // 3. Multiple insertions
    @Test
    @DisplayName("3. Multiple insertions preserve Max Heap property")
    public void testMultipleInsertions() {
        Indexed4AryMaxHeap<TestMedicine> h = new Indexed4AryMaxHeap<>();
        double[] scores = {45.0, 12.0, 89.5, 34.0, 99.0, 67.5, 23.0, 78.0, 91.2, 5.0, 56.0};

        for (int i = 0; i < scores.length; i++) {
            h.insert(new TestMedicine("MED-" + i, scores[i]));
            assertInvariants(h);
        }

        assertEquals(scores.length, h.size());
        assertEquals("MED-4", h.peekMax().orElseThrow().getId()); // 99.0
        assertEquals(99.0, h.peekMax().orElseThrow().getPriority());
    }

    // 4. Sift Up on Higher Priority
    @Test
    @DisplayName("4. Sift-Up restores root when maximum priority is inserted at leaf")
    public void testSiftUp() {
        Indexed4AryMaxHeap<TestMedicine> h = new Indexed4AryMaxHeap<>();
        for (int i = 0; i < 20; i++) {
            h.insert(new TestMedicine("MED-" + i, 10.0 + i));
        }

        // Insert new highest priority 250.0
        h.insert(new TestMedicine("EMERGENCY", 250.0));
        assertInvariants(h);

        assertEquals("EMERGENCY", h.peekMax().orElseThrow().getId());
        assertEquals(0, h.indexOfId("EMERGENCY"));
    }

    // 5. Extract Max & Sift Down
    @Test
    @DisplayName("5. Extract Max extracts in strictly descending priority order across 4 children")
    public void testExtractMaxDescending() {
        Indexed4AryMaxHeap<TestMedicine> h = new Indexed4AryMaxHeap<>();
        double[] scores = {10, 50, 30, 80, 20, 95, 60, 5, 88, 72, 41, 15, 63};
        for (int i = 0; i < scores.length; i++) {
            h.insert(new TestMedicine("MED-" + i, scores[i]));
        }

        double prev = Double.MAX_VALUE;
        while (!h.isEmpty()) {
            TestMedicine top = h.extractMax();
            assertTrue(top.getPriority() <= prev, "Order was not descending: " + top.getPriority() + " > " + prev);
            prev = top.getPriority();
            assertInvariants(h);
        }
    }

    // 6. Update Priority: Low to High
    @Test
    @DisplayName("6. Update Priority upward sifts up to root")
    public void testUpdatePriorityUpward() {
        Indexed4AryMaxHeap<TestMedicine> h = new Indexed4AryMaxHeap<>();
        for (int i = 0; i < 15; i++) {
            h.insert(new TestMedicine("MED-" + i, 100 - i));
        }

        // MED-14 is at leaf with 86.0
        h.updatePriority("MED-14", 999.0);
        assertInvariants(h);

        assertEquals(0, h.indexOfId("MED-14"));
        assertEquals("MED-14", h.peekMax().orElseThrow().getId());
        assertEquals(999.0, h.peekMax().orElseThrow().getPriority());
    }

    // 7. Update Priority: High to Low
    @Test
    @DisplayName("7. Update Priority downward sifts down across 4 children")
    public void testUpdatePriorityDownward() {
        Indexed4AryMaxHeap<TestMedicine> h = new Indexed4AryMaxHeap<>();
        for (int i = 0; i < 25; i++) {
            h.insert(new TestMedicine("MED-" + i, 200 - i));
        }

        // Demote root MED-0 from 200.0 to 1.0
        h.updatePriority("MED-0", 1.0);
        assertInvariants(h);

        assertEquals("MED-1", h.peekMax().orElseThrow().getId());
        assertEquals(0, h.indexOfId("MED-1"));
        assertTrue(h.indexOfId("MED-0") > 0);
    }

    // 8. Delete Arbitrary Medicine by ID
    @Test
    @DisplayName("8. Delete arbitrary medicine by ID (root, internal, leaf)")
    public void testDeleteById() {
        Indexed4AryMaxHeap<TestMedicine> h = new Indexed4AryMaxHeap<>();
        for (int i = 0; i < 20; i++) {
            h.insert(new TestMedicine("MED-" + i, (i * 37) % 100));
        }

        // Delete leaf
        TestMedicine delLeaf = h.deleteById("MED-19");
        assertEquals("MED-19", delLeaf.getId());
        assertFalse(h.contains("MED-19"));
        assertInvariants(h);

        // Delete root
        String rootId = h.peekMax().orElseThrow().getId();
        TestMedicine delRoot = h.deleteById(rootId);
        assertEquals(rootId, delRoot.getId());
        assertFalse(h.contains(rootId));
        assertInvariants(h);

        // Delete internal node
        String midId = h.toList().get(h.size() / 2).getId();
        TestMedicine delMid = h.deleteById(midId);
        assertEquals(midId, delMid.getId());
        assertFalse(h.contains(midId));
        assertInvariants(h);
    }

    // 9. Equal Priority Tie Breaking
    @Test
    @DisplayName("9. Equal priority values break ties deterministically (older timestamp first, then ID)")
    public void testEqualPriorityTieBreaking() {
        Indexed4AryMaxHeap<TestMedicine> h = new Indexed4AryMaxHeap<>();
        TestMedicine m1 = new TestMedicine("MED-C", 50.0, 3000L);
        TestMedicine m2 = new TestMedicine("MED-B", 50.0, 1000L);
        TestMedicine m3 = new TestMedicine("MED-A", 50.0, 1000L);

        h.insert(m1);
        h.insert(m2);
        h.insert(m3);
        assertInvariants(h);

        // m3 and m2 have older timestamp (1000L), "MED-A" < "MED-B" alphabetically
        assertEquals("MED-A", h.extractMax().getId());
        assertEquals("MED-B", h.extractMax().getId());
        assertEquals("MED-C", h.extractMax().getId());
    }

    // 10. Duplicate ID Handling
    @Test
    @DisplayName("10. Duplicate medicine ID is rejected safely")
    public void testDuplicateIdRejected() {
        Indexed4AryMaxHeap<TestMedicine> h = new Indexed4AryMaxHeap<>();
        h.insert(new TestMedicine("MED-001", 60.0));
        assertThrows(IllegalArgumentException.class, () -> h.insert(new TestMedicine("MED-001", 80.0)));
    }

    // 11. Hash Map Index Consistency After Every Operation
    @Test
    @DisplayName("11. Hash map index remains 100% consistent after every operation")
    public void testHashMapIndexConsistency() {
        Indexed4AryMaxHeap<TestMedicine> h = new Indexed4AryMaxHeap<>();
        for (int i = 0; i < 30; i++) {
            h.insert(new TestMedicine("M-" + i, (i * 17) % 67));
            verifyIndexMap(h);
        }
        for (int i = 0; i < 15; i++) {
            h.updatePriority("M-" + i, (i * 31) % 100);
            verifyIndexMap(h);
        }
        for (int i = 0; i < 10; i++) {
            h.extractMax();
            verifyIndexMap(h);
        }
    }

    private void verifyIndexMap(Indexed4AryMaxHeap<TestMedicine> h) {
        List<TestMedicine> list = h.toList();
        assertEquals(list.size(), h.size());
        for (int i = 0; i < list.size(); i++) {
            String id = list.get(i).getId();
            assertEquals(i, h.indexOfId(id));
            assertTrue(h.findById(id).isPresent());
            assertSame(list.get(i), h.findById(id).get());
        }
    }

    // 12. Randomized Oracle Comparison (500 steps)
    @Test
    @DisplayName("12. Randomized interleaved operations match reference sorting oracle")
    public void testRandomizedOracleComparison() {
        Indexed4AryMaxHeap<TestMedicine> h = new Indexed4AryMaxHeap<>();
        List<TestMedicine> oracle = new ArrayList<>();
        Random rng = new Random(1234);

        Comparator<TestMedicine> oracleCmp = Comparator.<TestMedicine>comparingDouble(TestMedicine::getPriority)
                .thenComparing(Comparator.comparingLong(TestMedicine::getCreatedAtMillis).reversed())
                .thenComparing(Comparator.comparing(TestMedicine::getId).reversed());

        int counter = 0;
        for (int step = 0; step < 500; step++) {
            int op = rng.nextInt(4);
            if (op == 0 || oracle.isEmpty()) {
                TestMedicine item = new TestMedicine("M-" + counter++, rng.nextInt(1000), step);
                h.insert(item);
                oracle.add(item);
            } else if (op == 1) {
                TestMedicine expected = oracle.stream().max(oracleCmp).orElseThrow();
                TestMedicine actual = h.extractMax();
                assertSame(expected, actual);
                oracle.remove(expected);
            } else if (op == 2) {
                TestMedicine victim = oracle.get(rng.nextInt(oracle.size()));
                double np = rng.nextInt(1000);
                h.updatePriority(victim.getId(), np);
            } else {
                TestMedicine victim = oracle.remove(rng.nextInt(oracle.size()));
                TestMedicine deleted = h.deleteById(victim.getId());
                assertSame(victim, deleted);
            }

            if (step % 25 == 0) {
                assertInvariants(h);
                assertEquals(oracle.size(), h.size());
            }
        }
    }
}
