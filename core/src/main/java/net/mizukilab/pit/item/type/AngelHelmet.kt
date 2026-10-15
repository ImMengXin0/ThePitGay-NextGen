package net.mizukilab.pit.item.type

import org.bukkit.Material

class AngelHelmet : AngelChestplate() {
    override fun getInternalName(): String = "angel_helmet"

    override fun getItemDisplayName(): String = "&f天使之盔"

    override fun getItemDisplayMaterial(): Material = Material.DIAMOND_HELMET
}
