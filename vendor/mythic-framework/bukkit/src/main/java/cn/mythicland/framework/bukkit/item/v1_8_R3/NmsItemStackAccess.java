package cn.mythicland.framework.bukkit.item.v1_8_R3;

import net.minecraft.nbt.*;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.TreeSet;

/** Historical namespace; all access now uses Paper 1.21.11 mappings. */
public final class NmsItemStackAccess {
    private NmsItemStackAccess() {}
    public static net.minecraft.world.item.ItemStack read(ItemStack item) { return CraftItemStack.asNMSCopy(item); }
    public static boolean matches(ItemStack first, ItemStack second) { return Objects.equals(first, second); }
    public static void write(CompoundTag tag, DataOutput output, boolean canonical) throws IOException {
        if (!canonical) { NbtIo.write(tag, output); return; }
        output.writeByte(tag.getId());
        output.writeUTF("");
        writePayload(tag, output);
    }
    private static void writePayload(Tag tag, DataOutput output) throws IOException {
        if (tag instanceof CompoundTag compound) {
            for (String key : new TreeSet<>(compound.keySet())) {
                Tag value = Objects.requireNonNull(compound.get(key));
                output.writeByte(value.getId());
                output.writeUTF(key);
                writePayload(value, output);
            }
            output.writeByte(Tag.TAG_END);
        } else if (tag instanceof ListTag list) {
            byte elementType = list.identifyRawElementType();
            output.writeByte(elementType);
            output.writeInt(list.size());
            for (Tag value : list) {
                // Modern NBT represents mixed lists as compounds, wrapping scalar
                // values and escaping compounds whose sole key is the wrapper key.
                if (elementType == Tag.TAG_COMPOUND && (!(value instanceof CompoundTag compound)
                        || (compound.size() == 1 && compound.contains("")))) {
                    CompoundTag wrapper = new CompoundTag();
                    wrapper.put("", value);
                    writePayload(wrapper, output);
                } else writePayload(value, output);
            }
        } else {
            tag.write(output);
        }
    }
}
