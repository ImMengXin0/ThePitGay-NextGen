package cn.charlotte.pit.util.hologram;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.util.hologram.touch.TouchAction;
import cn.charlotte.pit.util.hologram.touch.TouchHandler;
import cn.charlotte.pit.util.hologram.view.ViewHandler;
import net.mizukilab.pit.platform.paper.FancyTextService.TextHandle;
import net.mizukilab.pit.platform.paper.ViewerPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** The legacy game-facing hologram contract, rendered through FancyHolograms. */
public final class FancyHologramAdapter implements Hologram, Listener {
    private final ThePit owner = ThePit.getInstance();
    private final ViewerPolicy viewers = new ViewerPolicy();
    private final List<TouchHandler> touchHandlers = new CopyOnWriteArrayList<>();
    private final List<ViewHandler> viewHandlers = new CopyOnWriteArrayList<>();
    private final Map<UUID, TextHandle> personal = new HashMap<>();
    private volatile Location location;
    private volatile String text;
    private volatile boolean spawned, closed, fullyDespawned, touchable;
    private volatile Set<Player> members = Set.of();
    private volatile Entity attachedTo;
    private Hologram above, below;
    private TextHandle shared;
    private Interaction hitbox;
    private BukkitTask expiry;
    private long updateInterval = 5, ticks;

    public FancyHologramAdapter(Location location, String text) {
        this.location = Objects.requireNonNull(location, "location").clone();
        Objects.requireNonNull(location.getWorld(), "world");
        this.text = Objects.requireNonNullElse(text, "");
        HologramAPI.register(this);
    }

    private void onMain(Runnable action) {
        if (Bukkit.isPrimaryThread()) action.run();
        else if (owner.isEnabled()) Bukkit.getScheduler().runTask(owner, action);
    }

    @Override public boolean isSpawned() { return spawned && !closed; }
    @Override public boolean isFullyDespawned() { return fullyDespawned; }
    @Override public boolean spawn() {
        if (closed) return false;
        viewers.showAll();
        spawned = true;
        syncLines();
        update();
        return true;
    }
    @Override public boolean spawn(Collection<? extends Player> receivers) {
        if (closed) return false;
        for (Player player : List.copyOf(receivers)) viewers.show(player.getUniqueId());
        spawned = true;
        syncLines();
        update();
        return true;
    }
    @Override public void spawn(long lifetime) {
        if (lifetime < 0) throw new IllegalArgumentException("Negative hologram lifetime");
        if (!spawn()) return;
        onMain(() -> {
            if (closed) return;
            if (expiry != null) expiry.cancel();
            expiry = Bukkit.getScheduler().runTaskLater(owner, this::deSpawn, Math.max(1, lifetime));
        });
    }
    @Override public boolean deSpawn() {
        if (closed) {
            if (Bukkit.isPrimaryThread() && !fullyDespawned) cleanup();
            return false;
        }
        closed = true;
        spawned = false;
        onMain(this::cleanup);
        return true;
    }

    private void cleanup() {
            if (fullyDespawned) return;
            if (expiry != null) expiry.cancel();
            clearDisplays();
            removeHitbox();
            if (above != null) above.deSpawn();
            if (below != null) below.deSpawn();
            viewers.clear();
            members = Set.of();
            fullyDespawned = true;
            HologramAPI.removeHologram(this);
    }
    @Override public String getText() { return text; }
    @Override public void setText(String text) {
        String replacement = Objects.requireNonNullElse(text, "");
        if (replacement.equals(this.text)) return;
        this.text = replacement;
        update();
    }
    @Override public void update() { onMain(this::refresh); }
    @Override public void update(long interval) {
        if (interval == 0 || interval < -1) throw new IllegalArgumentException("Invalid update interval");
        onMain(() -> updateInterval = interval);
    }
    @Override public Location getLocation() { return location.clone(); }
    @Override public void setLocation(Location location) { move(location); }
    @Override public void move(Location destination) {
        Location next = destination.clone();
        Objects.requireNonNull(next.getWorld(), "world");
        onMain(() -> {
            Location previous = location;
            location = next;
            if (above != null) above.move(next.clone().add(0, 0.25, 0));
            if (below != null) below.move(next.clone().subtract(0, 0.25, 0));
            if (!previous.equals(next)) refresh();
        });
    }
    @Override public Set<Player> members() { return members; }
    @Override public void hide(Player player) {
        viewers.hide(player.getUniqueId());
        if (above != null) above.hide(player);
        if (below != null) below.hide(player);
        update();
    }

    /** Invoked on the server thread by the shared ticker. */
    public void tick() {
        if (!isSpawned()) return;
        Entity entity = attachedTo;
        if (entity != null) {
            if (!entity.isValid()) { deSpawn(); return; }
            move(entity.getLocation().add(0, entity.getHeight() + 0.25, 0));
        }
        if (++ticks % 5 == 0 || (updateInterval > 0 && ticks % updateInterval == 0)) refresh();
    }

    private void refresh() {
        if (!isSpawned()) return;
        Location at = location;
        Collection<? extends Player> candidates = viewers.isPublic() ? Bukkit.getOnlinePlayers()
                : viewers.privateCandidates(personal.keySet()).stream().map(Bukkit::getPlayer)
                    .filter(Objects::nonNull).toList();
        Set<Player> visible = new HashSet<>();
        for (Player player : candidates) {
            if (player.isOnline() && viewers.accepts(player.getUniqueId()) && player.getWorld().equals(at.getWorld())
                    && player.getLocation().distanceSquared(at) <= 64 * 64) visible.add(player);
        }
        members = Set.copyOf(visible);
        if (viewHandlers.isEmpty()) {
            if (!personal.isEmpty()) { personal.values().forEach(TextHandle::close); personal.clear(); }
            if (shared == null) shared = owner.getFancyTexts().create(at, List.of(text));
            shared.setLocation(at);
            shared.setText(List.of(text));
            // Explicit viewers also retain range/world permissions in the service.
            shared.showOnly(visible);
        } else {
            if (shared != null) { shared.close(); shared = null; }
            Set<UUID> ids = new HashSet<>();
            for (Player player : visible) {
                ids.add(player.getUniqueId());
                String rendered = text;
                for (ViewHandler handler : viewHandlers) rendered = Objects.requireNonNullElse(handler.onView(this, player, rendered), "");
                TextHandle display = personal.get(player.getUniqueId());
                if (display == null) {
                    display = owner.getFancyTexts().create(at, List.of(rendered));
                    personal.put(player.getUniqueId(), display);
                }
                display.setLocation(at);
                display.setText(List.of(rendered));
                display.showOnly(List.of(player));
            }
            personal.entrySet().removeIf(entry -> {
                if (ids.contains(entry.getKey())) return false;
                entry.getValue().close();
                return true;
            });
        }
        refreshHitbox(visible);
    }

    private void clearDisplays() {
        if (shared != null) { shared.close(); shared = null; }
        personal.values().forEach(TextHandle::close);
        personal.clear();
    }
    @Override public boolean isTouchable() { return touchable; }
    @Override public void setTouchable(boolean flag) { touchable = flag; update(); }
    @Override public void addTouchHandler(TouchHandler handler) { touchHandlers.add(Objects.requireNonNull(handler)); }
    @Override public void removeTouchHandler(TouchHandler handler) { touchHandlers.remove(handler); }
    @Override public Collection<TouchHandler> getTouchHandlers() { return List.copyOf(touchHandlers); }
    @Override public void clearTouchHandlers() { touchHandlers.clear(); }
    @Override public void addViewHandler(ViewHandler handler) { viewHandlers.add(Objects.requireNonNull(handler)); update(); }
    @Override public void removeViewHandler(ViewHandler handler) { viewHandlers.remove(handler); update(); }
    @Override public Collection<ViewHandler> getViewHandlers() { return List.copyOf(viewHandlers); }
    @Override public void clearViewHandlers() { viewHandlers.clear(); update(); }

    private void refreshHitbox(Set<Player> visible) {
        if (!touchable) { removeHitbox(); return; }
        if (hitbox == null || !hitbox.getWorld().equals(location.getWorld())) {
            removeHitbox();
            hitbox = location.getWorld().spawn(location.clone().subtract(0, 0.15, 0), Interaction.class, entity -> {
                entity.setVisibleByDefault(false);
                entity.setPersistent(false);
                entity.setInteractionWidth(1.5f);
                entity.setInteractionHeight(0.3f);
                entity.setResponsive(true);
            });
            Bukkit.getPluginManager().registerEvents(this, owner);
        } else hitbox.teleport(location.clone().subtract(0, 0.15, 0));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (visible.contains(player)) player.showEntity(owner, hitbox);
            else player.hideEntity(owner, hitbox);
        }
    }
    private void removeHitbox() {
        if (hitbox == null) return;
        hitbox.remove();
        hitbox = null;
        HandlerList.unregisterAll(this);
    }
    private void touch(Player player, TouchAction action) {
        if (!isSpawned() || !members.contains(player) || !player.getWorld().equals(location.getWorld())
                || player.getLocation().distanceSquared(location) > 36) return;
        for (TouchHandler handler : touchHandlers) handler.onTouch(this, player, action);
    }
    @EventHandler public void onInteract(PlayerInteractEntityEvent event) {
        if (hitbox != null && hitbox.equals(event.getRightClicked())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) touch(event.getPlayer(), TouchAction.RIGHT_CLICK);
        }
    }
    @EventHandler public void onAttack(EntityDamageByEntityEvent event) {
        if (hitbox != null && hitbox.equals(event.getEntity())) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player player) touch(player, TouchAction.LEFT_CLICK);
        }
    }

    private Hologram line(String text, double offset) {
        if (closed) throw new IllegalStateException("Hologram is closed");
        FancyHologramAdapter child = new FancyHologramAdapter(getLocation().add(0, offset, 0), text);
        inherit(child);
        return child;
    }

    private void inherit(FancyHologramAdapter child) {
        child.viewers.restore(viewers.snapshot());
        child.spawned = isSpawned();
        child.syncLines();
        child.update();
    }

    private void syncLines() {
        if (above instanceof FancyHologramAdapter child) inherit(child);
        if (below instanceof FancyHologramAdapter child) inherit(child);
    }
    @Override public Hologram addLineAbove(String text) {
        if (above != null) return above.addLineAbove(text);
        return above = line(text, 0.25);
    }
    @Override public Hologram addLineBelow(String text) {
        if (below != null) return below.addLineBelow(text);
        return below = line(text, -0.25);
    }
    @Override public Hologram getLineAbove() { return above; }
    @Override public Hologram getLineBelow() { return below; }
    @Override public boolean removeLineAbove() {
        if (above == null) return false;
        above.deSpawn(); above = null; return true;
    }
    @Override public boolean removeLineBelow() {
        if (below == null) return false;
        below.deSpawn(); below = null; return true;
    }
    @Override public Collection<Hologram> getLinesAbove() {
        if (above == null) return List.of();
        List<Hologram> lines = new ArrayList<>(List.of(above)); lines.addAll(above.getLinesAbove()); return lines;
    }
    @Override public Collection<Hologram> getLinesBelow() {
        if (below == null) return List.of();
        List<Hologram> lines = new ArrayList<>(List.of(below)); lines.addAll(below.getLinesBelow()); return lines;
    }
    @Override public Collection<Hologram> getLines() {
        List<Hologram> lines = new ArrayList<>(getLinesAbove()); lines.add(this); lines.addAll(getLinesBelow()); return lines;
    }
    @Override public Entity getAttachedTo() { return attachedTo; }
    @Override public void setAttachedTo(Entity entity) { attachedTo = entity; }
}
