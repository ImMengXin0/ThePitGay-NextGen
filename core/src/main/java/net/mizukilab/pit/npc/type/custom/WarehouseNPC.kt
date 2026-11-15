package net.mizukilab.pit.npc.type.custom

import cn.charlotte.pit.ThePit
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.mizukilab.pit.menu.warehouse.WarehouseMainMenu
import net.mizukilab.pit.npc.BuiltinNpcSkins
import net.mizukilab.pit.npc.type.SkinNPC
import net.mizukilab.pit.util.PlayerUtil
import net.mizukilab.pit.util.chat.CC
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * @Author: Araykal
 * @Date: 2025/6/21
 */
class WarehouseNPC : SkinNPC() {
    override fun getAnimation(): cn.mythicland.npclib.api.state.NPCAnimation? = null
    override fun getNpcSkin(): cn.mythicland.npclib.api.skin.Skin = BuiltinNpcSkins.get(npcInternalName)
    override fun getNpcHeldItem(): org.bukkit.inventory.ItemStack? = null
    override fun getNpcInternalName(): String {
        return "warehouse_npc"
    }

    override fun getNpcDisplayName(player: Player): List<String> {
        val lines: MutableList<String> = ObjectArrayList(3)
        lines.add("&6&l寄存所")
        if (PlayerUtil.isPlayerUnlockedPerk(player, "cloak_room")) {
            lines.add("&e&l右键查看")
        } else {
            lines.add("&c解锁指定天赋后解锁")
        }
        return lines
    }

    override fun getNpcSpawnLocation(): Location {
        return ThePit.getInstance().pitConfig.warehouseNpcLocation
    }

    override fun handlePlayerInteract(player: Player) {
        if (PlayerUtil.isPlayerUnlockedPerk(player, "cloak_room")) {
            WarehouseMainMenu().openMenu(player)
        } else {
            player.sendMessage(CC.translate("&c&l无法交流! &c需解锁天赋 &e寄存所产业"))
        }
    }

}
