package net.mizukilab.pit.compat.enchant;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.HitTickGuard;
import net.mizukilab.pit.enchantment.param.item.WeaponOnly;
import net.mizukilab.pit.util.PlayerUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** The legacy Addon counts these hits in a Bukkit listener instead of the core callback. */
@WeaponOnly
public final class FrenzyStrikeEnchant extends AddonEnchantReplacements.Replacement implements Listener {
    private final MethodHandle damage;

    FrenzyStrikeEnchant(AbstractEnchantment source) {
        super(source);
        try {
            damage = MethodHandles.publicLookup().findVirtual(source.getClass(), "onDamage",
                    MethodType.methodType(void.class, EntityDamageByEntityEvent.class)).bindTo(source);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Unsupported Addon FrenzyStrike listener", exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) throws Throwable {
        if (event.isCancelled() || !(event.getDamager() instanceof Player attacker)
                || PlayerUtil.shouldIgnoreEnchant(attacker) || attacker.getItemInHand() == null
                || getItemEnchantLevel(attacker.getItemInHand()) <= 0) return;
        var factor = ThePit.getInstance().getEnchantmentFactor();
        if (factor.getEnchantmentMap().get(getNbtName()) == this
                && HitTickGuard.attack(this, attacker, null, false)) damage.invokeExact(event);
    }

    @Override public void close() { }
}
