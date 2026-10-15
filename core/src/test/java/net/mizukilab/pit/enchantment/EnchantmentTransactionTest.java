package net.mizukilab.pit.enchantment;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import net.mizukilab.pit.data.operator.ProfileOperator;
import net.mizukilab.pit.enchantment.info.EnchantRequest;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.item.MythicColor;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EnchantmentTransactionTest extends net.mizukilab.pit.BukkitDataTestSupport {
    @Test
    public void failureRollsBackSerializedItemAndBookAndUnlocksProfile() throws Exception {
        runTransaction(true);
    }

    @Test
    public void successCommitsOnlyTheIndependentWorkingItem() throws Exception {
        runTransaction(false);
    }

    private void runTransaction(boolean fail) throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Utils> utils = mockStatic(Utils.class);
             MockedStatic<InventoryUtil> inventory = mockStatic(InventoryUtil.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("enchant-test"));
            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            when(player.getName()).thenReturn("Tester");
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            PlayerProfile profile = new PlayerProfile(player.getUniqueId(), "Tester");
            Field loaded = PlayerProfile.class.getDeclaredField("loaded");
            loaded.setAccessible(true);
            loaded.set(profile, true);
            profile.setEnchantingItem("original");
            profile.setEnchantingBook("book");
            ItemStack stack = mock(ItemStack.class);
            when(stack.clone()).thenReturn(stack);
            IMythicItem original = mock(IMythicItem.class);
            when(original.toItemStack()).thenReturn(stack);
            MythicSwordItem working = spy(new MythicSwordItem());
            working.color = MythicColor.RED;
            doReturn(stack).when(working).toItemStack();
            utils.when(() -> Utils.getMythicItem0(stack)).thenReturn(working);
            inventory.when(() -> InventoryUtil.serializeItemStack(stack)).thenReturn("committed");

            EnchantmentTable table = new EnchantmentTable(new EnchantmentFactor());
            EnchantmentTable.Tier tier = EnchantmentTable.Tier.TIER_1;
            var previousNormal = tier.normal;
            var previousPost = tier.post;
            try {
                tier.normal = (chance, pool, item) -> {
                    item.getEnchantments().put(new UnknownEnchantment("test"), 1);
                    if (fail) {
                        profile.setEnchantingBook(null);
                        throw new IllegalStateException("expected generation failure");
                    }
                    return false;
                };
                tier.post = item -> item.maxLive = 10;
                AtomicReference<EnchantRequest> result = new AtomicReference<>();
                table.enchantItem(player, profile, original, false).promise(result::set);
                assertNotNull(result.get());
                assertEquals(fail, result.get().isFail());
                assertEquals(ProfileOperator.OPCODE_FREE, profile.code);
                assertEquals("book", profile.getEnchantingBook());
                assertEquals(fail ? "original" : "committed", profile.getEnchantingItem());
                if (!fail) {
                    assertSame(working, result.get().getMythic());
                    assertFalse(result.get().isUseBook());
                    assertEquals(1, working.getTier());
                    assertEquals(10, working.getLive());
                }
            } finally {
                tier.normal = previousNormal;
                tier.post = previousPost;
            }
        }
    }

    @Test
    public void busyRequestFailsWithoutUnlockingAnotherOperation() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            PlayerProfile profile = mock(PlayerProfile.class);
            when(profile.isLoaded()).thenReturn(true);
            when(profile.disallow()).thenReturn(PlayerProfile.NONE_PROFILE);
            AtomicReference<EnchantRequest> result = new AtomicReference<>();
            new EnchantmentTable(new EnchantmentFactor()).enchantItem(player, profile, mock(IMythicItem.class), false).promise(result::set);
            assertTrue(result.get().isFail());
            verify(profile, never()).allow();
        }
    }
}
