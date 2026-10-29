package cn.mythicland.framework.bukkit.item.v1_8_R3;

import net.minecraft.server.v1_8_R3.NBTBase;
import net.minecraft.server.v1_8_R3.NBTTagByteArray;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagIntArray;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;

public final class NmsItemStackAccess {
    private static final Field CRAFT_HANDLE = craftHandle();

    private NmsItemStackAccess() {
    }

    private static Field craftHandle() {
        try {
            Field field = CraftItemStack.class.getDeclaredField("handle");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public static net.minecraft.server.v1_8_R3.ItemStack read(ItemStack item) {
        if (!(item instanceof CraftItemStack)) return CraftItemStack.asNMSCopy(item);
        try {
            return (net.minecraft.server.v1_8_R3.ItemStack) CRAFT_HANDLE.get(item);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read item state", exception);
        }
    }

    public static boolean matches(ItemStack first, ItemStack second) {
        if (first == second) return true;
        if (first instanceof CraftItemStack && second instanceof CraftItemStack)
            return net.minecraft.server.v1_8_R3.ItemStack.matches(read(first), read(second));
        return Objects.equals(first, second);
    }

    public static void write(NBTTagCompound tag, DataOutput output, boolean canonical) throws IOException {
        output.writeByte(tag.getTypeId());
        output.writeUTF("");
        writeValue(tag, output, canonical);
    }

    private static void writeValue(NBTBase tag, DataOutput output, boolean canonical) throws IOException {
        switch (tag.getTypeId()) {
            case 0 -> {}
            case 1 -> output.writeByte(((NBTBase.NBTNumber) tag).f());
            case 2 -> output.writeShort(((NBTBase.NBTNumber) tag).e());
            case 3 -> output.writeInt(((NBTBase.NBTNumber) tag).d());
            case 4 -> output.writeLong(((NBTBase.NBTNumber) tag).c());
            case 5 -> output.writeFloat(((NBTBase.NBTNumber) tag).h());
            case 6 -> output.writeDouble(((NBTBase.NBTNumber) tag).g());
            case 7 -> {
                byte[] values = ((NBTTagByteArray) tag).c();
                output.writeInt(values.length);
                output.write(values);
            }
            case 8 -> output.writeUTF(((NBTTagString) tag).a_());
            case 9 -> {
                NBTTagList values = (NBTTagList) tag;
                if (values.f() == 0 && values.size() != 0)
                    throw new IOException("Nonempty NBT list has no element type");
                output.writeByte(values.f());
                output.writeInt(values.size());
                for (int index = 0; index < values.size(); index++) writeValue(values.g(index), output, canonical);
            }
            case 10 -> {
                NBTTagCompound values = (NBTTagCompound) tag;
                Iterable<String> keys = values.c();
                if (canonical) {
                    String[] sorted = values.c().toArray(String[]::new);
                    Arrays.sort(sorted);
                    keys = Arrays.asList(sorted);
                }
                for (String key : keys) {
                    NBTBase value = values.get(key);
                    if (value.getTypeId() == 0) throw new IOException("Named NBT end tag");
                    output.writeByte(value.getTypeId());
                    output.writeUTF(key);
                    writeValue(value, output, canonical);
                }
                output.writeByte(0);
            }
            case 11 -> {
                int[] values = ((NBTTagIntArray) tag).c();
                output.writeInt(values.length);
                for (int value : values) output.writeInt(value);
            }
            default -> throw new IOException("Unknown NBT type: " + tag.getTypeId());
        }
    }
}
