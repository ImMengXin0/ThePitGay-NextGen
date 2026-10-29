package net.mizukilab.pit.npc.type;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import cn.mythicland.npclib.api.skin.Skin;
import cn.mythicland.npclib.api.state.NPCAnimation;
import net.mizukilab.pit.menu.prestige.PrestigeMainMenu;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.level.LevelUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * @Author: Misoryan
 * @Created_In: 2021/1/1 13:44
 */

public class PrestigeNPC extends SkinNPC {

    @Override
    public String getNpcInternalName() {
        return "prestige";
    }

    @Override
    public List<String> getNpcDisplayName(Player player) {
        List<String> lines = new ObjectArrayList<>(3);
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        lines.add("&6&l精通");
        if (profile.getLevel() >= 120 || profile.getPrestige() > 0) {
            lines.add("&e&l右键查看");
        } else {
            lines.add("&c在 " + LevelUtil.getLevelTag(profile.getPrestige(), 120) + " &c时解锁");
        }
        return lines;
    }

    @Override
    public NPCAnimation getAnimation() {
        return null;
    }

    @Override
    public Location getNpcSpawnLocation() {
        return ThePit.getInstance().getPitConfig().getPrestigeNpcLocation();
    }

    @Override
    public Skin getNpcSkin() {
        return net.mizukilab.pit.npc.BuiltinNpcSkins.get(getNpcInternalName());
    }

    @Override
    public void handlePlayerInteract(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (profile.getLevel() < 120 && profile.getPrestige() == 0) {
            player.sendMessage(CC.translate("&c&l等级不足! &7精通在 " + LevelUtil.getLevelTag(profile.getPrestige(), 120) + " &7时解锁."));
        } else {
            new PrestigeMainMenu().openMenu(player);
        }
    }

    @Override
    public ItemStack getNpcHeldItem() {
        return null;
    }
}
