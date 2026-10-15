package net.mizukilab.pit.enchantment.type.op;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.IActionDisplayEnchant;
import net.mizukilab.pit.enchantment.param.event.PlayerOnly;
import net.mizukilab.pit.enchantment.param.item.WeaponOnly;
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity;
import net.mizukilab.pit.parm.listener.IAttackEntity;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.cooldown.Cooldown;
import net.mizukilab.pit.util.random.RandomUtil;
import nya.Skip;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/4/9 20:23
 */
@Skip
@WeaponOnly
public class StarJudgementEnchant extends AbstractEnchantment implements IAttackEntity, IActionDisplayEnchant {

    @Override
    public String getEnchantName() {
        return "星辰裁决";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "star_judgement_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.OP;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7每" + (4 - enchantLevel <= 1 ? "" : (" &e" + (4 - enchantLevel) + " ")) + "&7次攻击有 &e" + (enchantLevel >= 3 ? "30%" : "20%") + " &7的几率造成二连击,"
                + "/s&7第二击造成 &c1❤ &7的&c必中&7伤害/s&c(必中伤害无法被免疫与抵抗)";
    }

    @Override
    public String getText(int level, Player player) {
        int hit = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId()).getMeleeHit();
        int remaining = Math.max(1, 4 - level) - Math.floorMod(hit, Math.max(1, 4 - level));
        return remaining == 1 ? "&a&l✔" : "&e&l" + remaining;
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (enchantLevel <= 0 || !(target instanceof Player victim) || attacker == victim
                || cancel.get()) {
            return;
        }
        boolean npcVictim = PlayerUtil.isNPC(victim);
        if (!canFight(attacker, false) || !canFight(victim, npcVictim)) return;
        PlayerProfile attackerProfile = PlayerProfile.getPlayerProfileByUuid(attacker.getUniqueId());
        PlayerProfile victimProfile = PlayerProfile.getPlayerProfileByUuid(victim.getUniqueId());
        // Combat bots intentionally have no loaded player profile, as in ProtectListener.
        if (!attackerProfile.isLoaded()
                || (!npcVictim && (!victimProfile.isLoaded() || !victimProfile.isInArena()))) return;

        // CombatListener counts this hit at MONITOR, after this NORMAL-priority callback.
        long hit = (long) attackerProfile.getMeleeHit() + 1;
        if (hit % Math.max(1, 4 - enchantLevel) != 0
                || !RandomUtil.hasSuccessfullyByChance(enchantLevel >= 3 ? 0.3 : 0.2)) return;

        int victimDeaths = victimProfile.getDeaths();
        int victimTicks = victim.getTicksLived();
        int attackerDeaths = attackerProfile.getDeaths();
        World world = victim.getWorld();
        Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
            // A first-hit kill (including instant respawn) must not damage the next life.
            // True damage can trigger even when absorption/reduction negates the ordinary hit.
            if (cancel.get() || !canFight(attacker, false) || !canFight(victim, npcVictim)
                    || attacker.getWorld() != world || victim.getWorld() != world
                    || (!npcVictim && !victimProfile.isInArena())
                    || victimProfile.getDeaths() != victimDeaths || victim.getTicksLived() < victimTicks
                    || attackerProfile.getDeaths() != attackerDeaths) return;

            PlayerUtil.damage(attacker, victim, PlayerUtil.DamageType.TRUE, 2.0D, false);
            attacker.sendMessage(CC.translate("&e&l星辰裁决! &7触发成功!"));
        });
    }

    private static boolean canFight(Player player, boolean npc) {
        return (npc ? player.isValid() : player.isOnline()) && !player.isDead() && player.getHealth() > 0
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                && !player.hasMetadata("pit_death_handled")
                && !player.hasMetadata("mx_suppressed_duplicate_death");
    }
}
