package net.mizukilab.pit.item.type

import org.bukkit.Material

class AngelLeggings : AngelChestplate() {
    override fun getInternalName(): String = "angel_leggings"

    override fun getItemDisplayName(): String = "&f天使之裤"

    override fun getItemDisplayMaterial(): Material = Material.DIAMOND_LEGGINGS
}
