package cn.mythicland.framework.bukkit.item.v1_8_R3;

import cn.mythicland.framework.bukkit.item.v1_8_R3.NmsItemStackAccess;
import net.minecraft.server.v1_8_R3.Item;
import net.minecraft.server.v1_8_R3.NBTCompressedStreamTools;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTBase;
import net.minecraft.server.v1_8_R3.NBTTagByteArray;
import net.minecraft.server.v1_8_R3.NBTTagIntArray;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class InventoryItemCodec {
    private static final int MAGIC = 0x50495449;
    private static final int VERSION = 1;

    private InventoryItemCodec() {
    }

    public static ItemStack copy(ItemStack item) {
        net.minecraft.server.v1_8_R3.ItemStack handle = CraftItemStack.asNMSCopy(item);
        if (handle.hasTag()) handle.setTag((NBTTagCompound) handle.getTag().clone());
        return CraftItemStack.asCraftMirror(handle);
    }

    public static String encode(ItemStack[] items) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream header = new DataOutputStream(Base64.getEncoder().wrap(bytes))) {
            header.writeInt(MAGIC);
            header.writeByte(VERSION);
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(new GZIPOutputStream(header)))) {
                output.writeInt(items.length);
                for (ItemStack item : items) {
                    output.writeBoolean(item != null);
                    if (item == null) continue;
                    output.writeInt(item.getTypeId());
                    output.writeInt(item.getAmount());
                    output.writeShort(item.getDurability());
                    net.minecraft.server.v1_8_R3.ItemStack handle = NmsItemStackAccess.read(item);
                    NBTTagCompound tag = handle == null ? null : handle.getTag();
                    output.writeBoolean(tag != null);
                    if (tag != null) NmsItemStackAccess.write(tag, output, false);
                }
            }
        }
        return bytes.toString(StandardCharsets.US_ASCII);
    }

    public static final class Reader implements Closeable {
        private final DataInputStream input;
        private final BukkitObjectInputStream legacy;
        private final int length;
        private long weight;

        public Reader(byte[] raw) throws IOException {
            ByteArrayInputStream bytes = new ByteArrayInputStream(raw);
            if (raw.length >= 2 && (raw[0] & 255) == 0xac && (raw[1] & 255) == 0xed) {
                this.legacy = new BukkitObjectInputStream(bytes);
                this.input = null;
                this.length = this.legacy.readInt();
            } else {
                DataInputStream header = new DataInputStream(bytes);
                if (header.readInt() != MAGIC || header.readUnsignedByte() != VERSION)
                    throw new IOException("Unsupported inventory payload");
                this.input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(bytes)));
                this.legacy = null;
                this.length = this.input.readInt();
            }
            if (this.length < 0 || this.length > 1_048_576) throw new IOException("Invalid inventory length");
            this.weight = 16L + 8L * this.length;
        }

        public int length() {
            return this.length;
        }

        public int weight() {
            return (int) Math.min(Integer.MAX_VALUE, this.weight);
        }

        public ItemStack read() throws IOException, ClassNotFoundException {
            if (this.legacy != null) {
                ItemStack item = (ItemStack) this.legacy.readObject();
                if (item != null) {
                    net.minecraft.server.v1_8_R3.ItemStack handle = NmsItemStackAccess.read(item);
                    this.weight += 128L + tagWeight(handle == null ? null : handle.getTag());
                }
                return item;
            }
            if (!this.input.readBoolean()) return null;
            int type = this.input.readInt();
            int amount = this.input.readInt();
            short durability = this.input.readShort();
            NBTTagCompound tag = this.input.readBoolean() ? NBTCompressedStreamTools.a(this.input) : null;
            this.weight += 128L + tagWeight(tag);
            Material material = Material.getMaterial(type);
            if (material == null) throw new IOException("Unknown inventory item type: " + type);
            if (material == Material.AIR) return new ItemStack(material, amount, durability);
            Item item = Item.getById(type);
            if (item == null) throw new IOException("Unknown inventory item type: " + type);
            net.minecraft.server.v1_8_R3.ItemStack handle =
                    new net.minecraft.server.v1_8_R3.ItemStack(item, amount, durability);
            handle.setTag(tag);
            return CraftItemStack.asCraftMirror(handle);
        }

        public void finish() throws IOException {
            if (this.input != null && this.input.read() != -1) throw new IOException("Trailing inventory payload");
            this.close();
        }

        @Override
        public void close() throws IOException {
            if (this.legacy != null) this.legacy.close();
            else this.input.close();
        }
    }

    private static long tagWeight(NBTBase tag) {
        if (tag == null) return 0L;
        if (tag instanceof NBTTagByteArray bytes) return 32L + bytes.c().length;
        if (tag instanceof NBTTagIntArray values) return 32L + 4L * values.c().length;
        if (tag instanceof NBTTagString value) return 64L + 2L * value.a_().length();
        if (tag instanceof NBTTagList values) {
            long weight = 48L + 8L * values.size();
            for (int index = 0; index < values.size(); index++) weight += tagWeight(values.g(index));
            return weight;
        }
        if (tag instanceof NBTTagCompound values) {
            long weight = 64L;
            for (String key : values.c()) weight += 96L + 2L * key.length() + tagWeight(values.get(key));
            return weight;
        }
        return 24L;
    }
}
