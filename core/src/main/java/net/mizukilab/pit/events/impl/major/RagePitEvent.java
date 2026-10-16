package net.mizukilab.pit.events.impl.major;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.event.PitDamageEvent;
import cn.charlotte.pit.event.PitKillEvent;
import cn.charlotte.pit.events.trigger.type.IEpicEvent;
import cn.charlotte.pit.events.AbstractEvent;
import com.boydti.fawe.FaweAPI;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockID;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import net.minecraft.server.v1_8_R3.MathHelper;
import net.mizukilab.pit.config.NewConfiguration;
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem;
import net.mizukilab.pit.medal.impl.challenge.RagePitTopMedal;
import net.mizukilab.pit.handlers.GlobalSweeper;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.cooldown.Cooldown;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.random.RandomUtil;
import net.mizukilab.pit.util.time.TimeUtil;
import net.mizukilab.pit.util.worldedit.JustAirBlockPattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/30 0:33
 */

public class RagePitEvent extends AbstractEvent implements IEpicEvent, Listener {
    @Getter
    private final Map<UUID, DamageData> damageMap = new java.util.concurrent.ConcurrentHashMap<>();
    private EditSession session;
    @Getter
    private int killed = 0;
    private BukkitRunnable runnable;
    @Getter
    private Cooldown timer = new Cooldown(5, TimeUnit.MINUTES);
    @Override
    public boolean processTrigger(TrigAction action,Player player,Object... objects){
        if(action == TrigAction.CLEAR){
            player.setMaxHealth(((PlayerProfile)objects[0]).getExtraMaxHealthValue() + 40);
        }
        return false;
    }
    public int getDamageRank(Player player) {
        DamageData damage = damageMap.get(player.getUniqueId());
        if (damage == null) return -1;
        double value = damage.getDamage();
        int rank = 0;
        // Preserve the old rule: tied scores all receive the last position in the tie.
        for (DamageData other : damageMap.values()) {
            if (Double.compare(other.getDamage(), value) >= 0) rank++;
        }
        return rank;
    }

    public record DamageStanding(double damage, int rank) { }

    /** Immutable values shared by every scoreboard in one refresh, never cached by time. */
    public Map<UUID, DamageStanding> getDamageStandings() {
        List<Map.Entry<UUID, Double>> scores = new ArrayList<>(damageMap.size());
        damageMap.forEach((uuid, data) -> scores.add(Map.entry(uuid, data.getDamage())));
        scores.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());
        Map<UUID, DamageStanding> standings = new HashMap<>();
        for (int start = 0; start < scores.size();) {
            int end = start + 1;
            double damage = scores.get(start).getValue();
            while (end < scores.size() && Double.compare(scores.get(end).getValue(), damage) == 0) end++;
            for (int i = start; i < end; i++) {
                standings.put(scores.get(i).getKey(), new DamageStanding(damage, end));
            }
            start = end;
        }
        return Collections.unmodifiableMap(standings);
    }

    @Override
    public String getEventInternalName() {
        return "rage_pit";
    }

    @Override
    public String getEventName() {
        return "疯狂天坑";
    }

    @Override
    public int requireOnline() {
        return NewConfiguration.INSTANCE.getEventOnlineRequired().getOrDefault(getEventInternalName(), 10);
    }

    @Override
    public void onActive() {
        this.timer = new Cooldown(5, TimeUnit.MINUTES);
        killed = 0;
        damageMap.clear();
        try {
            Bukkit.getPluginManager()
                    .registerEvents(this, ThePit.getInstance());
            //build wall - start
            Location middle = ThePit.getInstance().getPitConfig().getRagePitMiddle();
            BlockVector vector = new BlockVector(middle.getBlockX(), middle.getBlockY(), middle.getBlockZ());
            BukkitWorld world = new BukkitWorld(middle.getWorld());
            cn.charlotte.pit.events.EventWorldEdits.build(this, () -> {
                session = FaweAPI.getEditSessionBuilder(world).build();
                session.makeCylinder(vector, new JustAirBlockPattern(new BaseBlock(BlockID.GLASS)),
                        ThePit.getInstance().getPitConfig().getRagePitRadius(), ThePit.getInstance().getPitConfig().getRagePitHeight(), false);
                cn.charlotte.pit.events.EventWorldEdits.flush(session);
            });


            GlobalSweeper.get().getPlacedBlock().removeIf((loc, blockData) -> {
                loc.getBlock().setType(Material.AIR);
                return true;
            });
            //build wall - end

            //set Player's max health and teleport to spawn
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setMaxHealth(PlayerProfile.getPlayerProfileByUuid(player.getUniqueId()).getExtraMaxHealthValue() + 40.0);
                player.setHealth(player.getMaxHealth());

                Location location = ThePit.getInstance().getPitConfig()
                        .getSpawnLocations()
                        .get(RandomUtil.random.nextInt(ThePit.getInstance().getPitConfig().getSpawnLocations().size()));
                player.teleport(location);
                player.playSound(player.getLocation(), Sound.ENDERDRAGON_GROWL, 1, 0.5F);
                player.sendMessage(CC.translate("&5&l大型事件! &6&l疯狂天坑 &7事件开始!"));
            }

            runnable = new BukkitRunnable() {
                @Override
                public void run() {
                    if (timer.hasExpired()) {
                        ThePit.getInstance()
                                .getEventFactory()
                                .inactiveEvent(RagePitEvent.this);
                        return;
                    }

                    ThePit.getInstance()
                            .getBossBar()
                            .getBossBar()
                            .setTitle(CC.translate("&5&l大型事件! &6&l" + getEventName() + " &7将在 &a" + TimeUtil.millisToTimer(timer.getRemaining()) + "&7 后结束!"));
                    ThePit.getInstance()
                            .getBossBar()
                            .getBossBar()
                            .setProgress(timer.getRemaining() / (1000 * 60 * 5f));
                }
            };

            trackTask(runnable.runTaskTimer(ThePit.getInstance(), 0, 20));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not start Rage Pit", failure);
        }
    }

    @EventHandler
    public void onPlayerKill(PitKillEvent event) {
        if ("rage_pit".equals(ThePit.getInstance().getEventFactory().getActiveEpicEventName())) {
            killed++;
        }
    }

    @EventHandler
    public void onPlayerDamage(PitDamageEvent event) {
        if ("rage_pit".equals(ThePit.getInstance().getEventFactory().getActiveEpicEventName())) {
            damageMap.putIfAbsent(event.getAttacker().getUniqueId(), new DamageData(event.getAttacker().getUniqueId(), 0));
            DamageData damageData = damageMap.get(event.getAttacker().getUniqueId());
            damageData.setDamage(damageData.getDamage() + event.getFinalDamage());
        }
    }

    @Override
    public void onInactive() {
        HandlerList.unregisterAll(this);

        //还原玩家血量,移除活动道具
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setMaxHealth(PlayerProfile.getPlayerProfileByUuid(player.getUniqueId()).getExtraMaxHealthValue() + 20.0);
            InventoryUtil.removeItemWithInternalName(player, "angry_potato");
        }
        //取消Runnable
        if (runnable != null) runnable.cancel();
        //还原建筑
        cn.charlotte.pit.events.EventWorldEdits.undo(() -> {
            if (session != null) { cn.charlotte.pit.events.EventWorldEdits.restore(session); cn.charlotte.pit.events.EventWorldEdits.flush(session); session = null; }
        });
        //还原BossBar
        ThePit.getInstance()
                .getBossBar()
                .getBossBar()
                .setTitle("");
        if (isAborting()) { damageMap.clear(); killed = 0; return; }
        //获取伤害前3名玩家
        HashSet<Map.Entry<UUID, DamageData>> entry = new HashSet<>(damageMap.entrySet());
        List<DamageData> collect = entry
                .stream()
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparingDouble(DamageData::getDamage).reversed())
                .collect(Collectors.toList());
        //结算消息
        int position = 0;
        for (DamageData data : collect) {
            int rank = ++position;
            Player player = Bukkit.getPlayer(data.getUuid());
            if (player == null || !player.isOnline()) {
                continue;
            }

            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
            player.sendMessage(CC.GOLD + CC.CHAT_BAR);
            player.sendMessage(CC.translate("&6&l天坑事件结束: &c" + this.getEventName()));
            double rewardCoins = 0;
            int rewardRenown = 0;
            if (rank <= 3) {
                rewardCoins += 2000;
                rewardRenown += 2;
            } else if (rank <= 20) {
                rewardCoins += 500;
                rewardRenown += 1;
            } else {
                rewardCoins += 100;
            }
            if (killed >= 600) {
                rewardCoins = 2 * rewardCoins;
            }
            if (ThePit.getInstance().getPitConfig().isGenesisEnable() && profile.getGenesisData().getTier() >= 5 && rewardRenown > 0) {
                rewardRenown++;
            }
            int enchantBoostLevel = Utils.getEnchantLevel(player.getInventory().getLeggings(), "Paparazzi");
            if (PlayerUtil.isVenom(player) || PlayerUtil.isEquippingSomber(player)) {
                enchantBoostLevel = 0;
            }
            if (enchantBoostLevel > 0) {
                rewardCoins += 0.5 * enchantBoostLevel * rewardCoins;
                rewardRenown += MathHelper.floor(0.5 * enchantBoostLevel * rewardRenown);
                MythicLeggingsItem mythicLeggings = new MythicLeggingsItem();
                mythicLeggings.loadFromItemStack(player.getInventory().getLeggings());
                if (mythicLeggings.isEnchanted()) {
                    if (mythicLeggings.getMaxLive() > 0 && mythicLeggings.getLive() <= 2) {
                        player.getInventory().setLeggings(new ItemStack(Material.AIR));
                    } else {
                        mythicLeggings.setLive(mythicLeggings.getLive() - 2);
                        player.getInventory().setLeggings(mythicLeggings.toItemStack());
                    }
                }
            }
            if (PlayerUtil.isPlayerUnlockedPerk(player, "self_confidence")) {
                if (rank <= 5) {
                    rewardCoins += 5000;
                } else if (rank <= 10) {
                    rewardCoins += 2500;
                } else if (rank <= 15) {
                    rewardCoins += 1000;
                }
            }
            profile.grindCoins(rewardCoins);
            profile.setCoins(profile.getCoins() + rewardCoins);
            profile.setRenown(profile.getRenown() + rewardRenown);
            profile.kingsQuestsData.checkUpdate();
            if (profile.kingsQuestsData.getAccepted()) {
                if (!profile.kingsQuestsData.getCompleted()) {
                    profile.kingsQuestsData.setCollectedRenown(profile.kingsQuestsData.getCollectedRenown() + rewardRenown);
                }
            }

            player.sendMessage(CC.translate("&6你的奖励: &6+" + rewardCoins + "硬币 &e+" + rewardRenown + "声望"));
            if (killed >= 600) {
                player.sendMessage(CC.translate("&6&l全局奖励: &a&l成功! &7所有人获得的金币翻倍!"));
            } else {
                player.sendMessage(CC.translate("&6&l全局奖励: &c&l失败! &7总共击败了" + killed + "名玩家"));
            }
            int damage = (int) (data.getDamage() / 2);
            player.sendMessage(CC.translate("&6&l你: &c造成" + damage + "❤ &7(排名#" + getDamageRank(player) + ")"));
            if (collect.size() >= 3) {
                player.sendMessage(CC.translate("&6顶级玩家: "));
                for (int i = 0; i < 3; i++) {
                    Player top = Bukkit.getPlayer(collect.get(i).getUuid());
                    if (top != null && top.isOnline()) {
                        PlayerProfile topProfile = PlayerProfile.getPlayerProfileByUuid(top.getUniqueId());

                        int d = (int) (collect.get(i).getDamage() / 2);
                        player.sendMessage(CC.translate(" &e&l#" + (i + 1) + " " + topProfile.getFormattedName() + " &e造成了 &c" + d + "❤ 伤害"));
                    }
                }
            }

            player.sendMessage(CC.GOLD + CC.CHAT_BAR);

            if (damageMap.size() >= 50 && getDamageRank(player) > 0 && getDamageRank(player) <= 3) {
                new RagePitTopMedal().addProgress(profile, 1);
            }


        }

        damageMap.clear();
        killed = 0;
    }

    @Data
    @AllArgsConstructor
    public static class DamageData {

        private UUID uuid;
        private volatile double damage;
    }
}
