package net.mizukilab.pit.listener;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerWarehouse;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Abilities;
import net.mizukilab.pit.PaperTestBootstrap;
import net.mizukilab.pit.util.item.ItemBuilder;
import org.bukkit.*;
import org.bukkit.craftbukkit.inventory.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.*;
import org.mockito.MockedStatic;

import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WarehouseStorageTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<PlayerProfile> profiles;
    private PlayerWarehouse storage;
    private CraftInventoryPlayer backpack;
    private Player player;
    private WarehouseListener listener;
    @Before public void setup() {
        bukkit = PaperTestBootstrap.mockBukkit();
        bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), anyString())).thenAnswer(call ->
                new CraftInventoryCustom((InventoryHolder) call.getArgument(0), (int) call.getArgument(1), (String) call.getArgument(2)));
        try (var warehouses = mockConstruction(PlayerWarehouse.class);
             var ender = mockConstruction(cn.charlotte.pit.data.sub.PlayerEnderChest.class)) {
            profiles = mockStatic(PlayerProfile.class);
        }
        storage = new PlayerWarehouse();
        var human = mock(ServerPlayer.class);
        when(human.getAbilities()).thenReturn(new Abilities());
        when(human.getOffhandItem()).thenReturn(net.minecraft.world.item.ItemStack.EMPTY);
        var inventory = new net.minecraft.world.entity.player.Inventory(human, new EntityEquipment());
        when(human.getInventory()).thenReturn(inventory);
        backpack = new CraftInventoryPlayer(inventory);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(backpack);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.isLoaded()).thenReturn(true);
        when(profile.getWarehouse()).thenReturn(storage);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
        listener = new WarehouseListener();
    }
    @After public void cleanup() {
        if (profiles != null) profiles.close();
        if (bukkit != null) bukkit.close();
    }
    private ItemStack item(int amount) {
        return new ItemBuilder(Material.GOLD_INGOT).internalName("stored_gold").canSaveToEnderChest(true).amount(amount).build();
    }
    private InventoryView view(Inventory top) {
        var view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(backpack);
        when(view.getPlayer()).thenReturn(player);
        when(view.getInventory(anyInt())).thenAnswer(call -> (int) call.getArgument(0) < top.getSize() ? top : backpack);
        when(view.convertSlot(anyInt())).thenAnswer(call -> {
            int slot = call.getArgument(0);
            return slot < top.getSize() ? slot : slot - top.getSize();
        });
        return view;
    }
    @Test public void emptyPanesAreNotSavedOrCountedAndOldPanesMigrate() {
        for (int i = 1; i <= 10; i++) assertEquals(0, storage.getItemCount(i));
        var box = storage.getWarehouse(1);
        box.setItem(0, new ItemBuilder(Material.WHITE_STAINED_GLASS_PANE).name("§7储存格").build());
        box.setItem(7, item(19));
        var restored = PlayerWarehouse.deserialization(storage.serialize());
        assertEquals(1, restored.getItemCount(1));
        assertEquals(19, restored.getWarehouse(1).getItem(7).getAmount());
        assertTrue(PlayerWarehouse.isBarrierPlaceholder(restored.getWarehouse(1).getItem(0)));
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, restored.getWarehouse(1).getItem(0).getType());
        assertEquals(storage.serialize(), restored.serialize());
    }
    @Test public void leftAndShiftClicksDepositAndWithdrawExactlyOnce() {
        backpack.setItem(0, item(37));
        var top = storage.getWarehouse(1);
        var view = view(top);
        var deposit = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 45,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        listener.onInventoryClick(deposit);
        assertTrue(deposit.isCancelled());
        assertTrue(backpack.getItem(0) == null || backpack.getItem(0).getType().isAir());
        assertEquals(37, top.getItem(0).getAmount());
        var withdraw = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(withdraw);
        assertEquals(37, backpack.getItem(0).getAmount());
        assertTrue(PlayerWarehouse.isBarrierPlaceholder(top.getItem(0)));
    }
    @Test public void partialTransferAcrossMultipleStacksNeverLosesRemainder() {
        var top = storage.getWarehouse(1);
        for (int i = 0; i < 45; i++) top.setItem(i, new ItemStack(Material.STONE, 64));
        top.setItem(3, item(60)); top.setItem(9, item(59)); backpack.setItem(0, item(20));
        assertEquals(9, WarehouseListener.transfer(backpack, 0, top, true));
        assertEquals(11, backpack.getItem(0).getAmount());
        assertEquals(64, top.getItem(3).getAmount()); assertEquals(64, top.getItem(9).getAmount());
        assertEquals(0, WarehouseListener.transfer(backpack, 0, top, true));
        assertEquals(11, backpack.getItem(0).getAmount());
    }
    @Test public void fullBackpackOnlyAcceptsAvailableStackCapacityAndNeverUsesArmorOrOffhand() {
        for (int i = 0; i < 36; i++) backpack.setItem(i, new ItemStack(Material.STONE, 64));
        backpack.setItem(12, item(62));
        var top = storage.getWarehouse(1); top.setItem(5, item(10));
        assertEquals(2, WarehouseListener.transfer(top, 5, backpack, false));
        assertEquals(8, top.getItem(5).getAmount());
        assertEquals(64, backpack.getItem(12).getAmount());
        assertTrue(backpack.getItemInOffHand().getType().isAir());
        assertEquals(0, WarehouseListener.transfer(top, 5, backpack, false));
    }
    @Test public void foreignContainersSpoofedTitlesAndDangerousClicksCannotTransfer() {
        backpack.setItem(0, item(14));
        var foreign = new PlayerWarehouse().getWarehouse(1);
        var unauthorized = new InventoryClickEvent(view(foreign), InventoryType.SlotType.CONTAINER, 45,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(unauthorized);
        assertTrue(unauthorized.isCancelled());
        assertEquals(14, backpack.getItem(0).getAmount());
        var spoof = new CraftInventoryCustom(null, 45, "寄存箱 #1");
        var unrelated = new InventoryClickEvent(view(spoof), InventoryType.SlotType.CONTAINER, 45,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(unrelated);
        assertFalse(unrelated.isCancelled());
        for (var type : new ClickType[]{ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.SWAP_OFFHAND, ClickType.DROP}) {
            var event = new InventoryClickEvent(view(storage.getWarehouse(1)), InventoryType.SlotType.CONTAINER,
                    45, type, InventoryAction.NOTHING);
            listener.onInventoryClick(event);
            assertTrue(event.isCancelled()); assertEquals(14, backpack.getItem(0).getAmount());
        }
    }
    @Test public void snapshotRollbackDoesNotShareMutableItemStacks() {
        var top = storage.getWarehouse(1); top.setItem(2, item(22));
        storage.snapshot(); top.getItem(2).setAmount(4); storage.rollback();
        assertEquals(22, top.getItem(2).getAmount());
        top.getItem(2).setAmount(7); storage.rollback();
        assertEquals(22, top.getItem(2).getAmount());
    }
    @Test public void cancelledClicksAndForbiddenItemsCannotBeStored() {
        var top = storage.getWarehouse(1);
        backpack.setItem(0, item(18));
        var cancelled = new InventoryClickEvent(view(top), InventoryType.SlotType.CONTAINER, 45,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        cancelled.setCancelled(true);
        listener.onInventoryClick(cancelled);
        assertEquals(18, backpack.getItem(0).getAmount()); assertEquals(0, storage.getItemCount(1));
        backpack.setItem(0, new ItemBuilder(Material.IRON_SWORD).defaultItem().canSaveToEnderChest(false).build());
        listener.onInventoryClick(new InventoryClickEvent(view(top), InventoryType.SlotType.CONTAINER, 45,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(Material.IRON_SWORD, backpack.getItem(0).getType()); assertEquals(0, storage.getItemCount(1));
    }
}
