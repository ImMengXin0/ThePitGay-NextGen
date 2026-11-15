package net.mizukilab.pit.listener;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.world.WorldLoadEvent;

/** Rules for configured Pit worlds, including maps which are not currently selected. */
public final class PitWorldRules implements Listener {
    public PitWorldRules() {
        Bukkit.getWorlds().forEach(PitWorldRules::apply);
    }

    public static boolean isPitWorld(World world) {
        ThePit plugin = ThePit.getInstance();
        if (world == null || plugin == null || plugin.getConfigManager() == null) return false;
        return plugin.getConfigManager().getPitConfigs().values().stream()
                .anyMatch(config -> world.getName().equals(config.getWorldName()));
    }

    public static void apply(World world) {
        if (!isPitWorld(world)) return;
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Pets, skill effects and plugin NPCs use CUSTOM; block vanilla spawn paths.
        if (isPitWorld(event.getEntity().getWorld())
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player) || !isPitWorld(event.getEntity().getWorld())) return;
        switch (event.getRegainReason()) {
            case SATIATED, REGEN -> event.setCancelled(true);
            default -> { } // Potion, golden apple and Pit healing keep their existing behavior.
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        // Each coin carries its own reward and lifetime; merging would discard metadata.
        if (isPitWorld(event.getEntity().getWorld())
                && (event.getEntity().hasMetadata("gold") || event.getTarget().hasMetadata("gold"))) {
            event.setCancelled(true);
        }
    }
}
