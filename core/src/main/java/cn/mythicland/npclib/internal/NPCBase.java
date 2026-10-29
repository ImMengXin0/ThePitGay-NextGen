/*
 * Copyright (c) 2018 Jitse Boonstra
 */

package cn.mythicland.npclib.internal;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import cn.mythicland.npclib.NPCLib;
import cn.mythicland.npclib.api.NPC;
import cn.mythicland.npclib.api.PacketNpc;
import cn.mythicland.npclib.api.events.NPCHideEvent;
import cn.mythicland.npclib.api.events.NPCShowEvent;
import cn.mythicland.npclib.api.skin.Skin;
import cn.mythicland.npclib.api.state.NPCAnimation;
import cn.mythicland.npclib.api.state.NPCSlot;
import cn.mythicland.npclib.api.state.NPCState;
import cn.mythicland.npclib.hologram.Hologram;
import cn.mythicland.npclib.utilities.MathUtil;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

public abstract class NPCBase implements PacketNpc, NPCPacketHandler {

    protected final int entityId = NPCManager.allocateEntityId();
    protected final Set<UUID> hasTeamRegistered = new ObjectOpenHashSet<>();
    protected final Set<NPCState> activeStates = EnumSet.noneOf(NPCState.class);
    protected final Map<NPCSlot, ItemStack> items = new EnumMap<>(NPCSlot.class);
    // Storage for per-player text;
    protected final Map<UUID, List<String>> playerText = new Object2ObjectOpenHashMap<>();
    protected final Map<UUID, Hologram> playerHologram = new Object2ObjectOpenHashMap<>();
    private final Set<UUID> shown = new ObjectOpenHashSet<>();
    private final Set<UUID> autoHidden = new ObjectOpenHashSet<>();
    protected double cosFOV = Math.cos(Math.toRadians(60));
    // 12/4/20, JMB: Changed the UUID in order to enable LabyMod Emotes:
    // This gives a format similar to: 528086a2-4f5f-2ec2-0000-000000000000
    protected UUID uuid = new UUID(new Random().nextLong(), 0);
    protected String name = uuid.toString().replace("-", "").substring(0, 10);
    protected GameProfile gameProfile = new GameProfile(uuid, name);
    protected boolean created = false;
    private boolean destroyed;
    protected Skin skin;

    // protected Hologram hologram;
    protected NPCLib instance;
    protected List<String> text;
    protected Location location;

    public NPCBase(NPCLib instance, List<String> text) {
        this.instance = instance;
        this.text = text != null ? List.copyOf(text) : Collections.emptyList();
        instance.registerNPC(this);
        NPCManager.add(this);
    }

    public NPCLib getInstance() {
        return instance;
    }

    // Unique per-player hologram by Gatt, modifications and refactoring done by
    // Jitse (JMB - 23rd Jan. 2021).
    protected Hologram getHologram(Player player) {
        Validate.notNull(player, "Player cannot be null.");
        return playerHologram.getOrDefault(player.getUniqueId(),
                null); // If null, NMS version will return new hologram instance.
    }

    @Override
    public NPC removeText(Player player) {
        Validate.notNull(player, "Player cannot be null.");
        setText(player, null);
        return this;
    }

    @Override
    public NPC setText(Player player, List<String> text) {
        Validate.notNull(player, "Player cannot be null.");
        if (destroyed) return this;
        List<String> originalText = getText(player);
        if (text == null) playerText.remove(player.getUniqueId());
        else playerText.put(player.getUniqueId(), List.copyOf(text));
        updateText(player, originalText, getText(player));
        return this;
    }

    private void updateText(Player player, List<String> before, List<String> after) {
        if (before.equals(after)) return;
        Hologram hologram = playerHologram.get(player.getUniqueId());
        if (hologram == null) return; // Created with the latest text when first shown.
        if (before.size() != after.size()) {
            if (isShown(player)) hologram.hide(player);
            playerHologram.remove(player.getUniqueId());
            if (isShown(player)) getHologram(player).show(player);
        } else {
            List<Object> packets = hologram.getUpdatePackets(after);
            if (isShown(player) && !packets.isEmpty()) hologram.update(player, packets);
        }
    }

    @Override
    public NPC setText(List<String> text) {
        List<String> before = this.text;
        this.text = text != null ? List.copyOf(text) : List.of();
        for (UUID uuid : new ArrayList<>(playerHologram.keySet())) {
            if (playerText.containsKey(uuid)) continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            updateText(player, before, this.text);
        }
        return this;
    }

    @Override
    public List<String> getText(Player player) {
        Validate.notNull(player, "Player cannot be null.");
        return playerText.getOrDefault(player.getUniqueId(), text);
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public String getId() {
        return name;
    }

    @Override
    public NPC setSkin(Skin skin) {
        this.skin = skin;
        gameProfile.getProperties().removeAll("textures");
        if (skin != null) {
            gameProfile.getProperties().put(
                    "textures",
                    new Property("textures", skin.value(), skin.signature())
            );
        }
        return this;
    }

    @Override
    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        NPCManager.remove(this);
        this.instance.forgetNPC(this);
        // Shutdown must remove client entities even when a hide event is cancelled.
        for (UUID uuid : new ArrayList<>(shown)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !autoHidden.contains(uuid)) sendHidePackets(player, true);
        }
        created = false;
        shown.clear();
        autoHidden.clear();
        hasTeamRegistered.clear();
        playerText.clear();
        playerHologram.clear();
        items.clear();
    }

    @Override
    public void disableFOV() {
        this.cosFOV = -1.0D;
    }

    public void setFOV(double fov) {
        this.cosFOV = Math.cos(Math.toRadians(fov));
    }

    public Set<UUID> getShown() {
        return shown;
    }

    public Set<UUID> getAutoHidden() {
        return autoHidden;
    }

    @Override
    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    @Override
    public World getWorld() {
        return location != null ? location.getWorld() : null;
    }

    public int getEntityId() {
        return entityId;
    }

    @Override
    public boolean isShown(Player player) {
        Objects.requireNonNull(player, "Player object cannot be null");
        return shown.contains(player.getUniqueId()) && !autoHidden.contains(player.getUniqueId());
    }

    @Override
    public NPC setLocation(Location location) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location world");
        if (destroyed) throw new IllegalStateException("NPC is destroyed");
        if (created) {
            for (UUID id : new ArrayList<>(shown)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && isShown(player)) sendHidePackets(player, true);
            }
            playerHologram.clear();
            autoHidden.addAll(shown);
        }
        this.location = location.clone();
        if (created) {
            createPackets();
            for (UUID id : new ArrayList<>(shown)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && isLocationChunkLoaded() && inRangeOf(player) && inViewOf(player)) show(player, true);
            }
        }
        return this;
    }

    @Override
    public NPC create() {
        if (destroyed) throw new IllegalStateException("NPC is destroyed");
        Objects.requireNonNull(location, "NPC location");
        createPackets();
        this.created = true;
        return this;
    }

    @Override
    public boolean isCreated() {
        return created;
    }

    public void onLogout(Player player) {
        getAutoHidden().remove(player.getUniqueId());
        getShown().remove(player.getUniqueId()); // Don't need to use NPC#hide since the entity is not registered in the
        // NMS server.
        hasTeamRegistered.remove(player.getUniqueId());
        playerText.remove(player.getUniqueId());
        playerHologram.remove(player.getUniqueId());
    }

    public boolean inRangeOf(Player player) {
        if (player == null || location == null) return false;
        if (!player.getWorld().equals(location.getWorld())) {
            // No need to continue our checks, they are in different worlds.
            return false;
        }

        // If Bukkit doesn't track the NPC entity anymore, bypass the hiding distance
        // variable.
        double hideDistance = instance.getAutoHideDistance();
        double distanceSquared = player.getLocation().distanceSquared(location);
        double bukkitRange = Bukkit.getViewDistance() * 16.0;

        return distanceSquared <= MathUtil.squareTwo(hideDistance)
                && distanceSquared <= MathUtil.squareTwo(bukkitRange);
    }

    public boolean inViewOf(Player player) {
        if (this.cosFOV <= -1.0D) return true;
        Vector dir = location.toVector()
                .subtract(player.getEyeLocation().toVector())
                .normalize();
        return dir.dot(player.getEyeLocation().getDirection()) >= cosFOV;
    }

    public boolean isLocationChunkLoaded() {
        if (this.location == null || this.location.getWorld() == null) return false;
        return this.location.getWorld().isChunkLoaded(
                this.location.getBlockX() >> 4,
                this.location.getBlockZ() >> 4
        );
    }

    @Override
    public void show(Player player) {
        show(player, false);
    }

    public void show(Player player, boolean auto) {
        if (!created || destroyed || instance.isShutdown() || !player.isOnline() || player.isDead()) return;
        if (isShown(player)) return;
        if (auto && !shown.contains(player.getUniqueId())) return;
        NPCShowEvent event = new NPCShowEvent(this, player, auto);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        if (auto) {
            sendShowPackets(player);
            sendMetadataPacket(player);
            sendEquipmentPackets(player);

            // NPC is auto-shown now, we can remove the UUID from the set.
            autoHidden.remove(player.getUniqueId());
        } else {
            // Adding the UUID to the set.
            shown.add(player.getUniqueId());

            if (isLocationChunkLoaded() && inRangeOf(player) && inViewOf(player)) {
                // The player can see the NPC and is in range, send the packets.
                sendShowPackets(player);
                sendMetadataPacket(player);
                sendEquipmentPackets(player);
                autoHidden.remove(player.getUniqueId());
            } else {
                // We'll wait until we can show the NPC to the player via auto-show.
                autoHidden.add(player.getUniqueId());
            }
        }
    }

    @Override
    public void hide(Player player) {
        hide(player, false);
    }

    public void hide(Player player, boolean auto) {
        if (!shown.contains(player.getUniqueId()) || (auto && autoHidden.contains(player.getUniqueId()))) return;
        NPCHideEvent event = new NPCHideEvent(this, player, auto);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        if (auto) {
            sendHidePackets(player, true);

            // NPC is auto-hidden now, we will add the UUID to the set.
            autoHidden.add(player.getUniqueId());
        } else {
            if (isShown(player)) sendHidePackets(player, false);
            // Removing the UUID from the set.
            shown.remove(player.getUniqueId());
            autoHidden.remove(player.getUniqueId());
        }
    }

    @Override
    public boolean getState(NPCState state) {
        return activeStates.contains(state);
    }

    @Override
    public NPC toggleState(NPCState state) {
        if (activeStates.contains(state)) {
            activeStates.remove(state);
        } else {
            activeStates.add(state);
        }

        // Send a new metadata packet to all players that can see the NPC.
        for (UUID shownUuid : shown) {
            Player player = Bukkit.getPlayer(shownUuid);
            if (player != null && isShown(player)) {
                sendMetadataPacket(player);
            }
        }
        return this;
    }

    @Override
    public void playAnimation(NPCAnimation animation) {
        for (UUID shownUuid : shown) {
            Player player = Bukkit.getPlayer(shownUuid);
            if (player != null && isShown(player)) {
                sendAnimationPacket(player, animation);
            }
        }
    }

    @Override
    public void playHurtFeedback() {
        this.playAnimation(NPCAnimation.TAKE_DAMAGE);
    }

    @Override
    public ItemStack getItem(NPCSlot slot) {
        Objects.requireNonNull(slot, "Slot cannot be null");

        return items.get(slot);
    }

    @Override
    public NPC setItem(NPCSlot slot, ItemStack item) {
        Objects.requireNonNull(slot, "Slot cannot be null");

        items.put(slot, item);

        for (UUID shownUuid : shown) {
            Player player = Bukkit.getPlayer(shownUuid);
            if (player != null && isShown(player)) {
                sendEquipmentPacket(player, slot, false);
            }
        }
        return this;
    }

    @Override
    public List<String> getText() {
        return text;
    }

    @Override
    public void lookAt(Location location) {
        sendHeadRotationPackets(location);
    }
}
