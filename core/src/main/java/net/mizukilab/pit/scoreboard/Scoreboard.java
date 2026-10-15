package net.mizukilab.pit.scoreboard;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.events.AbstractEvent;
import cn.charlotte.pit.events.genesis.GenesisTeam;
import cn.charlotte.pit.events.trigger.type.IEpicEvent;
import cn.charlotte.pit.events.trigger.type.INormalEvent;
import cn.charlotte.pit.events.trigger.type.addon.IScoreBoardInsert;
import cn.charlotte.pit.perk.AbstractPerk;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.mizukilab.pit.config.NewConfiguration;
import net.mizukilab.pit.config.PitGlobalConfig;
import net.mizukilab.pit.config.PitWorldConfig;
import net.mizukilab.pit.events.impl.major.RagePitEvent;
import net.mizukilab.pit.perk.type.streak.tothemoon.ToTheMoonMegaStreak;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.ProgressBar;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.chat.RomanUtil;
import net.mizukilab.pit.util.level.LevelUtil;
import net.mizukilab.pit.util.scoreboard.AssembleAdapter;
import net.mizukilab.pit.util.time.TimeUtil;
import nya.Skip;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
@Skip
public class Scoreboard implements AssembleAdapter {

    private record RageFrame(RagePitEvent event, java.util.Map<java.util.UUID, RagePitEvent.DamageStanding> standings) { }
    private final ThreadLocal<RageFrame> rageFrame = new ThreadLocal<>();

    private static final class CommonFrame {
        String title;
        String date;
    }
    private final ThreadLocal<CommonFrame> commonFrame = new ThreadLocal<>();

    @Override
    public void beginUpdate() {
        commonFrame.set(new CommonFrame());
        rageFrame.remove();
        IEpicEvent event = ThePit.getInstance().getEventFactory().getActiveEpicEvent();
        if (event instanceof RagePitEvent rage && rage.isActive()) {
            rageFrame.set(new RageFrame(rage, rage.getDamageStandings()));
        }
    }

    @Override
    public void endUpdate() {
        commonFrame.remove();
        rageFrame.remove();
    }

    private final SimpleDateFormat dateFormat = new SimpleDateFormat(NewConfiguration.INSTANCE.getDateFormat());
    private final DecimalFormat numFormat = new DecimalFormat("0.0");
    private final DecimalFormat numFormatTwo = new DecimalFormat("0.00");
    private final DecimalFormat df = new DecimalFormat(",###,###,###,###");

    private long lastAnimationTime = 0;
    private int animationTick = 0;
    private long animationStartTime = System.currentTimeMillis();

    @Override
    public String getTitle(Player player) {
        CommonFrame frame = commonFrame.get();
        if (frame == null) return currentTitle();
        if (frame.title == null) frame.title = currentTitle();
        return frame.title;
    }

    private String currentTitle() {
        List<String> title = NewConfiguration.INSTANCE.getScoreBoardAnimation();
        long currentTime = System.currentTimeMillis();
        if (title == null || title.isEmpty()) return "&e&lTHE PIT";
        long animationInterval = Math.max(1, NewConfiguration.INSTANCE.getMaxScoreboardAnimationInterval());
        long timeSinceStart = currentTime - animationStartTime;
        int totalFrames = title.size();
        int currentFrame = (int) ((timeSinceStart / animationInterval) % totalFrames);
        if (currentTime - lastAnimationTime >= animationInterval) {
            animationTick = currentFrame;
            lastAnimationTime = currentTime;
            if (timeSinceStart > Long.MAX_VALUE / 2) {
                animationStartTime = currentTime;
            }
        }
        int safeIndex = Math.max(0, Math.min(animationTick, totalFrames - 1));
        return title.get(safeIndex);
    }


    private String currentDate(long now) {
        CommonFrame frame = commonFrame.get();
        if (frame == null) return dateFormat.format(now);
        if (frame.date == null) frame.date = dateFormat.format(now);
        return frame.date;
    }

    @Override
    public List<String> getLines(Player player) {

        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!profile.isLoaded()) {
            return NewConfiguration.INSTANCE.getLoadingBoardTips();
        }
        List<String> lines = new ObjectArrayList<>(16);

        int prestige = profile.getPrestige();
        int level = profile.getLevel();

        long currentSystemTime = System.currentTimeMillis();
        ThePit instance = ThePit.getInstance();
        if (NewConfiguration.INSTANCE.getScoreboardShowtime()) {
            lines.add("&7" +
                    currentDate(currentSystemTime) + " &8" + instance.getServerId());
        }

        IEpicEvent activeEpicEvent = instance.getEventFactory().getActiveEpicEvent();
        INormalEvent activeNormalEvent = instance.getEventFactory().getActiveNormalEvent();
        if (activeEpicEvent != null) {
            AbstractEvent event = (AbstractEvent) activeEpicEvent;
            lines.add(" ");
            lines.add("&f事件: &6" + event.getEventName());
            if (event instanceof RagePitEvent ragePit && ragePit.isActive()) {
                lines.add("&f剩余: &a" + TimeUtil.millisToTimer(ragePit.getTimer().getRemaining()));
                RageFrame frame = rageFrame.get();
                RagePitEvent.DamageStanding standing = (frame != null && frame.event() == ragePit
                        ? frame.standings() : ragePit.getDamageStandings()).get(player.getUniqueId());
                if (standing != null) {
                    int damage = (int) (standing.damage() / 2);
                    int rank = standing.rank();
                    lines.add("&f伤害: &c" + damage + "❤ &7(#" + rank + ")");
                }
                int killed = ragePit.getKilled();

                lines.add("&f总击杀: " + (killed >= 600 ? "&a" : "&c") + killed + "&7/600");
            } else if (event instanceof IScoreBoardInsert insert) {
                try {
                    List<String> insert1 = insert.insert(player);
                    if (insert1 != null) {
                        lines.addAll(insert1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    //ignore
                }

            }
        } else if (activeNormalEvent != null) {
            if (activeNormalEvent instanceof IScoreBoardInsert insert) {
                lines.add(" ");
                lines.add("&f事件: &a" + ((AbstractEvent) activeNormalEvent).getEventName());
                List<String> insert1 = insert.insert(player);
                if (insert1 != null) {
                    lines.addAll(insert1);
                }
            }
        }
        lines.add("");

        int bounty = profile.getBounty();

        String genesisPrefix = "";
        String genesisTeam = "";
        PitWorldConfig pitConfig = instance.getPitConfig();
        if (bounty == 0) {
            if (pitConfig.isGenesisEnable() && profile.getGenesisData().getTeam() != GenesisTeam.NONE) {
                switch (profile.getGenesisData().getTeam()) {
                    case ANGEL -> {
                        genesisPrefix = "&b";
                        genesisTeam = genesisPrefix + " ♆";
                    }
                    case DEMON -> {
                        genesisPrefix = "&c";
                        genesisTeam = genesisPrefix + " ♨";
                    }
                }
            }
        }
        if (prestige > 0) {
            lines.add("&f精通: &e" + RomanUtil.convert(prestige));
        }
        lines.add("&f等级: " + LevelUtil.getLevelTag(prestige, level) + genesisTeam);
        if (level >= NewConfiguration.INSTANCE.getMaxLevel()) {
            lines.add("&f经验值: &b已满!");
        } else {
            if (!profile.getPlayerOption().isLevelBar() && prestige <= 100) {
                lines.add("&f下级: &b" + numFormatTwo.format((LevelUtil.getLevelTotalExperience(prestige, level + 1) - profile.getExperience())) + " Xp");
            } else {
                lines.add("&f下级: ");
                lines.add("§7[ " + ProgressBar.getProgressBar(profile.getExperience(), LevelUtil.getLevelTotalExperience(prestige, level), LevelUtil.getLevelTotalExperience(prestige, level + 1), 9) + " §7]");
            }
        }

        if (profile.getCurrentQuest() != null) {
            lines.add(" ");
            lines.add("&f击杀: &a" + profile.getCurrentQuest().getCurrent() + "/" + profile.getCurrentQuest().getTotal());
            if (profile.getCurrentQuest().getCurrent() == profile.getCurrentQuest().getTotal()) {
                lines.add("&f状态: &a完成");
            } else {
                if (profile.getCurrentQuest().getEndTime() > currentSystemTime) {
                    lines.add("&f状态: &a"
                            + TimeUtil.millisToTimer(
                            profile.getCurrentQuest().getEndTime() - currentSystemTime));
                } else {
                    lines.add("&f状态: &c超时");
                }
            }
        }

        lines.add(" ");
        if (profile.getCoins() >= 10000) {
            lines.add("&f硬币: &6" + df.format(profile.getCoins()));
        } else {
            lines.add("&f硬币: &6" + numFormatTwo.format(profile.getCoins()));
        }
        //if Player is in Fight:
        boolean statusToggle = true;
        if (activeEpicEvent == null) {
            if (activeNormalEvent != null) {
                statusToggle = !(activeNormalEvent instanceof IScoreBoardInsert);
            }
        } else {
            statusToggle = false;
        }
        if (statusToggle) {
            final AbstractPerk currentStreak = profile.getActiveMegaStreakObj();
            final long combatRemaining = profile.getCombatTimer().getRemaining();
            final boolean inCombat = combatRemaining > 0;
            String status = currentStreak != null
                    ? CC.translate(currentStreak.getDisplayName())
                    : (inCombat ? "&c战斗中" : "&a空闲中");
            if (inCombat) {
                // Round up so a live combat timer never displays 0s.
                status += "&7 (" + (long) Math.ceil(combatRemaining / 1000D) + "s)";
            }
            lines.add(" ");
            lines.add("&f状态: " + status);
            if (inCombat) {
                lines.add("&f连杀: &a" + numFormat.format(profile.getStreakKills()));
            }

            if (currentStreak != null && currentStreak == ToTheMoonMegaStreak.getInstance()) {
                Double storedExp = ToTheMoonMegaStreak.getCache().get(player.getUniqueId());
                if (storedExp == null) {
                    storedExp = 0.0;
                }
                final double streakKills = profile.getStreakKills();
                final double multiple = Math.min(1.0, (streakKills - 100) * 0.005);

                lines.add("&f已储: &b" + df.format(storedExp) + "&7 (&a" + numFormat.format(multiple) + "x&7)");
            }
        }
        //if Player have a bounty:
        if (bounty != 0) {
            lines.add("&f赏金: " + profile.bountyColor() + "&l" + bounty + "g");
        }
        //Damage reduce caused by Perks
        if (profile.getStrengthNum() > 0 && !profile.getStrengthTimer().hasExpired()) {
            lines.add("&f力量: &c+" + profile.getStrengthNum() * 4 + "% &7 (" + numFormat.format(profile.getStrengthTimer().getRemaining() / 1000D) + ")");
        }

        boolean gladiator = profile.isChoosePerk("Gladiator");

        if (gladiator && profile.isInArena()) {
            int boost = 0;
            try {
                boost = PlayerUtil.getNearbyPlayers(player.getLocation(), 8).size();
            } catch (Exception ignored) {
            }
            int sybilLevel = Utils.getEnchantLevel(profile.leggings, "sybil");
            if (sybilLevel > 0) {
                boost += sybilLevel + 1;
            }

            if (boost > 10) {
                boost = 10;
            }
            if (boost >= 3) {
                lines.add("&f角斗士: &9-" + boost * 3 + "%");
            }

        }

        lines.add(" ");
        PitGlobalConfig globalConfig = ThePit.getInstance().getGlobalConfig();
        if (globalConfig.isCooldownView()) {
            int remainTime = ThePit.getInstance().getMapSelector().getRemainTime();
            if (remainTime > 0 && remainTime < 30) {
                lines.add("&a切换地图! &c(" + remainTime + "s)");
            }
        }
        if (instance.getRebootRunnable().getCurrentTask() != null) {
            lines.add("&c重启! &7" + TimeUtil.millisToRoundedTime(instance.getRebootRunnable().getCurrentTask().getEndTime() - currentSystemTime).replace(" ", "") + "后");
        }
        if (ThePit.isDEBUG_SERVER()) {
            lines.add("&3测试 " + (instance.getGlobalConfig().isDebugServerPublic() ? "&a#Public" : "&c#Private"));
        } else {
            lines.add(instance.getGlobalConfig().getServerName());
        }
        return lines;
    }
}
