package cn.charlotte.pit.menu.rune

import cn.charlotte.pit.menu.rune.button.DJEnchantButton
import net.mizukilab.pit.util.Utils
import net.mizukilab.pit.util.menu.Button
import net.mizukilab.pit.util.menu.menus.PagedMenu
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * @author Araykal
 * @since 2025/1/16
 */
class SecondChoseRuneMenu(private val itemStack: ItemStack, var index: Int) :
    PagedMenu("选择一项DJ音乐", emptyList()) {

    override fun getButtons(player: Player): MutableMap<Int, Button> {
        if (Utils.getMythicItem(itemStack) == null) return mutableMapOf()
        flushButton(MusicRuneOptions.available().map { DJEnchantButton(it, itemStack, index) })
        return super.getButtons(player)
    }
}
