package net.mizukilab.pit.item;

import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RepairExtendItemApiTest {
    @Test
    public void legacyClientSideRebuildRefreshesCacheWithoutRewritingItemNbt() throws Throwable {
        try (MockedStatic<Utils> utils = mockStatic(Utils.class);
             MockedStatic<ItemUtil> nbt = mockStatic(ItemUtil.class)) {
            ItemFactory factory = new ItemFactory();
            ItemStack stack = mock(ItemStack.class);
            IMythicItem first = mock(IMythicItem.class), replacement = mock(IMythicItem.class);
            first.uuid = replacement.uuid = UUID.randomUUID();
            utils.when(() -> Utils.getMythicItem0(stack, "mythic_leggings")).thenReturn(first, replacement);
            var entry = MethodHandles.publicLookup().findVirtual(ItemFactory.class, "getIMythicItem0",
                    MethodType.methodType(IMythicItem.class, ItemStack.class, String.class, boolean.class));
            assertSame(first, (IMythicItem) entry.invokeExact(factory, stack, "mythic_leggings", true));
            assertSame(replacement, (IMythicItem) entry.invokeExact(factory, stack, "mythic_leggings", true));
            assertSame(replacement, factory.getItem(first.uuid));
            nbt.verifyNoInteractions();
        }
    }

    @Test
    public void legacyServerRebuildUsesVersionMigrationWithoutClampingOldEnchantments() {
        try (MockedStatic<Utils> utils = mockStatic(Utils.class);
             MockedStatic<ItemUtil> nbt = mockStatic(ItemUtil.class)) {
            ItemStack stack = mock(ItemStack.class);
            IMythicItem item = mock(IMythicItem.class);
            item.uuid = UUID.randomUUID();
            nbt.when(() -> ItemUtil.shouldUpdateItem(stack)).thenReturn(true);
            nbt.when(ItemUtil::shouldUpdateUUID).thenReturn(true);
            utils.when(() -> Utils.getMythicItem0(stack, "mythic_sword")).thenReturn(item);
            assertSame(item, new ItemFactory().getIMythicItem0(stack, "mythic_sword", false));
            nbt.verify(() -> ItemUtil.randomUUIDItem(stack));
            nbt.verify(() -> ItemUtil.signVer(stack));
            verifyNoInteractions(item);
        }
    }

    @Test
    public void playerArenaDescriptorUsesTheExistingEntityArenaRule() throws Throwable {
        Player player = mock(Player.class);
        try (MockedStatic<Utils> utils = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utils.when(() -> Utils.isInArena((Entity) player)).thenReturn(true);
            var entry = MethodHandles.publicLookup().findStatic(Utils.class, "isInArena",
                    MethodType.methodType(boolean.class, Player.class));
            assertTrue((boolean) entry.invokeExact(player));
            utils.when(() -> Utils.isInArena((Entity) player)).thenReturn(false);
            assertFalse((boolean) entry.invokeExact(player));
        }
    }
}
