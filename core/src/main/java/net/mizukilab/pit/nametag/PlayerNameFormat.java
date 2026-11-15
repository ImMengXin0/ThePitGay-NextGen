package net.mizukilab.pit.nametag;

import cn.charlotte.pit.data.PlayerProfile;
import me.clip.placeholderapi.PlaceholderAPI;
import net.mizukilab.pit.config.TabConfiguration;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.nametag.BufferedNametag;
import net.mizukilab.pit.util.rank.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Formats use the subject player's PAPI values and never reveal a nicked player's real name. */
public final class PlayerNameFormat {
    private static final java.util.Map<Player, String> OWNED_NAMES = new java.util.WeakHashMap<>();
    private PlayerNameFormat() { }

    public static String render(String format, Player target, PlayerProfile profile, String prefix, String suffix) {
        String name = profile.isNicked() && profile.getNickName() != null ? profile.getNickName() : target.getName();
        String result = format.replace("{prefix}", prefix).replace("{suffix}", suffix)
                .replace("{name}", name);
        if (result.contains("{level}")) result = result.replace("{level}", profile.getFormattedLevelTagTabSpec());
        if (result.contains("{rank-color}")) result = result.replace("{rank-color}", RankUtil.getPlayerRankColor(target.getUniqueId()));
        if (result.contains("%") && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            result = PlaceholderAPI.setPlaceholders(target, result);
        }
        return CC.translate(result);
    }

    public static BufferedNametag apply(String group, Player target, PlayerProfile profile, String prefix, String suffix) {
        TabConfiguration config = TabConfiguration.INSTANCE;
        if (config.getPlayerListEnabled()) {
            String text = render(config.getPlayerListFormat(), target, profile, prefix, suffix);
            // Bukkit broadcasts only changes; avoid touching the player-info packet on every viewer update.
            if (!text.equals(target.getPlayerListName())) target.setPlayerListName(text);
            OWNED_NAMES.put(target, text);
        } else reset(target);
        return new BufferedNametag(group,
                config.getNametagEnabled() ? render(config.getNametagPrefix(), target, profile, prefix, suffix) : "",
                config.getNametagEnabled() ? render(config.getNametagSuffix(), target, profile, prefix, suffix) : "",
                false, target);
    }

    public static void reset(Player target) {
        String previous = OWNED_NAMES.remove(target);
        if (previous != null && previous.equals(target.getPlayerListName())) target.setPlayerListName(null);
    }
}
