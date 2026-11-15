package cn.mythicland.framework.bukkit.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Custom item data is immutable at the component boundary. Always replace it after editing. */
public final class PaperItemData {
    private PaperItemData() {}
    public static CompoundTag custom(ItemStack item) {
        return item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
    public static void custom(ItemStack item, CompoundTag tag) {
        CustomData.set(DataComponents.CUSTOM_DATA, item, tag);
    }
    public static CompoundTag save(ItemStack item) {
        return (CompoundTag) ItemStack.CODEC.encodeStart(MinecraftServer.getServer().registryAccess()
                .createSerializationContext(NbtOps.INSTANCE), item).getOrThrow();
    }
}
