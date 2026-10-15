package net.mizukilab.pit.enchantment;

import cn.charlotte.pit.data.PlayerProfile;
import net.mizukilab.pit.enchantment.param.item.ArmorOnly;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EnchantmentRegistryMutationTest {
    @ArmorOnly public static class Entry extends UnknownEnchantment {
        public Entry() { super("replacement"); }
    }

    @Test
    public void equalByNameReplacementInvalidatesButRepeatedMaintenancePutIsANoOp() {
        EnchantmentFactor factor = new EnchantmentFactor();
        Map<String, AbstractEnchantment> registry = factor.getEnchantmentMap();
        Entry first = new Entry(), second = new Entry();
        registry.put("replacement", first);
        long revision = factor.getRevision();
        for (int i = 0; i < 1000; i++) registry.put("replacement", first);
        assertEquals(revision, factor.getRevision());
        assertEquals(first, second);
        registry.put("replacement", second);
        assertEquals(revision + 1, factor.getRevision());
        assertSame(second, factor.getEnchByClass(Entry.class));
    }

    @Test
    public void legacyMapViewsAndDefaultMethodsAlsoInvalidate() {
        EnchantmentFactor factor = new EnchantmentFactor();
        Map<String, AbstractEnchantment> registry = factor.getEnchantmentMap();
        Entry first = new Entry(), second = new Entry();
        registry.put("replacement", first);
        long revision = factor.getRevision();
        registry.entrySet().iterator().next().setValue(second);
        assertEquals(++revision, factor.getRevision());
        registry.compute("replacement", (key, current) -> first);
        assertEquals(++revision, factor.getRevision());
        registry.replaceAll((key, current) -> second);
        assertEquals(++revision, factor.getRevision());
        registry.values().remove(second);
        assertEquals(++revision, factor.getRevision());
        assertNull(factor.getEnchByClass(Entry.class));
        registry.putAll(Map.of("replacement", first, "alias", first));
        revision += 2;
        registry.keySet().remove("alias");
        assertEquals(++revision, factor.getRevision());
        var iterator = factor.getEnchantments().iterator();
        iterator.next();
        iterator.remove();
        assertEquals(++revision, factor.getRevision());
        registry.clear();
        assertEquals(revision, factor.getRevision());
    }

    @Test
    public void directReplacementRebuildsTheEnchantPoolOnceAndStableReadsReuseIt() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            EnchantmentFactor factor = new EnchantmentFactor();
            Entry first = new Entry(), second = new Entry();
            factor.getEnchantmentMap().put("replacement", first);
            EnchantmentTable table = spy(new EnchantmentTable(factor));
            factor.getEnchantmentMap().put("replacement", second);
            Player player = mock(Player.class);
            PlayerProfile profile = mock(PlayerProfile.class);
            for (int i = 0; i < 10; i++) {
                factor.getEnchantmentMap().put("replacement", second);
                table.enchantItem(player, profile, null, false);
            }
            verify(table, times(1)).reload();
            assertSame(second, table.armorEnchant.get(second.getRarity()).get(0));
            assertEquals(1, table.armorEnchant.get(second.getRarity()).size());
        }
    }
}
