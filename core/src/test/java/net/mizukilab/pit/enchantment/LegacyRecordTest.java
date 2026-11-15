package net.mizukilab.pit.enchantment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class LegacyRecordTest {
    @Test
    public void repeatedReadsKeepSourceAndDoNotDuplicateHistory() throws Exception {
        IMythicItem item = new MythicSwordItem();
        CompoundTag extra = new CompoundTag();
        String records = "Tester|enchanted|123;Legacy|imported|bad-time";
        extra.putString("records", records);
        Method read = IMythicItem.class.getDeclaredMethod("readRecords", CompoundTag.class);
        read.setAccessible(true);
        read.invoke(item, extra);
        read.invoke(item, extra);
        assertEquals(2, item.getEnchantmentRecords().size());
        assertTrue(extra.get("records") instanceof StringTag);
        assertEquals(records, extra.getStringOr("records", ""));
    }
}
