package net.mizukilab.pit.compat.enchant;

import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity;
import net.mizukilab.pit.util.cooldown.Cooldown;
import org.bukkit.inventory.ItemStack;

/** Exact implementation matching leaves Repair and other same-NBT replacements in control. */
public final class AddonEnchantReplacements {
    private AddonEnchantReplacements() { }

    public static AbstractEnchantment adapt(AbstractEnchantment source, EnderPearlEnchant.SharedState pearls) {
        return switch (source.getClass().getName()) {
            case "cn.apnf.mistral.enchantments.normal.FrenzyStrikeEnchant" -> new FrenzyStrikeEnchant(source);
            case "net.mizukilab.pit.enchantment.type.normal.BowComboEnchant",
                 "cn.apnf.mistral.enchantments.normal.BowComboEnchant" -> new BowComboEnchant(source);
            case "cn.apnf.mistral.enchantments.op.EnderEnvoy" -> new EnderPearlEnchant.Envoy(source, pearls);
            case "cn.apnf.mistral.enchantments.rare.EnderSword" -> new EnderPearlEnchant.Sword(source, pearls);
            case "cn.apnf.mistral.enchantments.rare.EchoArrowEnchant" -> new EchoArrowEnchant(source);
            case "cn.apnf.mistral.enchantments.rare.RetroGravityMicrocosmEnchant" -> new MicroGravityEnchant(source);
            default -> source;
        };
    }

    public static Class<?> sourceClass(Object enchantment) {
        return enchantment instanceof Replacement replacement ? replacement.source.getClass() : enchantment.getClass();
    }

    public static boolean replaces(AbstractEnchantment current, AbstractEnchantment source) {
        return current instanceof Replacement replacement && replacement.source == source;
    }

    public abstract static class Replacement extends AbstractEnchantment {
        protected final AbstractEnchantment source;
        protected Replacement(AbstractEnchantment source) { this.source = source; }
        @Override public String getEnchantName() { return source.getEnchantName(); }
        @Override public String getNbtName() { return source.getNbtName(); }
        @Override public int getMaxEnchantLevel() { return source.getMaxEnchantLevel(); }
        @Override public EnchantmentRarity getRarity() { return source.getRarity(); }
        @Override public Cooldown getCooldown() { return source.getCooldown(); }
        @Override public String getUsefulnessLore(int level) { return source.getUsefulnessLore(level); }
        @Override public boolean canApply(ItemStack item) { return source.canApply(item); }
        public abstract void close();
    }
}
