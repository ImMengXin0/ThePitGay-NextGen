package net.mizukilab.pit.compat.enchant;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.event.PitPlayerSpawnEvent;
import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.IActionDisplayEnchant;
import net.mizukilab.pit.enchantment.param.item.BowOnly;
import net.mizukilab.pit.parm.listener.IPlayerShootEntity;
import net.mizukilab.pit.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Counts physical arrow hits, including hits suppressed by the target's hurt resistance. */
@BowOnly
public final class BowComboEnchant extends AddonEnchantReplacements.Replacement
        implements IPlayerShootEntity, IActionDisplayEnchant, Listener {
    // Action-bar text may be read asynchronously; collision state is confined to the server thread.
    private final Map<UUID, Integer> combo = new ConcurrentHashMap<>();
    private final Map<Arrow, Shot> shots = new WeakHashMap<>();
    private final Set<Arrow> resolved = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Arrow, Hit> pending = new HashMap<>();
    private record Shot(UUID shooter, int level) { }
    private static final class Hit {
        final Arrow arrow;
        final Player shooter;
        final int level;
        boolean collided;
        EntityDamageByEntityEvent damage;
        BukkitTask task;
        Hit(Arrow arrow, Player shooter, int level) {
            this.arrow = arrow; this.shooter = shooter; this.level = level;
        }
    }

    BowComboEnchant(AbstractEnchantment source) { super(source); }

    private boolean active() {
        return ThePit.getInstance().getEnchantmentFactor().getEnchantmentMap().get(getNbtName()) == this;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowShot(EntityShootBowEvent event) {
        if (!active() || event.isCancelled() || !(event.getEntity() instanceof Player player)
                || !(event.getProjectile() instanceof Arrow arrow)) return;
        int level = event.getBow() == null || PlayerUtil.shouldIgnoreEnchant(player)
                ? 0 : getItemEnchantLevel(event.getBow());
        // Store zero too: Repair's later fallback to the held bow must not enchant an ordinary shot.
        shots.put(arrow, new Shot(player.getUniqueId(), Math.max(0, Math.min(3, level))));
    }

    private int level(Arrow arrow) {
        Shot shot = shots.get(arrow);
        if (shot != null) return shot.level();
        // Repair's additional arrow bypasses EntityShootBowEvent but carries the original enchant map.
        for (MetadataValue value : arrow.getMetadata("enchant")) {
            if (value.getOwningPlugin() != ThePit.getInstance() || !(value.value() instanceof Map<?, ?> map)) continue;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof AbstractEnchantment enchant && getNbtName().equals(enchant.getNbtName())
                        && entry.getValue() instanceof Number number) return Math.max(0, Math.min(3, number.intValue()));
            }
        }
        return 0;
    }

    private Hit track(Arrow arrow) {
        if (!active() || resolved.contains(arrow) || !(arrow.getShooter() instanceof Player player)) return null;
        Hit hit = pending.get(arrow);
        if (hit != null) return hit;
        int level = level(arrow);
        if (level <= 0) return null;
        hit = new Hit(arrow, player, level);
        pending.put(arrow, hit);
        Hit queued = hit;
        hit.task = Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> resolve(queued), 1L);
        return hit;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBowHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow) {
            Hit hit = track(arrow);
            if (hit != null) hit.collided = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow) {
            Hit hit = track(arrow);
            if (hit != null) hit.damage = event;
        }
    }

    private void resolve(Hit hit) {
        if (!pending.remove(hit.arrow, hit)) return;
        resolved.add(hit.arrow);
        shots.remove(hit.arrow);
        Player player = hit.shooter;
        if (!active() || !player.isOnline() || player.isDead() || PlayerUtil.shouldIgnoreEnchant(player)) {
            combo.remove(player.getUniqueId());
            return;
        }
        boolean landed;
        if (hit.damage != null) {
            // Read the final event state, including cancellations by listeners after ours.
            landed = !hit.damage.isCancelled() && !PlayerUtil.shouldIgnoreEnchant(player, hit.damage.getEntity());
        } else {
            // 1.8 fires ProjectileHitEvent BEFORE updating inGround / attempting damage.
            // An entity collision leaves inGround false even when no damage event is emitted.
            // Check inGround even for a dead arrow: removing a wall-hit arrow is still a miss.
            landed = hit.collided && hit.arrow instanceof CraftArrow craft && !craft.getHandle().isInGround();
        }
        UUID id = player.getUniqueId();
        if (!landed) { combo.remove(id); return; }
        int count = combo.getOrDefault(id, 0) + 1;
        if (count < (hit.level >= 2 ? 2 : 3)) { combo.put(id, count); return; }
        combo.remove(id);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED) && (effect.getAmplifier() > hit.level
                    || effect.getAmplifier() == hit.level && effect.getDuration() >= 80)) return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, hit.level, true, false), true);
    }

    @Override public void handleShootEntity(int level, Player attacker, Entity target, double damage,
                                            AtomicDouble base, AtomicDouble boost, AtomicBoolean cancel) {
        // The collision task owns both counting and activation; damage callbacks must not repeat either.
    }

    @Override public String getText(int level, Player player) {
        return "&e&l" + combo.getOrDefault(player.getUniqueId(), 0) + "/" + (level >= 2 ? 2 : 3);
    }

    private void clear(Player player) {
        UUID id = player.getUniqueId();
        combo.remove(id);
        shots.replaceAll((arrow, shot) -> shot.shooter().equals(id) ? new Shot(id, 0) : shot);
        pending.values().removeIf(hit -> {
            if (!hit.shooter.getUniqueId().equals(id)) return false;
            resolved.add(hit.arrow);
            hit.task.cancel();
            return true;
        });
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { clear(event.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { clear(event.getEntity()); }
    @EventHandler public void onSpawn(PitPlayerSpawnEvent event) { clear(event.getPlayer()); }
    @Override public void close() {
        for (Hit hit : pending.values()) hit.task.cancel();
        pending.clear(); shots.clear(); resolved.clear(); combo.clear();
    }
}
