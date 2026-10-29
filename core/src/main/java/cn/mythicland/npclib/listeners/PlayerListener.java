/* Copyright (c) 2018 Jitse Boonstra. */
package cn.mythicland.npclib.listeners;

import cn.mythicland.npclib.NPCLib;
import cn.mythicland.npclib.internal.NPCBase;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** All viewer state changes run on the Bukkit thread and belong to one library. */
public class PlayerListener extends HandleMoveBase implements Listener {
    private final Map<UUID, BukkitTask> pending = new HashMap<>();

    public PlayerListener(NPCLib instance) { super(instance); }

    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) {
        instance.forgetViewer(event.getPlayer());
        BukkitTask task = pending.remove(event.getPlayer().getUniqueId());
        if (task != null) task.cancel();
        for (NPCBase npc : instance.getNpcs()) npc.onLogout(event.getPlayer());
    }

    @EventHandler public void onPlayerDeath(PlayerDeathEvent event) {
        hideVisible(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        hideVisible(event.getPlayer());
        refreshLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // The client discards entities on dimension change. Mark all of them hidden
        // even when the destination is already within the NPC's visibility range.
        hideVisible(event.getPlayer());
        refreshLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // PlayerTeleportEvent fires before the player has actually moved.
        refreshLater(event.getPlayer());
    }

    private void hideVisible(Player player) {
        for (NPCBase npc : instance.getNpcs()) {
            if (npc.isShown(player)) npc.hide(player, true);
        }
    }

    private void refreshLater(Player player) {
        if (instance.isShutdown()) return;
        UUID id = player.getUniqueId();
        BukkitTask previous = pending.remove(id);
        if (previous != null) previous.cancel();
        pending.put(id, instance.getPlugin().getServer().getScheduler().runTask(instance.getPlugin(), () -> {
            pending.remove(id);
            if (instance.isShutdown() || !player.isOnline()) return;
            instance.showAutomaticNpcs(player);
            handleMove(player);
        }));
    }

    public void shutdown() {
        for (BukkitTask task : pending.values()) task.cancel();
        pending.clear();
    }
}
