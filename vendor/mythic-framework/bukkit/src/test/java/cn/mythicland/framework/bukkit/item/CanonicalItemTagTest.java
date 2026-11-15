package cn.mythicland.framework.bukkit.item;

import cn.mythicland.framework.bukkit.item.v1_8_R3.NmsItemStackAccess;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalItemTagTest {
    private static CompoundTag sample(boolean reverse) {
        CompoundTag extra = new CompoundTag();
        // These names share a Java hash and expose insertion-dependent map ordering.
        if (reverse) { extra.putInt("BB", 2); extra.putInt("Aa", 1); }
        else { extra.putInt("Aa", 1); extra.putInt("BB", 2); }
        ListTag records = new ListTag(); records.add(extra);
        CompoundTag item = new CompoundTag(); item.put("records", records);
        item.putByteArray("bytes", new byte[] { -1, 0, 127 });
        item.putString("name", "test\u0000\u4e2d");
        return item;
    }

    private static byte[] encode(CompoundTag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NmsItemStackAccess.write(tag, new DataOutputStream(bytes), true);
        return bytes.toByteArray();
    }

    @Test void canonicalEncodingSortsNestedCompoundsAndPreservesNbt() throws IOException {
        byte[] first = encode(sample(false)), second = encode(sample(true));
        assertArrayEquals(first, second);
        CompoundTag decoded = NbtIo.read(new DataInputStream(new ByteArrayInputStream(first)), NbtAccounter.unlimitedHeap());
        assertEquals(sample(false), decoded);
    }

    @Test void mixedListsAndReservedWrapperKeysRoundTrip() throws IOException {
        CompoundTag reserved = new CompoundTag(); reserved.putString("", "reserved");
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(42)); list.add(StringTag.valueOf("text")); list.add(reserved);
        list.add(sample(true));
        CompoundTag root = new CompoundTag(); root.put("mixed", list);
        byte[] bytes = encode(root);
        assertEquals(root, NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)), NbtAccounter.unlimitedHeap()));
        list.set(3, sample(false));
        assertArrayEquals(bytes, encode(root));
    }
}
