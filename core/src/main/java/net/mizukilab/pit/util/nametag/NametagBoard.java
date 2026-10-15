package net.mizukilab.pit.util.nametag;

import cn.charlotte.pit.ThePit;
import io.irina.backports.utils.SWMRHashTable;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import net.mizukilab.pit.util.LzScheduler;
import org.apache.commons.lang.StringEscapeUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
public class NametagBoard {

    private final UUID uuid;

    private NametagHandler handler;

    private Set<String> bufferedTeams = new ObjectOpenHashSet<>();
    private Map<String, List<String>> bufferedPlayers = new SWMRHashTable<>();

    /**
     * Nametag Board.
     *
     * @param player  that board belongs to.
     * @param handler instance.
     */
    public NametagBoard(Player player, NametagHandler handler) {
        this.uuid = player.getUniqueId();
        this.handler = handler;
        this.setup(player);
    }

    /**
     * Setup Nametag Board.
     *
     * @param player that board belongs to.
     */
    private void setup(Player player) {
        Scoreboard scoreboard = getScoreboard();

        // Update Bukkit scoreboard.
        player.setScoreboard(scoreboard);
    }

    /**
     * Get Scoreboard Object.
     *
     * @return existing scoreboard if in hook, or create new one.
     */
    public Scoreboard getScoreboard() {
        Player player = Bukkit.getPlayer(getUuid());
        if (getHandler().isHook() || player.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
            return player.getScoreboard();
        } else {
            return Bukkit.getScoreboardManager().getNewScoreboard();
        }
    }

    /**
     * Update Health Slot.
     *
     * @param player     object.
     * @param scoreboard of player.
     */
    private void updateHealthBelow(Player player, Scoreboard scoreboard) {
        if (this.handler.getAdapter().showHealthBelowName(player)) {
            if (scoreboard.getObjective(DisplaySlot.BELOW_NAME) == null) {
                Objective objective = scoreboard.getObjective("showhealth");
                if(objective == null) {
                    objective = scoreboard.registerNewObjective("showhealth", "health");
                }
                objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
                objective.setDisplayName(ChatColor.RED + StringEscapeUtils.unescapeJava("\u2764"));
                // Ensures that 0 isn't displayed if they haven't lost health.
                for (Player loopPlayer : Bukkit.getOnlinePlayers()) {
                    objective.getScore(loopPlayer).setScore((int) Math.floor(loopPlayer.getHealth()));
                }
            }
        } else {
            if (scoreboard.getObjective(DisplaySlot.BELOW_NAME) != null) {
                Objective objective = scoreboard.getObjective(DisplaySlot.BELOW_NAME);
                objective.unregister();
            }
        }
    }

    /**
     * Get's or creates a team for the scoreboard.
     *
     * @param scoreboard of team.
     * @param name       of team.
     * @return new or existing team.
     */
    private Team getOrRegisterTeam(Scoreboard scoreboard, String name) {
        Team team = scoreboard.getTeam(name);

        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }

        return team;
    }

    /**
     * Update Nametags slot.
     *
     * @param player     object.
     * @param scoreboard of player.
     */
    private void updateNametags(Scoreboard scoreboard, List<BufferedNametag> nametags) {

        if (nametags == null) {
            return;
        }

        Set<String> toReturn = new ObjectOpenHashSet<>();
        Map<String, List<String>> strings = new Object2ObjectOpenHashMap<>();

        for (BufferedNametag bufferedNametag : nametags) {
            Team team = this.getOrRegisterTeam(scoreboard, bufferedNametag.getGroupName());

            toReturn.add(team.getName());
            this.getBufferedTeams().remove(team.getName());

            String prefix = bufferedNametag.getPrefix() != null ? bufferedNametag.getPrefix() : ChatColor.RESET.toString();
            String suffix = bufferedNametag.getSuffix() != null ? bufferedNametag.getSuffix() : ChatColor.RESET.toString();

            if (!prefix.equals(team.getPrefix())) team.setPrefix(prefix);
            if (!suffix.equals(team.getSuffix())) team.setSuffix(suffix);

            if (bufferedNametag.getPlayer() != null) {
                String name = bufferedNametag.getPlayer().getName();
                if (!team.hasEntry(name)) {
                    team.addEntry(name);
                }
                List<String> inner = new ObjectArrayList<>();
                String name1 = team.getName();
                if (strings.containsKey(name1)) {
                    inner = strings.get(name1);
                }
                inner.add(name);
                strings.put(name1, inner);
            }

            // Friendly Invisibility.
            if (team.canSeeFriendlyInvisibles() != bufferedNametag.isFriendlyInvis()) {
                team.setCanSeeFriendlyInvisibles(bufferedNametag.isFriendlyInvis());
            }
        }

        // Unregister teams that are no longer in use.
        for (String newGroupName : this.getBufferedTeams()) {
            Team team = scoreboard.getTeam(newGroupName);

            if (team == null) {
                continue;
            }

            for (String entry : team.getEntries()) {
                team.removeEntry(entry);
            }
            team.unregister();
        }

        this.getBufferedTeams().clear();
        this.getBufferedTeams().addAll(toReturn);

        // Clean out members who are no longer in the team.
        for (String teamName : this.getBufferedTeams()) {
            List<String> members = strings.get(teamName);
            if (members == null) {
                continue;
            }
            Team team = scoreboard.getTeam(teamName);

            if (team == null) {
                continue;
            }

            for (String entry : team.getEntries()) {
                if (members.contains(entry)) {
                    continue;
                }
                team.removeEntry(entry);
            }
        }
    }

    /**
     * Update Health and Nametag slots.
     */
    public void update() {
        if (!Bukkit.isPrimaryThread()) {
            LzScheduler.ensureMain(this::update);
            return;
        }
        Player player = Bukkit.getPlayer(getUuid());
        if (player == null || !player.isOnline()) return;
        update(this.handler.getAdapter().getPlate(player));
    }

    /** Apply plates prepared once for this update, while keeping health viewer-specific. */
    public void update(List<BufferedNametag> nametags) {
        if (!Bukkit.isPrimaryThread()) {
            LzScheduler.ensureMain(() -> update(nametags));
            return;
        }
        Player player = Bukkit.getPlayer(getUuid());
        if (player == null || !player.isOnline()) return;
        Scoreboard scoreboard = this.getScoreboard();
        this.updateHealthBelow(player, scoreboard);
        this.updateNametags(scoreboard, nametags);
    }

    /**
     * Cleanup Board.
     */
    public void cleanup() {
        bufferedPlayers.clear();
        bufferedTeams.clear();
    }

}
