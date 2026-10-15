package net.mizukilab.pit.enchantment;

import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagString;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class LegacyRecordTest {
    @Test
    public void repeatedReadsKeepSourceAndDoNotDuplicateHistory() throws Exception {
        IMythicItem item = new MythicSwordItem();
        NBTTagCompound extra = new NBTTagCompound();
        String records = "Tester|enchanted|123;Legacy|imported|bad-time";
        extra.setString("records", records);
        Method read = IMythicItem.class.getDeclaredMethod("readRecords", NBTTagCompound.class);
        read.setAccessible(true);
        read.invoke(item, extra);
        read.invoke(item, extra);
        assertEquals(2, item.getEnchantmentRecords().size());
        assertTrue(extra.get("records") instanceof NBTTagString);
        assertEquals(records, extra.getString("records"));
    }
}
