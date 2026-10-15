package net.mizukilab.pit.item;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.UnknownEnchantment;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AddonItemCacheTest {
    @Test
    public void changingEnchantRegistryInvalidatesParsedItemsAndStableReadsKeepCache() {
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Utils> utils = mockStatic(Utils.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            EnchantmentFactor factor = new EnchantmentFactor();
            when(plugin.getEnchantmentFactor()).thenReturn(factor);
            UnknownEnchantment firstEnchant = new UnknownEnchantment("same_name");
            factor.registerEnchantment(firstEnchant);
            ItemFactory factory = new ItemFactory();
            ItemStack stack = mock(ItemStack.class);
            IMythicItem first = mock(IMythicItem.class), replacement = mock(IMythicItem.class);
            first.uuid = replacement.uuid = UUID.randomUUID();
            utils.when(() -> Utils.getMythicItem0(stack, "mythic_bow")).thenReturn(first, replacement);
            assertSame(first, factory.getIMythicItem0(stack, "mythic_bow", true));
            assertSame(first, factory.getItem(first.uuid));
            assertTrue(factory.hasItem(first.uuid));
            factor.registerEnchantment(new UnknownEnchantment("same_name"));
            assertNull(factory.getItem(first.uuid));
            assertFalse(factory.hasItem(first.uuid));
            assertSame(replacement, factory.getIMythicItem0(stack, "mythic_bow", true));
            assertSame(replacement, factory.getItem(first.uuid));
            factor.removeEnchantment(firstEnchant); // delayed removal of the old object
            assertSame(replacement, factory.getItem(first.uuid));
        }
    }

    @Test
    public void clearCachePreservesOtherItemsAndRejectsUuidHashCollision() {
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Utils> utils = mockStatic(Utils.class);
             MockedStatic<ItemUtil> nbt = mockStatic(ItemUtil.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            ItemFactory factory = new ItemFactory();
            when(plugin.getItemFactory()).thenReturn(factory);
            ItemStack firstStack = mock(ItemStack.class), otherStack = mock(ItemStack.class), collidingStack = mock(ItemStack.class);
            IMythicItem first = mock(IMythicItem.class), other = mock(IMythicItem.class);
            first.uuid = new UUID(0, 101);
            other.uuid = new UUID(0, 102);
            UUID collision = new UUID(101, 0);
            assertEquals(first.uuid.hashCode(), collision.hashCode());
            nbt.when(() -> ItemUtil.getUUIDObj(firstStack)).thenReturn(first.uuid);
            nbt.when(() -> ItemUtil.getUUIDObj(collidingStack)).thenReturn(collision);
            utils.when(() -> Utils.getMythicItem0(firstStack, "mythic_bow")).thenReturn(first);
            utils.when(() -> Utils.getMythicItem0(otherStack, "mythic_bow")).thenReturn(other);
            factory.getIMythicItem0(firstStack, "mythic_bow", true);
            factory.getIMythicItem0(otherStack, "mythic_bow", true);
            IMythicItem.clearCache(collidingStack);
            assertSame(first, factory.getItem(first.uuid));
            IMythicItem.clearCache(firstStack);
            assertNull(factory.getItem(first.uuid));
            assertSame(other, factory.getItem(other.uuid));
            nbt.verify(() -> ItemUtil.setUUID(any(ItemStack.class), anyString()), never());
        }
    }
}
