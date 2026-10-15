package net.mizukilab.pit.item.type

import org.bukkit.Material

/** Shares angel armor's life, lore and persistence rules. */
class AngelBoots : AngelChestplate() {
    override fun getInternalName(): String = "angel_boots"

    override fun getItemDisplayName(): String = "&f天使之靴"

    override fun getItemDisplayMaterial(): Material = Material.DIAMOND_BOOTS
}
