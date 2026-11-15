/* Copyright (c) 2018 Jitse Boonstra. High platform adaptation. */
package cn.mythicland.npclib.internal;

import cn.charlotte.pit.ThePit;
import cn.mythicland.npclib.NPCLib;
import cn.mythicland.npclib.api.NPC;
import cn.mythicland.npclib.api.PacketNpc;
import cn.mythicland.npclib.api.events.NPCHideEvent;
import cn.mythicland.npclib.api.events.NPCShowEvent;
import cn.mythicland.npclib.api.skin.Skin;
import cn.mythicland.npclib.api.state.*;
import net.mizukilab.pit.platform.paper.FancyNpcService.NpcHandle;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Stable game API with FancyNpcs responsible for all visual NPC entities. */
public abstract class NPCBase implements PacketNpc {
    protected final NPCLib instance;
    protected UUID uuid = UUID.randomUUID();
    protected String name = uuid.toString().replace("-", "").substring(0, 10);
    protected int entityId = NPCManager.allocateEntityId();
    protected Skin skin;
    protected Location location;
    protected List<String> text;
    protected EntityType type = EntityType.PLAYER;
    protected org.bukkit.entity.Villager.Profession profession;
    protected final Map<NPCSlot, ItemStack> items = new EnumMap<>(NPCSlot.class);
    protected final Set<NPCState> activeStates = EnumSet.noneOf(NPCState.class);
    private final Map<UUID, List<String>> playerText = new HashMap<>();
    private final Set<UUID> shown = new HashSet<>(), autoHidden = new HashSet<>();
    private double cosFOV = -1;
    private NpcHandle handle;
    private boolean destroyed;
    public NPCBase(NPCLib instance, List<String> text) {
        this.instance = Objects.requireNonNull(instance);
        this.text = text == null ? List.of() : List.copyOf(text);
        instance.registerNPC(this);
        NPCManager.add(this);
    }
    private void requireMain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("NPC changes require the main thread");
    }
    public NPCLib getInstance() { return instance; }
    public void setEntityType(EntityType type) {
        requireMain();
        if (handle != null || destroyed) throw new IllegalStateException("NPC type must be set before creation");
        this.type = Objects.requireNonNull(type);
    }
    public void setProfession(org.bukkit.entity.Villager.Profession profession) {
        requireMain();
        this.profession = profession;
        if (handle != null && profession != null) handle.setAttribute("profession", profession.getKey().toString());
    }
    public int getEntityId() { return entityId; }
    public Set<UUID> getShown() { return shown; }
    public Set<UUID> getAutoHidden() { return autoHidden; }
    @Override public UUID getUniqueId() { return uuid; }
    @Override public String getId() { return name; }
    @Override public Location getLocation() { return location == null ? null : location.clone(); }
    @Override public World getWorld() { return location == null ? null : location.getWorld(); }
    @Override public NPC setLocation(Location location) {
        requireMain();
        if (destroyed) throw new IllegalStateException("NPC destroyed");
        Objects.requireNonNull(location.getWorld(), "NPC world");
        this.location = location.clone();
        if (handle != null) handle.teleport(this.location.clone());
        return this;
    }
    @Override public void teleport(Location location) { setLocation(location); }
    @Override public NPC create() {
        requireMain();
        if (destroyed) throw new IllegalStateException("NPC destroyed");
        if (handle != null) return this;
        Objects.requireNonNull(location, "NPC location");
        handle = ThePit.getInstance().getFancyNpcs().create(name, location, type, player -> {});
        NPCManager.remove(this);
        entityId = handle.entityId();
        NPCManager.add(this);
        if (profession != null) handle.setAttribute("profession", profession.getKey().toString());
        if (skin != null) handle.setSkin(skin.value(), skin.signature());
        items.forEach((slot, item) -> handle.setItem(slot.name(), item));
        for (NPCState state : activeStates) applyState(state);
        return this;
    }
    @Override public boolean isCreated() { return handle != null && !destroyed; }
    @Override public boolean isShown(Player player) {
        return isCreated() && shown.contains(player.getUniqueId()) && !autoHidden.contains(player.getUniqueId()) && handle.isShown(player);
    }
    @Override public NPC setSkin(Skin skin) {
        requireMain(); this.skin = skin;
        if (handle != null) handle.setSkin(skin == null ? null : skin.value(), skin == null ? null : skin.signature());
        return this;
    }
    @Override public void updateSkin(Skin skin) { setSkin(skin); }
    @Override public NPC setText(Player player, List<String> text) {
        requireMain();
        if (text == null) playerText.remove(player.getUniqueId());
        else playerText.put(player.getUniqueId(), List.copyOf(text));
        if (handle != null) handle.setText(player, getText(player));
        return this;
    }
    @Override public NPC removeText(Player player) { return setText(player, null); }
    @Override public List<String> getText(Player player) { return playerText.getOrDefault(player.getUniqueId(), text); }
    @Override public List<String> getText() { return text; }
    @Override public NPC setText(List<String> text) {
        requireMain(); this.text = text == null ? List.of() : List.copyOf(text);
        if (handle != null) for (UUID id : shown) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && !playerText.containsKey(id)) handle.setText(player, this.text);
        }
        return this;
    }
    @Override public NPC setItem(NPCSlot slot, ItemStack item) {
        requireMain();
        if (item == null) items.remove(slot); else items.put(slot, item.clone());
        if (handle != null) handle.setItem(slot.name(), item);
        return this;
    }
    @Override public ItemStack getItem(NPCSlot slot) { ItemStack item = items.get(slot); return item == null ? null : item.clone(); }
    @Override public void disableFOV() { cosFOV = -1; }
    public void setFOV(double fov) { cosFOV = Math.cos(Math.toRadians(fov)); }
    public boolean inRangeOf(Player player) {
        return location != null && player.getWorld().equals(location.getWorld())
                && player.getLocation().distanceSquared(location) <= Math.pow(instance.getAutoHideDistance(), 2);
    }
    public boolean inViewOf(Player player) {
        return cosFOV <= -1 || location.toVector().subtract(player.getEyeLocation().toVector()).normalize()
                .dot(player.getEyeLocation().getDirection()) >= cosFOV;
    }
    public boolean isLocationChunkLoaded() {
        return location != null && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
    @Override public void show(Player player) { show(player, false); }
    public void show(Player player, boolean auto) {
        requireMain();
        if (!isCreated() || instance.isShutdown() || !player.isOnline() || player.isDead()) return;
        if (auto && !shown.contains(player.getUniqueId())) return;
        if (isShown(player)) return;
        NPCShowEvent event = new NPCShowEvent(this, player, auto);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        shown.add(player.getUniqueId());
        if (inRangeOf(player) && inViewOf(player) && isLocationChunkLoaded()) {
            autoHidden.remove(player.getUniqueId());
            handle.setText(player, getText(player));
            handle.show(player);
        } else autoHidden.add(player.getUniqueId());
    }
    @Override public void hide(Player player) { hide(player, false); }
    public void hide(Player player, boolean auto) {
        requireMain();
        if (!isCreated() || !shown.contains(player.getUniqueId()) || (auto && autoHidden.contains(player.getUniqueId()))) return;
        NPCHideEvent event = new NPCHideEvent(this, player, auto);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        handle.hide(player);
        if (auto) autoHidden.add(player.getUniqueId());
        else { shown.remove(player.getUniqueId()); autoHidden.remove(player.getUniqueId()); }
    }
    public void onLogout(Player player) {
        requireMain();
        if (handle != null) { handle.hide(player); handle.setText(player, null); }
        shown.remove(player.getUniqueId()); autoHidden.remove(player.getUniqueId()); playerText.remove(player.getUniqueId());
    }
    @Override public boolean getState(NPCState state) { return activeStates.contains(state); }
    @Override public NPC toggleState(NPCState state) {
        requireMain();
        if (!activeStates.remove(state)) activeStates.add(state);
        if (handle != null) applyState(state);
        return this;
    }
    private void applyState(NPCState state) {
        switch (state) {
            case ON_FIRE -> handle.setAttribute("on_fire", Boolean.toString(getState(state)));
            case INVISIBLE -> handle.setAttribute("invisible", Boolean.toString(getState(state)));
            case CROUCHED -> handle.setAttribute("pose", getState(state) ? "crouching" : "standing");
        }
    }
    @Override public void lookAt(Location location) { requireMain(); if (handle != null) handle.lookAt(location); }
    @Override public void playAnimation(NPCAnimation animation) {
        requireMain(); if (!isCreated()) return;
        net.minecraft.network.protocol.Packet<?> packet = animation == NPCAnimation.TAKE_DAMAGE
                ? new ClientboundHurtAnimationPacket(entityId, this.location.getYaw())
                : net.mizukilab.pit.util.PaperPackets.animation(entityId, animation.getId());
        for (UUID id : shown) { Player player = Bukkit.getPlayer(id); if (player != null && isShown(player)) ((CraftPlayer) player).getHandle().connection.send(packet); }
    }
    @Override public void playHurtFeedback() { playAnimation(NPCAnimation.TAKE_DAMAGE); }
    @Override public void destroy() {
        requireMain(); if (destroyed) return; destroyed = true;
        NPCManager.remove(this); instance.forgetNPC(this);
        if (handle != null) handle.close();
        shown.clear(); autoHidden.clear(); playerText.clear();
    }
}
