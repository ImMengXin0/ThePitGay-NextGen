package cn.mythicland.framework.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StaggeredDeadlineTest {
    @Test
    void preservesPhaseAndSchedulesAfterLateChecks() {
        assertEquals(9, StaggeredDeadline.after(0, 1, 10));
        assertEquals(19, StaggeredDeadline.after(9, 1, 10));
        assertEquals(19, StaggeredDeadline.after(15, 1, 10));
        assertEquals(29, StaggeredDeadline.after(20, 1, 10));
        assertEquals(11, StaggeredDeadline.after(10, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> StaggeredDeadline.after(0, 1, 0));
    }
}
