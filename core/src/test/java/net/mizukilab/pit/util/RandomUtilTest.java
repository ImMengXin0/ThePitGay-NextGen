package net.mizukilab.pit.util;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.mizukilab.pit.util.random.RandomUtil;
import net.mizukilab.pit.util.exception.IllegalEnchantInputException;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class RandomUtilTest {
    @Test(timeout = 1000)
    public void exhaustedUniquePoolFailsInsteadOfSpinning() {
        Object2IntOpenHashMap<String> enchants = new Object2IntOpenHashMap<>();
        enchants.put("only", 3);
        assertThrows(IllegalEnchantInputException.class,
                () -> RandomUtil.chooseAndApply(true, List.of("only"), enchants, 1));
    }

    @Test(timeout = 1000)
    public void emptyPoolFailsExplicitly() {
        assertThrows(IllegalEnchantInputException.class,
                () -> RandomUtil.randEnch(0, List.of(), List.of()));
    }

    @Test(timeout = 1000)
    public void singleLevelEnchantIsEligibleAtZero() {
        Object2IntOpenHashMap<String> enchants = new Object2IntOpenHashMap<>();
        RandomUtil.randEnchMultipleApplyRNPreferStEEmp(2, 0, 1, 1, enchants,
                List.of("one-level"), List.of(), 0, (level, enchant) -> level < 1);
        assertEquals(1, enchants.getInt("one-level"));
    }

    @Test(timeout = 1000)
    public void cappedEnchantPoolFailsWithoutChangingLevels() {
        Object2IntOpenHashMap<String> enchants = new Object2IntOpenHashMap<>();
        enchants.put("capped", 3);
        assertThrows(IllegalEnchantInputException.class, () -> RandomUtil.randEnchMultipleApplyRNPreferStEEmp(
                2, 0, 1, 1, enchants, List.of("capped"), List.of(), 0, (level, enchant) -> level < 3));
        assertEquals(3, enchants.getInt("capped"));
    }
}
