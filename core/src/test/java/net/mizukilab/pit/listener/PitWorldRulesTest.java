package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.PaperTestBootstrap;
import net.mizukilab.pit.config.ConfigManager;
import net.mizukilab.pit.config.PitWorldConfig;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.WorldLoadEvent;
import org.junit.*;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PitWorldRulesTest {
    private MockedStatic<ThePit> pit;
    private MockedStatic<Bukkit> bukkit;
    private final World arena = mock(World.class), nextMap = mock(World.class), other = mock(World.class);
    private PitWorldRules rules;

    @Before public void setUp() {
        pit = mockStatic(ThePit.class);
        bukkit = PaperTestBootstrap.mockBukkit();
        ThePit plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        ConfigManager configs = mock(ConfigManager.class);
        when(plugin.getConfigManager()).thenReturn(configs);
        PitWorldConfig first = mock(PitWorldConfig.class), second = mock(PitWorldConfig.class);
        when(first.getWorldName()).thenReturn("arena");
        when(second.getWorldName()).thenReturn("next");
        when(configs.getPitConfigs()).thenReturn(Map.of(1, first, 2, second));
        when(arena.getName()).thenReturn("arena");
        when(nextMap.getName()).thenReturn("next");
        when(other.getName()).thenReturn("survival");
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of(arena, other));
        rules = new PitWorldRules();
    }

    @After public void close() {
        bukkit.close();
        pit.close();
    }

    @Test public void appliesAtStartupAndWorldLoadToConfiguredMapsOnly() {
        rules.onWorldLoad(new WorldLoadEvent(nextMap));
        rules.onWorldLoad(new WorldLoadEvent(other));
        for (World world : List.of(arena, nextMap)) {
            verify(world).setGameRule(GameRule.DO_MOB_SPAWNING, false);
            verify(world).setGameRule(GameRule.NATURAL_REGENERATION, false);
            verify(world).setGameRule(GameRule.KEEP_INVENTORY, true);
            verify(world, never()).getEntities();
        }
        verify(other, never()).setGameRule(any(), any());
    }

    @Test public void blocksAllVanillaSpawnReasonsAndPreservesPluginSpawns() {
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getWorld()).thenReturn(arena);
        for (CreatureSpawnEvent.SpawnReason reason : CreatureSpawnEvent.SpawnReason.values()) {
            CreatureSpawnEvent event = new CreatureSpawnEvent(mob, reason);
            rules.onCreatureSpawn(event);
            assertEquals(reason.name(), reason != CreatureSpawnEvent.SpawnReason.CUSTOM, event.isCancelled());
        }
        when(mob.getWorld()).thenReturn(other);
        CreatureSpawnEvent event = new CreatureSpawnEvent(mob, CreatureSpawnEvent.SpawnReason.NATURAL);
        rules.onCreatureSpawn(event);
        assertFalse(event.isCancelled());
    }

    @Test public void cancelsOnlyNaturalPlayerHealingAndKeepsPotionsAndPitHealing() {
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(arena);
        for (EntityRegainHealthEvent.RegainReason reason : EntityRegainHealthEvent.RegainReason.values()) {
            EntityRegainHealthEvent event = new EntityRegainHealthEvent(player, 1, reason);
            rules.onRegainHealth(event);
            assertEquals(reason.name(), reason == EntityRegainHealthEvent.RegainReason.SATIATED
                    || reason == EntityRegainHealthEvent.RegainReason.REGEN, event.isCancelled());
        }
        when(player.getWorld()).thenReturn(other);
        EntityRegainHealthEvent outside = new EntityRegainHealthEvent(player, 1, EntityRegainHealthEvent.RegainReason.SATIATED);
        rules.onRegainHealth(outside);
        assertFalse(outside.isCancelled());
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getWorld()).thenReturn(arena);
        EntityRegainHealthEvent pet = new EntityRegainHealthEvent(mob, 1, EntityRegainHealthEvent.RegainReason.REGEN);
        rules.onRegainHealth(pet);
        assertFalse(pet.isCancelled());
    }

    @Test public void coinMergeProtectionPreservesRewardsInBothMergeDirections() {
        Item source = mock(Item.class), target = mock(Item.class);
        when(source.getWorld()).thenReturn(arena);
        ItemMergeEvent vanilla = new ItemMergeEvent(source, target);
        rules.onItemMerge(vanilla);
        assertFalse(vanilla.isCancelled());
        when(source.hasMetadata("gold")).thenReturn(true);
        ItemMergeEvent coinSource = new ItemMergeEvent(source, target);
        rules.onItemMerge(coinSource);
        assertTrue(coinSource.isCancelled());
        when(source.hasMetadata("gold")).thenReturn(false);
        when(target.hasMetadata("gold")).thenReturn(true);
        ItemMergeEvent coinTarget = new ItemMergeEvent(source, target);
        rules.onItemMerge(coinTarget);
        assertTrue(coinTarget.isCancelled());
        when(source.getWorld()).thenReturn(other);
        ItemMergeEvent outside = new ItemMergeEvent(source, target);
        rules.onItemMerge(outside);
        assertFalse(outside.isCancelled());
    }
}
