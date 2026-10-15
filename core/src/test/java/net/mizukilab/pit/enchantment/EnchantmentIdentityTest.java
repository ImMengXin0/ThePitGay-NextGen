package net.mizukilab.pit.enchantment;

import org.junit.Test;
import java.util.HashMap;
import static org.junit.Assert.*;

public class EnchantmentIdentityTest {
    @Test
    public void sameNameCanRetrieveAnExistingHashEntry() {
        AbstractEnchantment registered = new UnknownEnchantment("test_enchant");
        AbstractEnchantment reloaded = new UnknownEnchantment("test_enchant");
        HashMap<AbstractEnchantment, Integer> map = new HashMap<>();
        map.put(registered, 3);
        assertEquals(registered, reloaded);
        assertEquals(registered.hashCode(), reloaded.hashCode());
        assertEquals(Integer.valueOf(3), map.get(reloaded));
    }
}
