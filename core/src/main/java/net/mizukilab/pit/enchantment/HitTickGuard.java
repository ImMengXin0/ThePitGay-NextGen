package net.mizukilab.pit.enchantment;

import cn.charlotte.pit.ThePit;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-thread-only hit accounting. Uses actual server ticks, including when TPS drops. */
public final class HitTickGuard {
    // Exact NBT identifiers also cover Addon/Repair implementations installed under those identifiers.
    // Ordinary damage multipliers (notably Billionaire) and Regularity are deliberately not counters.
    private static final Set<String> ATTACK = Set.of(
            "Combo_Damage", "vampire", "combo_swift_enchant", "combocoma_enchant", "combosteal_enchant",
            "combo_stun_enchant", "lightning_strike", "combo_venom_enchant", "dazzlinggold_enchant",
            "unpredictably_enchant", "combo_dust", "star_judgement_enchant", "24_5_20",
            "Echo_Of_Snowland_Weapon", "combo_chaos", "combo_parasite", "bone_frost", "pharmacist",
            "steal", "SeprentBlade", "vital_echo", "war_god", "erosion", "combo_speed_force_enchant",
            "soul_ripper_enchant", "momentum", "battle_spirit", "frenzy_strike_enchant", "kings_flame");
    private static final Set<String> DEFENCE = Set.of("counter_offensive_enchant", "emergency_cluster",
            "microantygravity", "frost");
    private static final Set<String> BOW = Set.of("withered_and_piercing_through_the_heart", "piercing_bow",
            "bow_combo_enchant", "serious_sit");

    private final Set<UUID> meleeCounts = new HashSet<>();
    private final Set<UUID> bowCounts = new HashSet<>();
    private final Set<Check> checks = new HashSet<>();
    private int tick;
    private record Check(String enchantment, UUID holder, UUID source, boolean defence) { }

    private void advance() {
        int now = MinecraftServer.currentTick;
        if (now == tick) return;
        tick = now;
        meleeCounts.clear(); bowCounts.clear(); checks.clear();
    }

    public boolean countMelee(Player attacker) {
        advance();
        return meleeCounts.add(attacker.getUniqueId());
    }

    public boolean countArrow(Entity arrow) {
        advance();
        return bowCounts.add(arrow.getUniqueId());
    }

    public static boolean attack(Object callback, Player attacker, Entity projectile, boolean cancelled) {
        if (!(callback instanceof AbstractEnchantment enchant)) return true;
        String nbt = enchant.getNbtName();
        if (nbt == null || !ATTACK.contains(nbt) && (projectile == null || !BOW.contains(nbt))) return true;
        if (cancelled) return false;
        HitTickGuard guard = ThePit.getInstance().getEnchantmentFactor().getHitTickGuard();
        guard.advance();
        // Distinct arrows are distinct hits, even in one tick. Melee counts once per attacker,
        // not per target/item: switching victims or holding a second enchanted item cannot bypass it.
        return guard.checks.add(new Check(nbt, attacker.getUniqueId(), projectile == null ? null : projectile.getUniqueId(), false));
    }

    public static boolean damaged(Object callback, Player holder, Entity source, boolean cancelled) {
        if (!(callback instanceof AbstractEnchantment enchant) || enchant.getNbtName() == null
                || !DEFENCE.contains(enchant.getNbtName())) return true;
        if (cancelled) return false;
        HitTickGuard guard = ThePit.getInstance().getEnchantmentFactor().getHitTickGuard();
        guard.advance();
        // Independent attackers/projectiles can each contribute; repeated clicks from one cannot.
        if (!(source instanceof Player) && !(source instanceof Projectile)) return true;
        return guard.checks.add(new Check(enchant.getNbtName(), holder.getUniqueId(), source.getUniqueId(), true));
    }
}
