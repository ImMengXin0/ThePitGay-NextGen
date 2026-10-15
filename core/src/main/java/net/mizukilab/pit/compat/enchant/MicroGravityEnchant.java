package net.mizukilab.pit.compat.enchant;

import cn.charlotte.pit.event.PitPlayerSpawnEvent;
import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.param.item.ArmorOnly;
import net.mizukilab.pit.parm.listener.IAttackEntity;
import net.mizukilab.pit.parm.listener.IPlayerDamaged;
import net.mizukilab.pit.parm.listener.IPlayerShootEntity;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.chat.CC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@ArmorOnly
public final class MicroGravityEnchant extends AddonEnchantReplacements.Replacement
        implements IPlayerDamaged, IAttackEntity, IPlayerShootEntity, Listener {
    private final Map<UUID, State> states = new HashMap<>();
    private static final class State { int hits, stacks; long hitUntil, boostUntil; }
    MicroGravityEnchant(AbstractEnchantment source) { super(source); }

    @Override public void handlePlayerDamaged(int level, Player player, Entity damageSource, double damage,
                                              AtomicDouble base, AtomicDouble boost, AtomicBoolean cancel) {
        if (level <= 0 || cancel.get() || PlayerUtil.shouldIgnoreEnchant(player)) return;
        Player attacker = damageSource instanceof Player p ? p
                : damageSource instanceof Projectile projectile && projectile.getShooter() instanceof Player p ? p : null;
        if (attacker == null) return;
        State state = states.computeIfAbsent(player.getUniqueId(), id -> new State());
        if (player.isOnGround()) { state.hits = 0; return; }
        long now = System.currentTimeMillis();
        if (now >= state.hitUntil || state.hits == 0) { state.hits = 0; state.hitUntil = now + 2000; }
        if (++state.hits < 3) return;
        state.hits = 0;
        PlayerUtil.heal(player, 4);
        player.sendMessage(CC.translate("&d&l微观反重力！ &7附魔已被 " + attacker.getName() + " &7触发！"));
        if (level >= 2) {
            state.stacks = Math.min(2, (now < state.boostUntil ? state.stacks : 0) + 1);
            state.boostUntil = now + 30_000;
        }
    }

    private void boost(int level, Player player, AtomicDouble damage, AtomicBoolean cancel) {
        if (level < 2 || cancel.get() || PlayerUtil.shouldIgnoreEnchant(player)) return;
        State state = states.get(player.getUniqueId());
        if (state != null && System.currentTimeMillis() < state.boostUntil) {
            damage.addAndGet(state.stacks * (level >= 3 ? 0.15 : 0.1));
        }
    }
    @Override public void handleAttackEntity(int level, Player player, Entity target, double damage,
                                             AtomicDouble base, AtomicDouble boost, AtomicBoolean cancel) {
        boost(level, player, boost, cancel);
    }
    @Override public void handleShootEntity(int level, Player player, Entity target, double damage,
                                            AtomicDouble base, AtomicDouble boost, AtomicBoolean cancel) {
        boost(level, player, boost, cancel);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { states.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { states.remove(event.getEntity().getUniqueId()); }
    @EventHandler public void onSpawn(PitPlayerSpawnEvent event) { states.remove(event.getPlayer().getUniqueId()); }
    @Override public void close() { states.clear(); }
}
