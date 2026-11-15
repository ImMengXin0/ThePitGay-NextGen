package cn.mythicland.framework.bukkit.item.v1_8_R3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Version 2 writes complete Paper item blobs. Version 1 is converted by Mojang's DataFixer. */
public final class InventoryItemCodec {
    private static final int MAGIC = 0x50495449;
    private static final int VERSION = 2;
    private static final int MAX_ITEM_BYTES = 8 * 1024 * 1024;
    private InventoryItemCodec() {}
    public static ItemStack copy(ItemStack item) { return item == null ? null : item.clone(); }
    public static String encode(ItemStack[] items) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream header = new DataOutputStream(Base64.getEncoder().wrap(bytes))) {
            header.writeInt(MAGIC);
            header.writeByte(VERSION);
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(header)))) {
                output.writeInt(items.length);
                for (ItemStack item : items) {
                    output.writeBoolean(item != null && !item.getType().isAir());
                    if (item == null || item.getType().isAir()) continue;
                    byte[] blob = item.serializeAsBytes();
                    if (blob.length > MAX_ITEM_BYTES) throw new IOException("Item payload exceeds limit");
                    output.writeInt(blob.length);
                    output.write(blob);
                }
            }
        }
        return bytes.toString(StandardCharsets.US_ASCII);
    }
    public static final class Reader implements Closeable {
        private final DataInputStream input;
        private final BukkitObjectInputStream legacy;
        private final int length, version;
        private int read;
        private long weight;
        public Reader(byte[] raw) throws IOException {
            ByteArrayInputStream bytes = new ByteArrayInputStream(raw);
            if (raw.length >= 2 && (raw[0] & 255) == 0xac && (raw[1] & 255) == 0xed) {
                legacy = new BukkitObjectInputStream(bytes);
                legacy.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                        "maxdepth=64;maxrefs=1048576;maxarray=1048576;maxbytes=67108864"));
                input = null;
                version = 0;
                length = legacy.readInt();
            } else {
                DataInputStream header = new DataInputStream(bytes);
                if (header.readInt() != MAGIC) throw new IOException("Invalid inventory header");
                version = header.readUnsignedByte();
                if (version != 1 && version != VERSION) throw new IOException("Unsupported inventory version: " + version);
                input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(bytes)));
                legacy = null;
                length = input.readInt();
            }
            if (length < 0 || length > 1_048_576) throw new IOException("Invalid inventory length");
            weight = 16L + 8L * length;
        }
        public int length() { return length; }
        public int weight() { return (int) Math.min(Integer.MAX_VALUE, weight); }
        public ItemStack read() throws IOException, ClassNotFoundException {
            if (read++ >= length) throw new EOFException("Inventory already exhausted");
            if (legacy != null) {
                Object value = legacy.readObject();
                if (value != null && !(value instanceof ItemStack)) throw new IOException("Invalid legacy inventory entry");
                ItemStack item = (ItemStack) value;
                if (item != null) weight += 128L + item.serializeAsBytes().length * 2L;
                return item;
            }
            if (!input.readBoolean()) return null;
            byte[] blob;
            if (version == VERSION) {
                int size = input.readInt();
                if (size < 1 || size > MAX_ITEM_BYTES) throw new IOException("Invalid item size");
                blob = input.readNBytes(size);
                if (blob.length != size) throw new EOFException("Truncated item");
            } else {
                int type = input.readInt();
                int amount = input.readInt();
                short damage = input.readShort();
                CompoundTag tag = input.readBoolean() ? NbtIo.read(input, NbtAccounter.create(MAX_ITEM_BYTES)) : null;
                if (type < 0 || type > Short.MAX_VALUE || (type != 0 && (amount < 1 || amount > 127)))
                    throw new IOException("Invalid legacy item fields");
                if (type == 0) return null;
                CompoundTag old = new CompoundTag();
                old.putShort("id", (short) type);
                old.putByte("Count", (byte) amount);
                old.putShort("Damage", damage);
                old.putInt("DataVersion", 0);
                if (tag != null) old.put("tag", tag);
                ByteArrayOutputStream encoded = new ByteArrayOutputStream();
                NbtIo.writeCompressed(old, encoded);
                blob = encoded.toByteArray();
            }
            weight += 128L + blob.length * 2L;
            try {
                ItemStack item = ItemStack.deserializeBytes(blob);
                if (item == null || item.getType().isAir()) throw new IOException("Saved inventory item decoded as air");
                return item;
            }
            catch (RuntimeException failure) { throw new IOException("Cannot migrate inventory item", failure); }
        }
        public void finish() throws IOException {
            if (read != length) throw new IOException("Inventory was not completely read");
            if (input != null && input.read() != -1) throw new IOException("Trailing inventory payload");
            close();
        }
        @Override public void close() throws IOException { if (legacy != null) legacy.close(); else input.close(); }
    }
}
