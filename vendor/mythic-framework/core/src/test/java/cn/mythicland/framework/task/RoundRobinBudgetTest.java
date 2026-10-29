package cn.mythicland.framework.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinBudgetTest {
    @Test
    void preservesRatiosAndFairnessIncludingOneActor() {
        for (double multiplier : new double[]{1, .8, .5, .1}) {
            for (int size : new int[]{1, 7, 73, 300}) {
                RoundRobinBudget budget = new RoundRobinBudget(0);
                int[] counts = new int[size];
                int total = 0;
                for (int tick = 0; tick < 1000; tick++) {
                    int updates = budget.begin(size, multiplier);
                    assertTrue(updates >= 0 && updates <= size);
                    total += updates;
                    for (int update = 0; update < updates; update++) counts[budget.next(size)]++;
                }
                assertEquals((int) (size * multiplier * 1000), total);
                for (int count : counts) assertTrue(Math.abs(count - multiplier * 1000) <= 1);
            }
        }
    }

    @Test
    void membershipChangesAndSleepDoNotAccumulateCatchUp() {
        RoundRobinBudget budget = new RoundRobinBudget(0);
        assertEquals(1, budget.begin(3, .5));
        assertEquals(0, budget.next(3));
        assertEquals(0, budget.next(1));
        budget.reset();
        assertEquals(0, budget.begin(1, .5));
        assertEquals(1, budget.begin(1, .5));
        assertEquals(0, budget.begin(0, .5));
        assertEquals(0, budget.begin(1, .5));
        assertEquals(3, budget.begin(3, 2));
    }

    @Test
    void phasesDifferAndInvalidMultipliersFail() {
        assertNotEquals(new RoundRobinBudget(0).begin(1, .5), new RoundRobinBudget(900).begin(1, .5));
        assertNotEquals(new RoundRobinBudget(1).begin(1, .5), new RoundRobinBudget(2).begin(1, .5));
        for (double value : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new RoundRobinBudget(0).begin(1, value));
        }
    }

    @Test
    void reloadKeepsRotationAndSkippedTicksAddNoBudget() {
        RoundRobinBudget budget = new RoundRobinBudget(0);
        assertEquals(1, budget.begin(3, .5));
        assertEquals(0, budget.next(3));
        budget.reset();
        assertEquals(1, budget.begin(3, .5));
        assertEquals(1, budget.next(3));
        assertEquals(2, budget.begin(3, .5));
        assertEquals(2, budget.next(3));
        assertEquals(0, budget.next(3));
    }
}
