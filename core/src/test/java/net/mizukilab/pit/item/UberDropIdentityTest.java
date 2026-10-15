package net.mizukilab.pit.item;

import net.mizukilab.pit.item.type.UberDrop;
import net.mizukilab.pit.item.type.UberPlusDrop;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UberDropIdentityTest {
    @Test
    public void routesOrdinaryNewPlusAndLegacyPlusSeparately() {
        assertNotEquals(new UberDrop().getInternalName(), new UberPlusDrop().getInternalName());
        try (MockedStatic<ItemUtil> items = mockStatic(ItemUtil.class)) {
            ItemStack item = mock(ItemStack.class);
            ItemMeta meta = mock(ItemMeta.class);
            when(item.getItemMeta()).thenReturn(meta);
            items.when(() -> ItemUtil.getInternalName(item)).thenReturn("uber_drop");
            assertFalse(UberPlusDrop.isPlusDrop(item));
            when(item.containsEnchantment(Enchantment.THORNS)).thenReturn(true);
            assertFalse(UberPlusDrop.isPlusDrop(item));
            when(meta.getDisplayName()).thenReturn("§c不朽登峰造极掉落物");
            assertTrue(UberPlusDrop.isPlusDrop(item));
            items.when(() -> ItemUtil.getInternalName(item)).thenReturn("uber_plus_drop");
            when(item.containsEnchantment(Enchantment.THORNS)).thenReturn(false);
            assertTrue(UberPlusDrop.isPlusDrop(item));
        }
    }
}
