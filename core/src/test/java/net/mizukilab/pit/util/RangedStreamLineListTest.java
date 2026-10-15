package net.mizukilab.pit.util;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class RangedStreamLineListTest {
    @Test
    public void zeroCapacityRetainsLiveEntriesAndExpiresWithoutNewInserts() {
        Set<Integer> expired = new HashSet<>();
        List<Integer> removed = new ArrayList<>();
        RangedStreamLineList<Integer> entries = new RangedStreamLineList<>(0, expired::contains) {
            @Override public void onRecycle(Integer value) { removed.add(value); }
        };
        entries.addAll(List.of(1, 2, 3));
        assertEquals(3, entries.size());
        expired.add(2); // An interior entry can expire before the oldest one.
        entries.recycle();
        entries.recycle();
        assertEquals(List.of(3, 1), new ArrayList<>(entries));
        assertEquals(List.of(2), removed);
    }

    @Test
    public void capacityStillWorksAfterPollClearAndBulkInsert() {
        RangedStreamLineList<Integer> entries = new RangedStreamLineList<>(2, value -> false);
        assertNull(entries.poll());
        entries.addAll(List.of(1, 2, 3));
        assertEquals(List.of(3, 2), new ArrayList<>(entries));
        entries.pollLast();
        entries.clear();
        entries.offer(4);
        entries.addFirst(5);
        entries.add(6);
        assertEquals(List.of(6, 5), new ArrayList<>(entries));
    }

    @Test(timeout = 10000)
    public void concurrentProducersRespectCapacity() throws Exception {
        RangedStreamLineList<Integer> entries = new RangedStreamLineList<>(100, value -> false);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int producer = i;
                workers.add(pool.submit(() -> { for (int n = 0; n < 500; n++) entries.add(producer * 500 + n); }));
            }
            for (Future<?> worker : workers) worker.get();
            assertEquals(100, entries.size());
        } finally { pool.shutdownNow(); }
    }
}
