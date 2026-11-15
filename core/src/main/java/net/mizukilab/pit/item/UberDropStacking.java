package net.mizukilab.pit.item;

import cn.mythicland.framework.bukkit.item.PaperItemData;
import net.mizukilab.pit.item.type.UberPlusDrop;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/** Removes only the obsolete per-drop stack separator; retains all other item data. */
public final class UberDropStacking {
    private UberDropStacking() { }

    public static ItemStack normalize(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_CHEST) return item;
        String internal = ItemUtil.getInternalName(item);
        if (!"uber_drop".equals(internal) && !"uber_plus_drop".equals(internal)) return item;
        var extra = ItemUtil.getExtra(item);
        boolean legacyPlus = "uber_drop".equals(internal) && UberPlusDrop.isPlusDrop(item);
        boolean separator = extra.get("uuid") instanceof net.minecraft.nbt.IntTag;
        if (!separator && !legacyPlus) return item;
        var handle = CraftItemStack.asNMSCopy(item);
        var custom = PaperItemData.custom(handle);
        extra = extra.copy();
        if (separator) extra.remove("uuid");
        if (legacyPlus) extra.putString("internal", "uber_plus_drop");
        custom.put("extra", extra);
        PaperItemData.custom(handle, custom);
        return CraftItemStack.asBukkitCopy(handle);
    }
}
