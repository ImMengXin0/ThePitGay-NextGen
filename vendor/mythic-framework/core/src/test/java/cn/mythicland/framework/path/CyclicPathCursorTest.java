package cn.mythicland.framework.path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CyclicPathCursorTest {
    @Test
    void resumesAtSelectedPointAndLoopsInOriginalOrder() {
        CyclicPathCursor cursor = new CyclicPathCursor();
        cursor.reset(2);
        for (int index : new int[]{2, 3, 0, 1, 2}) {
            assertEquals(index, cursor.target(4));
            cursor.advance(4);
        }
        cursor.reset(0);
        cursor.advance(1);
        cursor.reset(0);
        assertEquals(0, cursor.target(1));
        assertThrows(IllegalArgumentException.class, () -> cursor.reset(-1));
    }

    @Test
    void loopsInOrderAndStopsAtSinglePoint() {
        CyclicPathCursor cursor = new CyclicPathCursor();
        assertEquals(-1, cursor.target(0));
        for (int step = 0; step < 10; step++) {
            assertEquals(step % 3, cursor.target(3));
            cursor.advance(3);
        }
        cursor.reset();
        assertEquals(0, cursor.target(1));
        cursor.advance(1);
        assertEquals(-1, cursor.target(1));
        cursor.reset();
        assertEquals(0, cursor.target(1));
    }
}
