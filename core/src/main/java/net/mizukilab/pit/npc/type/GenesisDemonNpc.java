package net.mizukilab.pit.npc.type;

import cn.charlotte.pit.ThePit;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import cn.mythicland.npclib.api.skin.Skin;
import cn.mythicland.npclib.api.state.NPCAnimation;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * @author Yurinan
 * @since 2022/3/6 7:45
 */

public class GenesisDemonNpc extends SkinNPC {

    @Override
    public String getNpcInternalName() {
        return "GenesisDemon";
    }

    @Override
    public List<String> getNpcDisplayName(Player player) {
        List<String> lines = new ObjectArrayList<>(3);
        lines.add("&e限时活动: 光暗派系");
        lines.add("&c&l恶魔");
        lines.add("&e&l右键查看");
        return lines;
    }

    @Override
    public NPCAnimation getAnimation() {
        return null;
    }

    @Override
    public Location getNpcSpawnLocation() {
        return ThePit.getInstance().getPitConfig().getGenesisDemonNpcLocation();
    }

    @Override
    public Skin getNpcSkin() {
        return net.mizukilab.pit.npc.BuiltinNpcSkins.get(getNpcInternalName());
    }

    @Override
    public void handlePlayerInteract(Player player) {
        ThePit.getApi().openDemonMenu(player);
    }

    @Override
    public ItemStack getNpcHeldItem() {
        return null;
    }

}
