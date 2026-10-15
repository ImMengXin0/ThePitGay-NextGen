package net.mizukilab.pit.enchantment.type.rare;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DemonHenCleanupTest {
    @Test
    public void expiresAndCleansDeathAndDisableOnServerTimer() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            BukkitTask task = mock(BukkitTask.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L))).thenReturn(task);
            DemonHenEnchant enchant = new DemonHenEnchant();
            verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L));
            Player owner = mock(Player.class);
            when(owner.isOnline()).thenReturn(true);
            LivingEntity expired = mock(LivingEntity.class);
            when(expired.isValid()).thenReturn(true);
            when(expired.getTicksLived()).thenReturn(300);
            enchant.entitySet.add(Map.entry(owner, expired));
            enchant.tickHens();
            verify(expired).remove();
            assertTrue(enchant.entitySet.isEmpty());

            LivingEntity killed = mock(LivingEntity.class);
            enchant.entitySet.add(Map.entry(owner, killed));
            ArrayList<ItemStack> drops = new ArrayList<>();
            drops.add(mock(ItemStack.class));
            EntityDeathEvent death = new EntityDeathEvent(killed, drops, 5);
            enchant.onHenDeath(death);
            assertTrue(drops.isEmpty());
            assertEquals(0, death.getDroppedExp());
            assertTrue(enchant.entitySet.isEmpty());

            LivingEntity remaining = mock(LivingEntity.class);
            enchant.entitySet.add(Map.entry(owner, remaining));
            enchant.onDisable(new PluginDisableEvent(plugin));
            verify(task).cancel();
            verify(remaining).remove();
            assertTrue(enchant.entitySet.isEmpty());
        }
    }
}
