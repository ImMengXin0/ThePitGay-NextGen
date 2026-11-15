package net.mizukilab.pit.util.nametag;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.PaperTestBootstrap;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class NametagSchedulingTest {
    @SuppressWarnings("deprecation")
    @Test public void modernSchedulerRegistersRunnableAndAllowsCancellation() {
        try (var bukkit = PaperTestBootstrap.mockBukkit()) {
            ThePit plugin = mock(ThePit.class);
            NametagHandler handler = mock(NametagHandler.class);
            when(handler.getPlugin()).thenReturn(plugin);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(plugin), any(BukkitRunnable.class), anyLong(), anyLong()))
                    .thenThrow(new UnsupportedOperationException("Use BukkitRunnable#runTaskTimer(Plugin, long, long)"));
            BukkitTask task = mock(BukkitTask.class);
            when(task.getTaskId()).thenReturn(42);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L))).thenReturn(task);

            NametagThread thread = new NametagThread(handler);
            assertEquals(42, thread.getTaskId());
            thread.cancel();

            verify(scheduler).runTaskTimer(plugin, (Runnable) thread, 0L, 20L);
            verify(scheduler, never()).runTaskTimer(eq(plugin), any(BukkitRunnable.class), anyLong(), anyLong());
            verify(scheduler).cancelTask(42);
        }
    }
}
