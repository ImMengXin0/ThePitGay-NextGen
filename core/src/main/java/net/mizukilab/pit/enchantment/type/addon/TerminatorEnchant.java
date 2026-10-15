package net.mizukilab.pit.enchantment.type.addon;

import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.param.item.WeaponOnly;
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity;
import net.mizukilab.pit.parm.listener.IAttackEntity;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.cooldown.Cooldown;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.atomic.AtomicBoolean;

/** Core fallback for existing Addon items when the extension has not registered Terminator. */
@WeaponOnly
public final class TerminatorEnchant extends AbstractEnchantment implements IAttackEntity {
    @Override public String getEnchantName() { return "终结者"; }
    @Override public String getNbtName() { return "terminator"; }
    @Override public int getMaxEnchantLevel() { return 3; }
    @Override public EnchantmentRarity getRarity() { return EnchantmentRarity.NORMAL; }
    @Override public Cooldown getCooldown() { return null; }

    @Override public String getUsefulnessLore(int level) {
        return "&7对未穿着 &7&f头盔 &7的玩家造成的伤害 &c+" + (25 + 50 * level) + "%";
    }

    @Override public void handleAttackEntity(int level, Player attacker, Entity target, double damage,
            AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (level <= 0 || cancel.get() || !(target instanceof Player player)
                || PlayerUtil.shouldIgnoreEnchant(attacker)) return;
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || helmet.getType() == Material.AIR) {
            // Preserve level IV on the Addon's existing space-shop exchange items.
            boostDamage.addAndGet((25 + 50 * level) * 0.01D);
        }
    }
}
