package cn.mythicland.framework.path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NavigationProgressTest {
    @Test
    void detectsStuckAfterOneHundredMovementUpdates() {
        NavigationProgress progress = new NavigationProgress(100, 2.25);
        assertFalse(progress.isStuck(0, 64, 0));
        for (int update = 0; update < 100; update++) assertFalse(progress.isStuck(0, 64, 0));
        assertTrue(progress.isStuck(0, 64, 0));
    }

    @Test
    void acceptsMovementAndStartsAnotherObservationWindow() {
        NavigationProgress progress = new NavigationProgress(100, 2.25);
        assertFalse(progress.isStuck(0, 64, 0));
        for (int update = 0; update < 100; update++) assertFalse(progress.isStuck(0, 64, 0));
        assertFalse(progress.isStuck(1.5, 64, 0));
        for (int update = 0; update < 100; update++) assertFalse(progress.isStuck(1.5, 64, 0));
        assertTrue(progress.isStuck(1.5, 64, 0));
    }

    @Test
    void resetDiscardsPreviousMovementAndElapsedUpdates() {
        NavigationProgress progress = new NavigationProgress(100, 2.25);
        assertFalse(progress.isStuck(0, 64, 0));
        for (int update = 0; update < 90; update++) assertFalse(progress.isStuck(0, 64, 0));
        progress.reset();
        assertFalse(progress.isStuck(100, 80, 100));
        for (int update = 0; update < 100; update++) assertFalse(progress.isStuck(100, 80, 100));
        assertTrue(progress.isStuck(100, 80, 100));
    }

    @Test
    void rejectsInvalidObservationParameters() {
        assertThrows(IllegalArgumentException.class, () -> new NavigationProgress(0, 2.25));
        for (double distance : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new NavigationProgress(100, distance));
    }
}
