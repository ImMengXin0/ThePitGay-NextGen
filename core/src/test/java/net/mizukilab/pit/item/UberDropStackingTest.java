package net.mizukilab.pit.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityEquipment;
import net.mizukilab.pit.PaperTestBootstrap;
import net.mizukilab.pit.item.type.UberDrop;
import net.mizukilab.pit.item.type.UberPlusDrop;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftInventoryCustom;
import org.bukkit.craftbukkit.inventory.CraftInventoryPlayer;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.*;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UberDropStackingTest {
    private MockedStatic<Bukkit> bukkit;
    @Before public void setup() { bukkit = PaperTestBootstrap.mockBukkit(); }
    @After public void cleanup() { if (bukkit != null) bukkit.close(); }

    @Test public void separatelyCreatedDropsStackWithinEachTierButNeverAcrossTiers() {
        var ordinary = new UberDrop().toItemStack();
        var plus = new UberPlusDrop().toItemStack();
        var container = new net.minecraft.world.entity.player.Inventory(mock(ServerPlayer.class), new EntityEquipment());
        var inventory = new CraftInventoryPlayer(container);
        assertTrue(inventory.addItem(ordinary, new UberDrop().toItemStack(), plus, new UberPlusDrop().toItemStack()).isEmpty());
        assertEquals(2, inventory.getItem(0).getAmount());
        assertEquals(2, inventory.getItem(1).getAmount());
        assertEquals("uber_drop", ItemUtil.getInternalName(inventory.getItem(0)));
        assertEquals("uber_plus_drop", ItemUtil.getInternalName(inventory.getItem(1)));
        assertFalse(ordinary.isSimilar(plus));
    }

    @Test public void oldSeparatorsAreRemovedWhenStoredContainerOpensWithoutMovingOrConsumingDrops() {
        var stored = new CraftInventoryCustom(null, 27, "storage");
        stored.setItem(3, new ItemBuilder(new UberDrop().toItemStack()).dontStack().amount(12).build());
        stored.setItem(8, new ItemBuilder(new UberDrop().toItemStack()).dontStack().amount(4).build());
        assertFalse(stored.getItem(3).isSimilar(stored.getItem(8)));
        var view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(stored);
        new UberDrop().onInventoryOpen(new InventoryOpenEvent(view));
        assertTrue(stored.getItem(3).isSimilar(stored.getItem(8)));
        assertEquals(12, stored.getItem(3).getAmount());
        assertEquals(4, stored.getItem(8).getAmount());
        assertTrue(stored.getItem(3).isSimilar(new UberDrop().toItemStack()));
    }

    @Test public void legacyPlusRetainsItsTierAndStacksWithTheCurrentPlusDrop() {
        var legacy = new ItemBuilder(new UberPlusDrop().toItemStack()).internalName("uber_drop").dontStack().amount(6).build();
        assertTrue(UberPlusDrop.isPlusDrop(legacy));
        var normalized = UberDropStacking.normalize(legacy);
        assertEquals(6, normalized.getAmount());
        assertTrue(normalized.isSimilar(new UberPlusDrop().toItemStack()));
        assertFalse(normalized.isSimilar(new UberDrop().toItemStack()));
        assertSame(normalized, UberDropStacking.normalize(normalized));
    }

    @Test public void normalizationRetainsOtherMetadataAndNeverEditsTheOriginal() {
        var original = new ItemBuilder(new UberDrop().toItemStack()).name("special reward")
                .changeNbt("custom_reward", 35).dontStack().amount(9).build();
        var before = ItemUtil.getExtra(original).copy();
        var normalized = UberDropStacking.normalize(original);
        assertEquals(before, ItemUtil.getExtra(original));
        before.remove("uuid");
        assertEquals(before, ItemUtil.getExtra(normalized));
        assertEquals(original.getItemMeta().getDisplayName(), normalized.getItemMeta().getDisplayName());
        assertEquals(9, normalized.getAmount());
    }

    @Test public void unrelatedItemsAndRealUuidIdentitiesAreUntouched() {
        var other = new ItemBuilder(Material.ENDER_CHEST).internalName("other_reward").dontStack().build();
        assertSame(other, UberDropStacking.normalize(other));
        var identified = new ItemBuilder(new UberDrop().toItemStack())
                .changeNbt("uuid", "0b6d59c4-e3f0-4927-8b21-a18a1dbd3dfa").build();
        assertSame(identified, UberDropStacking.normalize(identified));
        assertNull(UberDropStacking.normalize(null));
    }
}
