package net.mizukilab.pit.npc.type;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerMailData;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.mail.Mail;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import cn.mythicland.npclib.api.skin.Skin;
import cn.mythicland.npclib.api.state.NPCAnimation;
import net.mizukilab.pit.menu.mail.MailMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * @Author: EmptyIrony
 * @Date: 2021/3/25 19:03
 */

public class MailNpc extends SkinNPC {

    @Override
    public String getNpcInternalName() {
        return "mail";
    }

    @Override
    public List<String> getNpcDisplayName(Player player) {
        List<String> hologram = new ObjectArrayList<>(3);
        final PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        final PlayerMailData mailData = profile.getMailData();

        int unread = 0;
        for (Mail mail : mailData.getMails()) {
            if (!mail.isClaimed()) {
                unread++;
            }
        }
        hologram.add("&6&l邮件系统");
        if (unread == 0) {
            hologram.add("&e&l右键查看");
        }
        if (unread > 0) {
            hologram.add((System.currentTimeMillis() % 2 == 0 ? "&a" : "&2") + "您有 " + unread + " 封未读邮件");
        }

        return hologram;
    }

    @Override
    public NPCAnimation getAnimation() {
        return null;
    }

    @Override
    public Location getNpcSpawnLocation() {
        return ThePit.getInstance().getPitConfig().getMailNpcLocation();
    }

    @Override
    public Skin getNpcSkin() {
        return net.mizukilab.pit.npc.BuiltinNpcSkins.get(getNpcInternalName());
    }

    @Override
    public void handlePlayerInteract(Player player) {
        new MailMenu().openMenu(player);
    }

    @Override
    public ItemStack getNpcHeldItem() {
        return new ItemStack(Material.CHEST);
    }
}
