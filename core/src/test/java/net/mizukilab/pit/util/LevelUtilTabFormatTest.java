package net.mizukilab.pit.util;

import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.level.LevelUtil;
import org.bukkit.ChatColor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LevelUtilTabFormatTest {
    @Test
    public void tabPrefixKeepsVisiblePrestigeAndLevelColorsWithinLegacyTeamLimit() {
        // Cover both sides of every high-prestige color change and each level width/style.
        int[] prestiges = {0, 89, 90, 91, 120, 121, 240, 241, 300, 301, 400, 401,
                512, 513, 700, 701, 800, 801, 999, 1000, 1001};
        int[] levels = {0, 9, 10, 59, 60, 99, 100, 120};
        for (int prestige : prestiges) {
            for (int level : levels) {
                String tag = CC.translate(LevelUtil.getLevelTagTabListSpec(prestige, level));
                assertEquals("[" + level + "]", CC.stripColor(tag));
                String bracketColor = CC.translate(LevelUtil.getPrestigeColor(prestige)).substring(0, 2);
                assertEquals(bracketColor, ChatColor.getLastColors(tag.substring(0, tag.indexOf('['))));
                assertEquals(bracketColor, ChatColor.getLastColors(tag.substring(0, tag.indexOf(']'))));
                int numberAt = tag.indexOf(Integer.toString(level), tag.indexOf('[') + 1);
                assertEquals(CC.translate(LevelUtil.getLevelColor(level)),
                        ChatColor.getLastColors(tag.substring(0, numberAt)));
                for (ChatColor nameColor : ChatColor.values()) {
                    if (!nameColor.isColor()) continue;
                    // This is the full prefix passed through NameTagImpl to Team.setPrefix.
                    String prefix = tag + " " + nameColor;
                    assertTrue("Prefix exceeds 1.8 limit: prestige=" + prestige + ", level=" + level,
                            prefix.length() <= 16);
                    assertEquals("[" + level + "] ", CC.stripColor(prefix));
                    assertFalse(prefix.endsWith("§"));
                    assertEquals(nameColor.toString(), ChatColor.getLastColors(prefix));
                }
            }
        }
    }

    @Test
    public void tabCompactionDoesNotChangeOtherLevelDisplays() {
        assertEquals("&8&l[&b&l120&8&l]", LevelUtil.getLevelTag(91, 120));
        assertEquals("&8&l[&eXCI&8&l-&b&l120&8&l]", LevelUtil.getLevelTagWithRoman(91, 120));
        assertEquals("&2[&b&l120&2]", LevelUtil.getLevelTagTabListSpec(90, 120));
    }
}
