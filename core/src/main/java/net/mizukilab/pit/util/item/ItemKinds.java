package net.mizukilab.pit.util.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

public final class ItemKinds {
    private ItemKinds() { }
    public static boolean isSword(ItemStack item) { return item != null && item.is(ItemTags.SWORDS); }
    public static boolean isBow(ItemStack item) { return item != null && item.getItem() instanceof BowItem; }
    public static boolean isRod(ItemStack item) { return item != null && item.getItem() instanceof FishingRodItem; }
    public static boolean isArmor(ItemStack item) {
        if (item == null) return false;
        var equipment = item.get(DataComponents.EQUIPPABLE);
        if (equipment == null) return false;
        EquipmentSlot slot = equipment.slot();
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }
}
