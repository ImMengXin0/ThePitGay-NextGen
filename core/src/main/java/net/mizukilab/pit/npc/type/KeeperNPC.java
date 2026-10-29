package net.mizukilab.pit.npc.type;

import cn.charlotte.pit.ThePit;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import cn.mythicland.npclib.api.skin.Skin;
import cn.mythicland.npclib.api.state.NPCAnimation;
import net.mizukilab.pit.menu.hub.HubMenu;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * @Author: Misoryan
 * @Created_In: 2021/2/6 16:28
 */

public class KeeperNPC extends SkinNPC {

    @Override
    public String getNpcInternalName() {
        return "keeper";
    }

    @Override
    public List<String> getNpcDisplayName(Player player) {
        List<String> lines = new ObjectArrayList<>(3);
        lines.add("&6&l看门狗");
        lines.add("&7玩累了？点击返回大厅");
        return lines;
    }

    @Override
    public NPCAnimation getAnimation() {
        return null;
    }

    @Override
    public Location getNpcSpawnLocation() {
        return ThePit.getInstance().getPitConfig().getKeeperNpcLocation();
    }

    @Override
    public Skin getNpcSkin() {
        return net.mizukilab.pit.npc.BuiltinNpcSkins.get(getNpcInternalName());
    }

    @Override
    public void handlePlayerInteract(Player player) {
        new HubMenu().openMenu(player);
    }

    @Override
    public ItemStack getNpcHeldItem() {
        return null;
    }
}
