package net.mizukilab.pit.npc.type.custom;

import cn.charlotte.pit.ThePit;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.mizukilab.pit.menu.sewers.SewersMenu;
import net.mizukilab.pit.npc.AbstractPitNPC;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * @author Araykal
 * @since 2025/5/2
 */
public class SewersNpc extends AbstractPitNPC {
    @Override public cn.mythicland.npclib.api.state.NPCAnimation getAnimation() { return null; }
    @Override public cn.mythicland.npclib.api.skin.Skin getNpcSkin() { return null; }
    @Override public org.bukkit.inventory.ItemStack getNpcHeldItem() { return null; }
    @Override public void initSkin(cn.mythicland.npclib.api.NPC npc) { }
    @Override
    public String getNpcInternalName() {
        return "sewers";
    }

    @Override
    public List<String> getNpcDisplayName(Player player) {
        List<String> lines = new ObjectArrayList<>(3);
        lines.add("&9&l下水道鱼");
        lines.add("&e&l右键查看");
        return lines;
    }

    @Override
    public Location getNpcSpawnLocation() {
        return ThePit.getInstance().getPitConfig().getSewersFishNpcLocation();
    }

    @Override
    public EntityType getNpcEntityType() {
        return EntityType.SILVERFISH;
    }


    @Override
    public void handlePlayerInteract(Player player) {
        new SewersMenu().openMenu(player);
        player.playSound(player.getLocation(), Sound.ENTITY_SILVERFISH_HURT, 1, 1);
    }

}
