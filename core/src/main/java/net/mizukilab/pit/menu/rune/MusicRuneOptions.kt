package cn.charlotte.pit.menu.rune

import cn.charlotte.pit.ThePit
import net.mizukilab.pit.enchantment.AbstractEnchantment
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity
import net.mizukilab.pit.item.AbstractPitItem
import org.bukkit.ChatColor

/** Resolve the active implementation, including late Addon/Repair registrations. */
object MusicRuneOptions {
    private fun label(enchantment: AbstractEnchantment): String =
        ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', enchantment.enchantName)) ?: ""

    fun isMusic(enchantment: AbstractEnchantment): Boolean = label(enchantment).startsWith("DJ #")

    fun available(): List<AbstractEnchantment> =
        ThePit.getInstance().enchantmentFactor.enchantmentMap.values
            .filter { isMusic(it) && it.rarity != EnchantmentRarity.DISABLED && it.rarity != EnchantmentRarity.REMOVED }
            .sortedWith(compareBy<AbstractEnchantment> {
                label(it).removePrefix("DJ #").toIntOrNull() ?: Int.MAX_VALUE
            }.thenBy { label(it) }.thenBy { it.nbtName })

    fun hasMusic(item: AbstractPitItem): Boolean =
        item.enchantmentRecords.any { it.description == "MusicRune" } ||
            item.enchantments.keys.any { isMusic(it) && item.enchantments.getInt(it) > 0 }
}
