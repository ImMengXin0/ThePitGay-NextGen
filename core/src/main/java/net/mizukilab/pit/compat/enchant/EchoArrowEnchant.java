package net.mizukilab.pit.compat.enchant;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.event.PitPlayerSpawnEvent;
import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.IActionDisplayEnchant;
import net.mizukilab.pit.enchantment.param.item.BowOnly;
import net.mizukilab.pit.parm.listener.IPlayerShootEntity;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.cooldown.Cooldown;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@BowOnly
public final class EchoArrowEnchant extends AddonEnchantReplacements.Replacement
        implements IPlayerShootEntity, IActionDisplayEnchant, Listener {
    private final Map<UUID, Cooldown> cooldowns = new ConcurrentHashMap<>();
    private final Set<Echo> pending = new HashSet<>();
    private static final class Echo {
        final Player attacker, target;
        BukkitTask task;
        Echo(Player attacker, Player target) { this.attacker = attacker; this.target = target; }
    }

    EchoArrowEnchant(AbstractEnchantment source) { super(source); }

    private static boolean alive(Player player) {
        return player.isValid() && !player.isDead() && (player.isOnline() || PlayerUtil.isNPC(player));
    }

    @Override public void handleShootEntity(int level, Player attacker, Entity target, double damage,
                                            AtomicDouble base, AtomicDouble boost, AtomicBoolean cancel) {
        if (level <= 0 || cancel.get() || damage <= 0 || !Double.isFinite(damage)
                || !(target instanceof Player victim) || PlayerUtil.shouldIgnoreEnchant(attacker)) return;
        Cooldown cd = cooldowns.get(attacker.getUniqueId());
        if (cd != null && !cd.hasExpired()) return;
        cooldowns.put(attacker.getUniqueId(), new Cooldown(level >= 3 ? 4 : level == 2 ? 6 : 8, TimeUnit.SECONDS));
        double amount = damage * (level >= 3 ? 0.5 : level == 2 ? 0.35 : 0.25);
        Echo echo = new Echo(attacker, victim);
        pending.add(echo);
        attacker.sendMessage(CC.translate("&d&l回声箭! &7回声将在 &e1.5秒 &7后引爆!"));
        echo.task = Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> {
            if (!pending.remove(echo) || !alive(attacker) || !alive(victim)
                    || !attacker.getWorld().equals(victim.getWorld())
                    || ThePit.getInstance().getEnchantmentFactor().getEnchantmentMap().get(getNbtName()) != this) return;
            // Snapshot the blast position before lethal damage can respawn/teleport the target.
            Location center = victim.getLocation().clone();
            Collection<Entity> nearby = new ArrayList<>(center.getWorld().getNearbyEntities(center, 3, 3, 3));
            PlayerUtil.damage(attacker, victim, PlayerUtil.DamageType.TRUE, amount, false);
            victim.sendMessage(CC.translate("&d&l回声! &7回声伤害命中了你!"));
            for (Entity entity : nearby) {
                if (entity instanceof Player other && !other.getUniqueId().equals(victim.getUniqueId())
                        && !other.getUniqueId().equals(attacker.getUniqueId()) && alive(other)) {
                    PlayerUtil.damage(attacker, other, PlayerUtil.DamageType.TRUE, amount * 0.5, false);
                    other.sendMessage(CC.translate("&d&l回声! &7你被回声溅射伤害命中!"));
                }
            }
        }, 30L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
        cancelFor(event.getPlayer());
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) { cancelFor(event.getEntity()); }
    @EventHandler public void onSpawn(PitPlayerSpawnEvent event) { cancelFor(event.getPlayer()); }
    private void cancelFor(Player player) {
        pending.removeIf(echo -> {
            if (!echo.attacker.getUniqueId().equals(player.getUniqueId())
                    && !echo.target.getUniqueId().equals(player.getUniqueId())) return false;
            if (echo.task != null) echo.task.cancel();
            return true;
        });
    }
    @Override public String getText(int level, Player player) {
        Cooldown cd = cooldowns.get(player.getUniqueId());
        return cd == null ? "&a&l✔" : getCooldownActionText(cd);
    }
    @Override public void close() {
        for (Echo echo : pending) if (echo.task != null) echo.task.cancel();
        pending.clear(); cooldowns.clear();
    }
}
