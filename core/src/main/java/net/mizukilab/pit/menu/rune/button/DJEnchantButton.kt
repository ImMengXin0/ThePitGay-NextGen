package cn.charlotte.pit.menu.rune.button

import cn.charlotte.pit.data.sub.EnchantmentRecord
import cn.charlotte.pit.menu.rune.MusicRuneOptions
import net.mizukilab.pit.enchantment.AbstractEnchantment
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem
import net.mizukilab.pit.util.chat.CC
import net.mizukilab.pit.util.item.ItemBuilder
import net.mizukilab.pit.util.item.ItemUtil
import net.mizukilab.pit.util.menu.Button
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack

/**
 * @author Araykal
 * @since 2025/1/16
 */
class DJEnchantButton(
    private val enchantment: AbstractEnchantment,
    itemStack: ItemStack,
    private val index: Int
) : Button() {
    private val expectedItem = itemStack.clone()
    private var applied = false

    override fun getButtonItem(player: Player): ItemStack {
        val lore = enchantment.getUsefulnessLore(1).split("/s").toTypedArray()
        return ItemBuilder(Material.JUKEBOX)
            .name("&9" + enchantment.getEnchantName())
            .lore(*lore)
            .shiny()
            .build()
    }

    override fun clicked(
        player: Player,
        slot: Int,
        clickType: ClickType,
        hotbarButton: Int,
        currentItem: ItemStack
    ) {
        if (applied || (clickType != ClickType.LEFT && clickType != ClickType.RIGHT)) return
        val inventory = player.inventory
        if (index !in 0 until inventory.size || index == inventory.heldItemSlot) return
        val rune = inventory.itemInHand ?: return
        if (rune.amount < 1 || ItemUtil.getInternalName(rune) != "musical_rune") return
        val current = inventory.getItem(index) ?: return
        if (current.amount != 1 || !current.isSimilar(expectedItem) ||
            ItemUtil.getInternalName(current) != "mythic_leggings") return
        val active = MusicRuneOptions.available().firstOrNull { it.nbtName == enchantment.nbtName } ?: return
        // Parse a detached copy, so failed validation never mutates an equipment-cache object.
        val leggings = MythicLeggingsItem().apply { loadFromItemStack(current.clone()) }
        if (MusicRuneOptions.hasMusic(leggings)) return
        leggings.enchantments[active] = 1
        leggings.enchantmentRecords += EnchantmentRecord(player.name, "MusicRune", System.currentTimeMillis())
        val result = leggings.toItemStack() ?: return
        val remaining = if (rune.amount > 1) rune.clone().apply { amount -= 1 } else null
        applied = true
        inventory.setItem(index, result)
        inventory.setItem(inventory.heldItemSlot, remaining)
        player.playSound(player.location, Sound.ANVIL_USE, 1f, 99f)
        player.sendMessage(CC.translate("&6&l铸造！ &7成功为你的神话之裤增加了${active.enchantName}附魔"))
        player.closeInventory()
    }
}
