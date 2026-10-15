package net.mizukilab.pit.npc.type;

import cn.charlotte.pit.ThePit;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.jitse.npclib.api.skin.Skin;
import net.jitse.npclib.api.state.NPCAnimation;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * @author Yurinan
 * @since 2022/3/5 16:53
 */

public class GenesisAngelNpc extends SkinNPC {

    @Override
    public String getNpcInternalName() {
        return "GenesisAngel";
    }

    @Override
    public List<String> getNpcDisplayName(Player player) {
        List<String> lines = new ObjectArrayList<>(3);
        lines.add("&e限时活动: 光暗派系");
        lines.add("&f&l天使");
        lines.add("&e&l右键查看");
        return lines;
    }

    @Override
    public NPCAnimation getAnimation() {
        return null;
    }

    @Override
    public Location getNpcSpawnLocation() {
        return ThePit.getInstance().getPitConfig().getGenesisAngelNpcLocation();
    }

    @Override
    public Skin getNpcSkin() {
        return net.mizukilab.pit.npc.BuiltinNpcSkins.get(getNpcInternalName());
    }

    @Override
    public void handlePlayerInteract(Player player) {
        ThePit.getApi().openAngelMenu(player);
    }

    @Override
    public ItemStack getNpcHeldItem() {
        return null;
    }

}
