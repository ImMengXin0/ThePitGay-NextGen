package net.mizukilab.pit.npc;

import cn.charlotte.pit.ThePit;
import org.bukkit.*;
import org.bukkit.entity.Bat;
import org.bukkit.scheduler.*;
import org.junit.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class NpcTaskPerformanceTest {
    @Test public void invalidEntityAndReloadReleasePositionTasks() throws Exception {
        BukkitScheduler scheduler=mock(BukkitScheduler.class);
        try(var pit=mockStatic(ThePit.class);
            var bukkit=new net.mizukilab.pit.OnlineServerTestContext(scheduler,mock(org.bukkit.plugin.PluginManager.class))) {
            pit.when(ThePit::getInstance).thenReturn(mock(ThePit.class));
            List<Runnable> frames=new ArrayList<>(); List<BukkitTask> tasks=new ArrayList<>();
            when(scheduler.runTaskTimer(any(),any(Runnable.class),eq(1L),eq(5L))).thenAnswer(call -> {
                frames.add(call.getArgument(1)); BukkitTask task=mock(BukkitTask.class); tasks.add(task); return task;
            });
            Bat bat=mock(Bat.class); when(bat.getUniqueId()).thenReturn(UUID.randomUUID());
            World world=mock(World.class); Location origin=new Location(world,1,2,3);
            when(bat.getLocation()).thenAnswer(call -> origin.clone()); when(bat.isValid()).thenReturn(true);
            CustomEntityNPCFactory factory=new CustomEntityNPCFactory(); factory.trackFixedPosition(bat);
            frames.get(0).run(); verify(bat,never()).teleport(any(Location.class));
            when(bat.getLocation()).thenReturn(new Location(mock(World.class),1,2,3));
            frames.get(0).run(); verify(bat).teleport(origin);
            when(bat.isValid()).thenReturn(false); frames.get(0).run(); verify(tasks.get(0)).cancel();
            factory.trackFixedPosition(bat); factory.trackFixedPosition(bat); verify(tasks.get(1)).cancel();
            factory.reload(); verify(tasks.get(2)).cancel(); assertEquals(3,tasks.size());
        }
    }
}
