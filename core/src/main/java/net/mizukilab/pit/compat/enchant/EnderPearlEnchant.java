package net.mizukilab.pit.compat.enchant;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.event.PitPlayerSpawnEvent;
import cn.charlotte.pit.data.PlayerProfile;
import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.IActionDisplayEnchant;
import net.mizukilab.pit.enchantment.param.item.WeaponOnly;
import net.mizukilab.pit.item.DyeColor;
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem;
import net.mizukilab.pit.parm.listener.IAttackEntity;
import net.mizukilab.pit.parm.listener.IPlayerKilledEntity;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.cooldown.Cooldown;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@WeaponOnly
public abstract class EnderPearlEnchant extends AddonEnchantReplacements.Replacement
        implements Listener, IActionDisplayEnchant, IAttackEntity, IPlayerKilledEntity {
    private final SharedState shared;
    private final boolean envoy;

    EnderPearlEnchant(AbstractEnchantment source, SharedState shared) {
        super(source);
        this.shared = shared;
        envoy = "ender_envoy".equals(source.getNbtName());
    }

    @WeaponOnly public static final class Envoy extends EnderPearlEnchant {
        Envoy(AbstractEnchantment source, SharedState shared) { super(source, shared); }
    }
    @WeaponOnly public static final class Sword extends EnderPearlEnchant {
        Sword(AbstractEnchantment source, SharedState shared) { super(source, shared); }
    }

    /** One state per core enchantment registry; two enchants must share both launch and display. */
    public static final class SharedState {
        // Display readers can query cooldowns without sharing mutable per-cast state.
        final Map<UUID, Cooldown> cooldowns = new ConcurrentHashMap<>();
        final Map<UUID, Cast> casts = new HashMap<>();
    }

    private record Cast(EnderPearlEnchant owner, EnderPearl pearl, int level, long expires) { }

    private boolean active() {
        return ThePit.getInstance().getEnchantmentFactor().getEnchantmentMap().get(getNbtName()) == this;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // Right-click-air may start with useInteractedBlock=DENY in Bukkit 1.8.
        if (event.useItemInHand() == Event.Result.DENY || !active()) return;
        Player player = event.getPlayer();
        if (PlayerUtil.shouldIgnoreEnchant(player)) return;
        int level = ThePit.getApi().getItemEnchantLevel(event.getItem(), getNbtName());
        if (level <= 0) return;
        AbstractEnchantment currentEnvoy = ThePit.getInstance().getEnchantmentFactor().getEnchantmentMap().get("ender_envoy");
        if (!envoy && currentEnvoy instanceof EnderPearlEnchant
                && ThePit.getApi().getItemEnchantLevel(event.getItem(), "ender_envoy") > 0) return;
        UUID id = player.getUniqueId();
        Cooldown cooldown = shared.cooldowns.get(id);
        if (cooldown != null && !cooldown.hasExpired()) return;
        EnderPearl pearl = player.launchProjectile(EnderPearl.class);
        if (pearl == null || pearl.isDead() || !pearl.isValid()) return;
        pearl.setShooter(player);
        shared.cooldowns.put(id, new Cooldown(level >= 3 ? 20 : 30, TimeUnit.SECONDS));
        shared.casts.put(id, new Cast(this, pearl, level, System.currentTimeMillis() + 60_000));
        if (!envoy) PlayerUtil.damage(player, PlayerUtil.DamageType.TRUE, 1D + level, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player player) {
            // A later ordinary pearl must never inherit this enchant's landing effect.
            Cast cast = shared.casts.get(player.getUniqueId());
            if (cast != null && cast.pearl() != pearl) shared.casts.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Cast cast = shared.casts.get(player.getUniqueId());
        if (cast == null || cast.owner() != this) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            if (!event.isCancelled()) shared.casts.remove(player.getUniqueId());
            return;
        }
        shared.casts.remove(player.getUniqueId());
        if (event.isCancelled() || !active() || System.currentTimeMillis() > cast.expires()
                || PlayerUtil.shouldIgnoreEnchant(player) || event.getTo() == null) return;
        var landing = cast.pearl().getLocation();
        if (!landing.getWorld().equals(event.getTo().getWorld()) || landing.distanceSquared(event.getTo()) > 4) return;
        // Keep an existing stronger/longer speed effect; amplification is zero-based.
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)
                    && (effect.getAmplifier() > cast.level()
                    || effect.getAmplifier() == cast.level() && effect.getDuration() >= 80)) return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, cast.level(), false, true), true);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        shared.cooldowns.remove(event.getPlayer().getUniqueId());
        shared.casts.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) { shared.casts.remove(event.getEntity().getUniqueId()); }
    @EventHandler public void onSpawn(PitPlayerSpawnEvent event) { shared.casts.remove(event.getPlayer().getUniqueId()); }

    @Override public String getText(int level, Player player) {
        Cooldown cd = shared.cooldowns.get(player.getUniqueId());
        return cd == null ? "&a&l✔" : getCooldownActionText(cd);
    }

    @Override public void handleAttackEntity(int level, Player attacker, Entity target, double damage,
                                            AtomicDouble base, AtomicDouble boost, AtomicBoolean cancel) {
        if (!envoy || level <= 0 || cancel.get() || !(target instanceof Player victim)
                || PlayerUtil.shouldIgnoreEnchant(attacker)) return;
        ItemStack chest = victim.getInventory().getChestplate();
        if (chest == null || chest.getType() == Material.AIR) boost.addAndGet((25 + 50 * level) * 0.01);
        if (Utils.getMythicItem(attacker.getInventory().getLeggings()) instanceof MythicLeggingsItem own
                && own.getDyeColor() == DyeColor.FUCHSIA
                && Utils.getMythicItem(victim.getInventory().getLeggings()) instanceof MythicLeggingsItem) {
            base.set(0.2); // Lore specifies 0.1 heart, Bukkit uses health points.
        }
    }

    @Override public void handlePlayerKilled(int level, Player player, Entity target, AtomicDouble coins, AtomicDouble exp) {
        if (!envoy || level < 2 || !(target instanceof Player) || player == null
                || PlayerUtil.shouldIgnoreEnchant(player)) return;
        PlayerProfile profile = PlayerProfile.getRawCache(player.getUniqueId());
        if (profile != null && profile.isLoaded()) profile.setStreakKills(profile.getStreakKills() + 0.1 * (level - 1));
    }

    @Override public String getUsefulnessLore(int level) {
        if (!envoy) return super.getUsefulnessLore(level);
        return "&7继承附魔 &d末影剑 &7的全部属性, 但使用时不再扣除生命值, &c同时与其共享冷却."
                + (level >= 2 ? "/s&7击杀玩家时额外获得 &c" + (10 * (level - 1)) + "% &7连杀数" : "")
                + "/s&7对未穿着 &f胸甲 &7的玩家造成的伤害 &c+" + (25 + 50 * level) + "%"
                + "/s&7当自身穿着 &dFUCHSIA &7染色神话之甲并攻击玩家时,"
                + "/s&7若目标穿着了任意颜色的神话之甲, 则锁定基础近战伤害为 &c0.1❤";
    }

    @Override public void close() { shared.casts.values().removeIf(cast -> cast.owner() == this); }
}
