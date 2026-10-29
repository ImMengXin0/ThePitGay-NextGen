/* Copyright (c) 2018 Jitse Boonstra. */
package cn.mythicland.npclib.listeners;

import cn.mythicland.npclib.NPCLib;
import cn.mythicland.npclib.api.events.NPCInteractEvent;
import cn.mythicland.npclib.internal.NPCBase;
import cn.mythicland.npclib.internal.NPCManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PacketListener {
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private NPCLib instance;
    private ProtocolManager protocol;
    private PacketAdapter adapter;
    private volatile boolean active;

    public void start(NPCLib instance) {
        this.instance = instance;
        this.protocol = ProtocolLibrary.getProtocolManager();
        active = true;
        adapter = new PacketAdapter(instance.getPlugin(), PacketType.Play.Client.USE_ENTITY) {
            @Override public void onPacketReceiving(PacketEvent event) {
                if (event.isCancelled() || !active) return;
                Integer id = event.getPacket().getIntegers().readSafely(0);
                EnumWrappers.EntityUseAction action = event.getPacket().getEntityUseActions().readSafely(0);
                if (id == null || action == null) return;
                handleInteract(event.getPlayer(), id, action == EnumWrappers.EntityUseAction.ATTACK);
            }
        };
        protocol.addPacketListener(adapter);
    }

    void handleInteract(Player player, int entityId, boolean attack) {
        if (!active) return;
        // Only the concurrent entity-ID registry is consulted off the Bukkit thread.
        NPCBase npc = NPCManager.getByEntityId(entityId);
        if (npc == null || npc.getInstance() != instance) return;
        UUID id = player.getUniqueId();
        if (!pending.add(id)) return;
        Runnable dispatch = () -> {
            try {
                if (!active || instance.isShutdown() || !player.isOnline() || player.isDead()
                        || NPCManager.getByEntityId(entityId) != npc || !npc.isCreated() || !npc.isShown(player)
                        || !player.getWorld().equals(npc.getWorld())
                        || player.getLocation().distanceSquared(npc.getLocation()) > 64) return;
                long now = System.nanoTime();
                Long previous = lastClick.get(id);
                if (previous != null && now - previous < TimeUnit.MILLISECONDS.toNanos(100)) return;
                lastClick.put(id, now);
                Bukkit.getPluginManager().callEvent(new NPCInteractEvent(player, attack
                        ? NPCInteractEvent.ClickType.LEFT_CLICK : NPCInteractEvent.ClickType.RIGHT_CLICK, npc));
            } finally {
                pending.remove(id);
            }
        };
        if (Bukkit.isPrimaryThread()) dispatch.run();
        else {
            try { Bukkit.getScheduler().runTask(instance.getPlugin(), dispatch); }
            catch (org.bukkit.plugin.IllegalPluginAccessException disabled) { pending.remove(id); }
        }
    }

    public void forget(Player player) {
        pending.remove(player.getUniqueId());
        lastClick.remove(player.getUniqueId());
    }

    public void stop() {
        active = false;
        if (adapter != null) protocol.removePacketListener(adapter);
        adapter = null;
        pending.clear();
        lastClick.clear();
    }
}
