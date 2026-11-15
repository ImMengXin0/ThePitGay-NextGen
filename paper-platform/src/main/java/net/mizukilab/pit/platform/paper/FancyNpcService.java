package net.mizukilab.pit.platform.paper;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.data.property.NpcVisibility;
import de.oliver.fancynpcs.api.skins.SkinData;
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** FancyNpcs owns the visual entities; the game owns interaction and individual text. */
public final class FancyNpcService implements Listener, AutoCloseable {
    private final JavaPlugin owner;
    private final FancyNpcsPlugin fancy;
    private final FancyTextService texts;
    private final Set<NpcHandle> handles = new LinkedHashSet<>();
    private final BukkitTask refreshTask;
    private boolean closed;

    public FancyNpcService(JavaPlugin owner, FancyTextService texts) {
        FancyTextService.requireMainThread();
        this.owner = Objects.requireNonNull(owner);
        this.texts = Objects.requireNonNull(texts);
        this.fancy = FancyNpcsPlugin.get();
        Bukkit.getPluginManager().registerEvents(this, owner);
        refreshTask = Bukkit.getScheduler().runTaskTimer(owner,
                () -> List.copyOf(handles).forEach(NpcHandle::refresh), 5L, 5L);
    }

    public NpcHandle create(String name, Location location, EntityType type, Consumer<Player> interaction) {
        FancyTextService.requireMainThread();
        if (closed) throw new IllegalStateException("NPC service is closed");
        Objects.requireNonNull(location.getWorld(), "NPC world");
        NpcData data = new NpcData("thepit_" + name + "_" + UUID.randomUUID(),
                new UUID(0, 0), location.clone());
        data.setType(type);
        data.setDisplayName("");
        data.setShowInTab(false);
        data.setCollidable(false);
        data.setTurnToPlayer(false);
        data.setVisibilityDistance(50);
        data.setVisibility(NpcVisibility.MANUAL);
        data.setInteractionCooldown(0.25f);
        Npc npc = fancy.getNpcAdapter().apply(data);
        npc.setSaveToFile(false);
        NpcHandle handle = new NpcHandle(npc);
        data.setOnClick(player -> {
            Runnable click = () -> {
                if (!owner.isEnabled() || handle.deleted || !handle.isShown(player)
                        || !player.isOnline() || player.isDead()) return;
                Location target = handle.location();
                if (player.getWorld().equals(target.getWorld())
                        && player.getEyeLocation().distanceSquared(target) <= 36.0) interaction.accept(player);
            };
            if (Bukkit.isPrimaryThread()) click.run();
            else if (owner.isEnabled()) Bukkit.getScheduler().runTask(owner, click);
        });
        try {
            npc.create();
            fancy.getNpcManager().registerNpc(npc);
            handles.add(handle);
            return handle;
        } catch (RuntimeException failure) {
            handle.close();
            throw failure;
        }
    }

    public boolean ownsEntity(int entityId) {
        return handles.stream().anyMatch(handle -> handle.entityId() == entityId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        for (NpcHandle handle : handles) {
            handle.viewers.forget(id);
            handle.shown.remove(id);
            handle.npc.getIsVisibleForPlayer().remove(id);
            handle.npc.getIsTeamCreated().remove(id);
            handle.npc.getIsLookingAtPlayer().remove(id);
            handle.npc.getLastPlayerInteraction().remove(id);
            NpcVisibility.ManualNpcVisibility.removeDistantViewer(handle.npc, id);
            FancyTextService.TextHandle text = handle.personalText.remove(id);
            if (text != null) text.close();
        }
    }

    @Override
    public void close() {
        FancyTextService.requireMainThread();
        if (closed) return;
        closed = true;
        refreshTask.cancel();
        for (NpcHandle handle : List.copyOf(handles)) handle.close();
        HandlerList.unregisterAll(this);
    }

    public final class NpcHandle implements AutoCloseable {
        private final Npc npc;
        private final ViewerPolicy viewers = new ViewerPolicy();
        private final Set<UUID> shown = new HashSet<>();
        private final Map<UUID, FancyTextService.TextHandle> personalText = new HashMap<>();
        private boolean deleted;

        private NpcHandle(Npc npc) { this.npc = npc; }

        public int entityId() { return npc.getEntityId(); }
        public void setAttribute(String name, String value) {
            FancyTextService.requireMainThread();
            if (deleted) return;
            var attribute = fancy.getAttributeManager().getAttributeByName(npc.getData().getType(), name);
            if (attribute == null) throw new IllegalArgumentException("Unsupported NPC attribute: " + name);
            npc.getData().addAttribute(attribute, value);
            npc.updateForAll(true);
        }
        public Location location() { return npc.getData().getLocation().clone(); }
        public boolean isShown(Player player) { return shown.contains(player.getUniqueId()) && npc.isShownFor(player); }

        public void setSkin(String texture, String signature) {
            FancyTextService.requireMainThread();
            if (deleted) return;
            npc.getData().setSkinData(texture == null ? null
                    : new SkinData("thepit:" + npc.getData().getId(), SkinData.SkinVariant.AUTO, texture, signature));
            npc.updateForAll(true);
        }

        public void setItem(String slot, ItemStack item) {
            FancyTextService.requireMainThread();
            if (deleted) return;
            NpcEquipmentSlot equipmentSlot = NpcEquipmentSlot.valueOf(slot);
            if (item == null) npc.getData().getEquipment().remove(equipmentSlot);
            else npc.getData().addEquipment(equipmentSlot, item.clone());
            npc.updateForAll();
        }

        public void setText(Player player, List<String> lines) {
            FancyTextService.requireMainThread();
            if (deleted) return;
            UUID id = player.getUniqueId();
            if (lines == null || lines.isEmpty()) {
                FancyTextService.TextHandle old = personalText.remove(id);
                if (old != null) old.close();
                return;
            }
            FancyTextService.TextHandle text = personalText.get(id);
            if (text == null) {
                text = texts.create(textLocation(), lines);
                personalText.put(id, text);
            } else text.setText(lines);
            if (shown.contains(id)) text.showOnly(List.of(player));
        }

        public void show(Player player) {
            FancyTextService.requireMainThread();
            if (deleted) return;
            viewers.show(player.getUniqueId());
            refresh();
        }

        public void hide(Player player) {
            FancyTextService.requireMainThread();
            if (deleted) return;
            viewers.hide(player.getUniqueId());
            refresh();
        }

        public void teleport(Location location) {
            FancyTextService.requireMainThread();
            if (deleted) return;
            Objects.requireNonNull(location.getWorld(), "NPC world");
            boolean changedWorld = !location.getWorld().equals(location().getWorld());
            if (changedWorld) for (Player player : Bukkit.getOnlinePlayers()) hideForViewer(player);
            npc.getData().setLocation(location.clone());
            npc.moveForAll(true);
            for (FancyTextService.TextHandle text : personalText.values()) text.setLocation(textLocation());
            refresh();
        }

        public void lookAt(Location location) {
            FancyTextService.requireMainThread();
            if (deleted) return;
            for (UUID id : shown) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) npc.lookAt(player, location);
            }
        }

        private Location textLocation() { return location().add(0, npc.getEyeHeight() + 0.5, 0); }

        private void refresh() {
            FancyTextService.requireMainThread();
            if (deleted) return;
            Location location = location();
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID id = player.getUniqueId();
                boolean visible = viewers.accepts(id) && !player.isDead()
                        && player.getWorld().equals(location.getWorld())
                        && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                        && player.getLocation().distanceSquared(location) <= 2500.0;
                if (visible) {
                    NpcVisibility.ManualNpcVisibility.addDistantViewer(npc, id);
                    if (shown.add(id) || !npc.isShownFor(player)) npc.spawn(player);
                    FancyTextService.TextHandle text = personalText.get(id);
                    if (text != null) text.showOnly(List.of(player));
                } else hideForViewer(player);
            }
        }

        private void hideForViewer(Player player) {
            UUID id = player.getUniqueId();
            NpcVisibility.ManualNpcVisibility.removeDistantViewer(npc, id);
            if (shown.remove(id)) npc.remove(player);
            FancyTextService.TextHandle text = personalText.get(id);
            if (text != null) text.hide(player);
        }

        @Override
        public void close() {
            FancyTextService.requireMainThread();
            if (deleted) return;
            deleted = true;
            for (Player player : Bukkit.getOnlinePlayers()) hideForViewer(player);
            personalText.values().forEach(FancyTextService.TextHandle::close);
            personalText.clear();
            viewers.clear();
            NpcVisibility.ManualNpcVisibility.remove(npc);
            fancy.getNpcManager().removeNpc(npc);
            npc.removeForAll();
            handles.remove(this);
        }
    }
}
