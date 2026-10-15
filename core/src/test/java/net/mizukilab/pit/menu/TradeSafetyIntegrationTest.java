package net.mizukilab.pit.menu;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.TradeData;
import cn.charlotte.pit.data.sub.OfferData;
import cn.charlotte.pit.data.temp.TradeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minecraft.server.v1_8_R3.AttributeModifier;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.command.PitCommands;
import net.mizukilab.pit.menu.offer.OfferMenu;
import net.mizukilab.pit.menu.offer.button.OfferButton;
import net.mizukilab.pit.menu.trade.*;
import net.mizukilab.pit.menu.trade.button.*;
import net.mizukilab.pit.medal.impl.challenge.FirstTradeMedal;
import net.mizukilab.pit.trade.TradeInventory;
import net.mizukilab.pit.trade.TradeMonitorRunnable;
import net.mizukilab.pit.util.item.ItemUtil;
import net.mizukilab.pit.util.menu.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_8_R3.inventory.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.*;
import org.mockito.*;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real item serialization and inventory events; only server transport and external effects are mocked. */
public class TradeSafetyIntegrationTest extends BukkitDataTestSupport {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerProfile> profiles;
    private MockedStatic<ItemUtil> items;
    private MockedConstruction<TargetConfirmButton> labels;
    private MockedConstruction<TradeData> logs;
    private MockedConstruction<FirstTradeMedal> medals;
    private final ButtonListener listener = new ButtonListener();
    private final Map<UUID, Surface> players = new HashMap<>();
    private Surface a, b;
    private ThePit plugin;

    @BeforeClass public static void registerItems() {
        PrintStream out = System.out, err = System.err;
        try (var attributes = mockConstruction(AttributeModifier.class)) { DispenserRegistry.c(); }
        finally { System.setOut(out); System.setErr(err); }
    }
    @Before public void setup() {
        bukkit = mockStatic(Bukkit.class); pit = mockStatic(ThePit.class);
        profiles = mockStatic(PlayerProfile.class); items = mockStatic(ItemUtil.class);
        labels = mockConstruction(TargetConfirmButton.class);
        logs = mockConstruction(TradeData.class); medals = mockConstruction(FirstTradeMedal.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
                .thenAnswer(call -> new CraftInventoryCustom(call.getArgument(0), (int) call.getArgument(1), call.getArgument(2)));
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> {
            Surface surface = players.get(call.getArgument(0)); return surface == null ? null : surface.player;
        });
        plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(any(UUID.class))).thenAnswer(call -> {
            Surface surface = players.get(call.getArgument(0)); return surface == null ? PlayerProfile.NONE_PROFILE : surface.profile;
        });
        items.when(() -> ItemUtil.canTrade(any(ItemStack.class))).thenReturn(true);
        a = new Surface("Alice"); b = new Surface("Bob");
    }
    @After public void cleanup() {
        TradeManager.cancelAll();
        for (Surface surface : players.values()) {
            surface.player.closeInventory();
            Menu.currentlyOpenedMenus.remove(surface.player.getUniqueId());
        }
        TradeManager.trades.clear(); TradeMonitorRunnable.getTradeRequests().clear();
        medals.close(); logs.close(); labels.close(); items.close(); profiles.close(); pit.close(); bukkit.close();
    }
    private class Surface {
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final Inventory crafting = mock(Inventory.class);
        final PlayerProfile profile;
        ItemStack[] contents = new ItemStack[36];
        InventoryView view;
        Surface(String name) {
            UUID uuid = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(uuid); when(player.getName()).thenReturn(name);
            when(player.getDisplayName()).thenReturn("§a" + name); when(player.isOnline()).thenReturn(true);
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getSize()).thenReturn(36); when(inventory.getMaxStackSize()).thenReturn(64);
            when(inventory.getContents()).thenAnswer(call -> TradeInventory.copy(contents));
            when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
            when(inventory.getItem(anyInt())).thenAnswer(call -> contents[(int) call.getArgument(0)]);
            doAnswer(call -> { contents[(int) call.getArgument(0)] = call.getArgument(1); return null; })
                    .when(inventory).setItem(anyInt(), any());
            doAnswer(call -> { contents = TradeInventory.copy(call.getArgument(0)); return null; })
                    .when(inventory).setContents(any(ItemStack[].class));
            when(crafting.getType()).thenReturn(InventoryType.CRAFTING);
            view = viewOf(crafting);
            when(player.getOpenInventory()).thenAnswer(call -> view);
            doAnswer(call -> {
                InventoryView closing = view;
                view = viewOf(crafting);
                listener.onInventoryClose(new InventoryCloseEvent(closing)); return null;
            }).when(player).closeInventory();
            when(player.openInventory(any(Inventory.class))).thenAnswer(call -> {
                listener.onInventoryClose(new InventoryCloseEvent(view));
                CraftInventory inv = call.getArgument(0);
                view = viewOf(new CraftInventory(inv.getInventory())); return view;
            });
            profile = spy(new PlayerProfile(uuid, name)); profile.setLoaded(true); profile.setCoins(1000);
            doReturn(70).when(profile).getLevel();
            players.put(uuid, this);
        }
        InventoryView viewOf(Inventory top) {
            return new InventoryView() {
                @Override public Inventory getTopInventory() { return top; }
                @Override public Inventory getBottomInventory() { return inventory; }
                @Override public HumanEntity getPlayer() { return player; }
                @Override public InventoryType getType() { return top.getType(); }
            };
        }
        InventoryClickEvent click(int raw, ClickType type) {
            InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, raw, type, InventoryAction.PICKUP_ALL);
            listener.onButtonPress(event); return event;
        }
        int count(Material material) {
            return Arrays.stream(contents).filter(Objects::nonNull).filter(i -> i.getType() == material).mapToInt(ItemStack::getAmount).sum();
        }
        void full() { Arrays.setAll(contents, i -> new ItemStack(Material.COBBLESTONE, 64)); }
    }
    private TradeManager start() {
        TradeManager trade = TradeManager.start(a.player, b.player);
        assertNotNull(trade); assertFalse(trade.isEnd());
        assertSame(trade, TradeManager.trades.get(a.player.getUniqueId()));
        assertTrue(Menu.find(a.player, a.view.getTopInventory()) instanceof TradeMenu);
        return trade;
    }
    private void expire(TradeManager trade) { trade.getAPutCooldown().fastExpired(); trade.getBPutCooldown().fastExpired(); }
    private void finish(TradeManager trade) {
        expire(trade); a.click(21, ClickType.LEFT); b.click(21, ClickType.LEFT);
    }

    @Test public void commandAcceptUsesSameMenusConsumesRequestsAndCannotReplaceActiveTrade() {
        TradeMonitorRunnable.getTradeRequests().add(new TradeRequest(b.player, a.player));
        new PitCommands().onRequest(a.player, b.player);
        TradeManager trade = TradeManager.trades.get(a.player.getUniqueId());
        assertNotNull(trade); assertTrue(TradeMonitorRunnable.getTradeRequests().isEmpty());
        Menu first = Menu.find(a.player), second = Menu.find(b.player);
        a.contents[9] = new ItemStack(Material.DIAMOND, 3);
        assertTrue(a.click(27, ClickType.LEFT).isCancelled());
        assertEquals(0, a.count(Material.DIAMOND)); assertEquals(3, trade.getAItems().get(0).getAmount());
        assertSame(first, Menu.find(a.player)); assertSame(second, Menu.find(b.player));
        new PitCommands().onRequest(a.player, b.player);
        assertSame(trade, TradeManager.trades.get(a.player.getUniqueId()));
        assertNull(TradeManager.start(a.player, b.player));
        assertThrows(IllegalStateException.class, () -> new TradeManager(a.player, b.player));
        assertEquals(3, trade.getAItems().get(0).getAmount());
    }
    @Test public void cancelledTradeCannotBeRefundedAgainByStaleButtonsOrCallbacks() {
        TradeManager trade = start();
        a.contents[9] = new ItemStack(Material.DIAMOND, 5);
        trade.addItem(a.player, 9); trade.setCoins(a.player, 600);
        Button staleItem = Menu.find(a.player).getButtons().get(1);
        Button staleCoin = Menu.find(a.player).getButtons().get(0);
        long edit = trade.beginCoinEdit(a.player);
        b.player.closeInventory();
        assertTrue(trade.isEnd()); assertEquals(5, a.count(Material.DIAMOND)); assertEquals(1000, a.profile.getCoins(), 0);
        for (int i = 0; i < 10; i++) {
            staleItem.clicked(a.player, 1, ClickType.LEFT, -1, null);
            staleCoin.clicked(a.player, 0, ClickType.LEFT, -1, null);
            trade.finishCoinEdit(a.player, edit, new String[]{"600"});
            trade.onPlayerCancel(b.player); trade.setEnd(false);
        }
        assertTrue(trade.isEnd()); assertEquals(5, a.count(Material.DIAMOND)); assertEquals(1000, a.profile.getCoins(), 0);
        assertTrue(trade.getAItems().isEmpty()); assertEquals(0, trade.getACoins(), 0);
        TradeManager next = start();
        trade.finishCoinEdit(a.player, edit, new String[]{"500"});
        assertSame(next, TradeManager.trades.get(a.player.getUniqueId())); assertEquals(1000, a.profile.getCoins(), 0);
    }
    @Test public void doubleClickHotbarDropAndDragCannotMoveDisplayCopies() {
        TradeManager trade = start();
        a.contents[9] = new ItemStack(Material.DIAMOND, 5); trade.addItem(a.player, 9);
        b.contents[9] = new ItemStack(Material.EMERALD, 2);
        for (ClickType click : List.of(ClickType.DOUBLE_CLICK, ClickType.NUMBER_KEY, ClickType.DROP, ClickType.CONTROL_DROP, ClickType.MIDDLE)) {
            assertTrue(a.click(5, click).isCancelled()); assertTrue(b.click(27, click).isCancelled());
        }
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(a.player); when(drag.getView()).thenReturn(a.view);
        listener.onInventoryDrag(drag); verify(drag).setCancelled(true);
        assertEquals(2, b.count(Material.EMERALD)); assertEquals(5, trade.getAItems().get(0).getAmount());
        assertTrue(trade.getBItems().isEmpty());
        trade.getAItems().get(0).setAmount(64);
        assertEquals(5, trade.getAItems().get(0).getAmount());
        assertThrows(UnsupportedOperationException.class, () -> trade.getAItems().clear());
    }
    @Test public void bothPlayersCanUseAllEightSlotsAndCoinSpaceIsReserved() {
        TradeManager trade = start();
        for (int slot = 0; slot < 8; slot++) {
            a.contents[slot] = new ItemStack(Material.DIAMOND);
            b.contents[slot] = new ItemStack(Material.EMERALD);
            assertTrue(trade.addItem(a.player, slot)); assertTrue(trade.addItem(b.player, slot));
        }
        assertEquals(8, trade.getAItems().size()); assertEquals(8, trade.getBItems().size());
        assertFalse(trade.setCoins(a.player, 1)); assertFalse(trade.setCoins(b.player, 1));
        assertTrue(trade.removeItem(a.player, 0, trade.getRevision())); assertTrue(trade.setCoins(a.player, 1));
        a.contents[9] = new ItemStack(Material.GOLD_INGOT);
        assertFalse(trade.addItem(a.player, 9)); assertEquals(1, a.count(Material.GOLD_INGOT));
    }
    @Test public void successfulTradeConservesItemsCoinsAndSettlesOnlyOnce() {
        TradeManager trade = start();
        a.contents[9] = new ItemStack(Material.DIAMOND, 10); b.contents[9] = new ItemStack(Material.EMERALD, 6);
        trade.addItem(a.player, 9); trade.addItem(b.player, 9);
        assertTrue(trade.setCoins(a.player, 700));
        assertTrue(trade.setCoins(a.player, 800)); // Includes already held coins in available balance.
        assertTrue(trade.setCoins(b.player, 200));
        finish(trade);
        assertTrue(trade.isEnd()); assertTrue(TradeManager.trades.isEmpty());
        assertEquals(6, a.count(Material.EMERALD)); assertEquals(10, b.count(Material.DIAMOND));
        assertEquals(400, a.profile.getCoins(), 0); assertEquals(1600, b.profile.getCoins(), 0);
        assertEquals(1000, a.profile.getTradeLimit().getAmount(), 0);
        assertEquals(1, b.profile.getTradeLimit().getTimes());
        trade.onPlayerCancel(a.player); trade.setAConfirm(true); trade.setBConfirm(true);
        assertEquals(10, b.count(Material.DIAMOND)); assertEquals(2000, a.profile.getCoins() + b.profile.getCoins(), 0);
        assertEquals(1, logs.constructed().size()); verify(logs.constructed().get(0)).save();
    }
    @Test public void fullRecipientPreventsPartialSettlementThenCanRetry() {
        TradeManager trade = start();
        a.contents[9] = new ItemStack(Material.DIAMOND, 5); trade.addItem(a.player, 9);
        trade.setCoins(b.player, 100); b.full();
        finish(trade);
        assertFalse(trade.isEnd()); assertFalse(trade.isAConfirm()); assertFalse(trade.isBConfirm());
        assertEquals(0, b.count(Material.DIAMOND)); assertEquals(5, trade.getAItems().get(0).getAmount());
        assertEquals(1000, a.profile.getCoins(), 0); assertEquals(900, b.profile.getCoins(), 0);
        b.contents[0] = null; finish(trade);
        assertTrue(trade.isEnd()); assertEquals(5, b.count(Material.DIAMOND));
        assertEquals(1100, a.profile.getCoins(), 0);
    }
    @Test public void fullRefundIsPersistedAndClaimedOnceAfterSpaceIsAvailable() throws Exception {
        TradeManager trade = start();
        a.contents[9] = new ItemStack(Material.DIAMOND, 50); trade.addItem(a.player, 9);
        a.full(); a.contents[0] = new ItemStack(Material.DIAMOND, 60);
        trade.onPlayerCancel(a.player);
        assertEquals(60, a.count(Material.DIAMOND)); assertEquals(1, a.profile.getTradeReturns().size());
        ObjectMapper mapper = new ObjectMapper();
        String saved = mapper.writeValueAsString(a.profile.getTradeReturns());
        PlayerProfile restored = mapper.readValue("{\"tradeReturns\":" + saved + "}", PlayerProfile.class);
        a.profile.setTradeReturns(restored.getTradeReturns());
        a.contents[1] = null;
        new PitCommands().claimTradeReturns(a.player);
        new PitCommands().claimTradeReturns(a.player);
        assertEquals(110, a.count(Material.DIAMOND)); assertTrue(a.profile.getTradeReturns().isEmpty());
        assertEquals(110, Arrays.stream(a.profile.getInventory().getContents()).filter(Objects::nonNull)
                .filter(i -> i.getType() == Material.DIAMOND).mapToInt(ItemStack::getAmount).sum());
    }
    @Test public void coinEditingInvalidatesConsentAndOldInputsDoNotApplyTwice() {
        TradeManager trade = start(); trade.setCoins(a.player, 100); expire(trade); trade.setAConfirm(true);
        assertTrue(trade.isAConfirm());
        long token = trade.beginCoinEdit(b.player);
        assertFalse(trade.isAConfirm()); expire(trade); trade.setAConfirm(true); trade.setBConfirm(true);
        assertFalse(trade.isEnd()); assertFalse(trade.isAConfirm());
        trade.finishCoinEdit(b.player, token, new String[]{"200"});
        trade.finishCoinEdit(b.player, token, new String[]{"400"});
        assertEquals(200, trade.getBCoins(), 0); assertEquals(800, b.profile.getCoins(), 0);
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 0.5, 50001}) assertFalse(trade.setCoins(a.player, bad));
        assertEquals(100, trade.getACoins(), 0);
    }
    @Test public void disconnectAndShutdownRefundOnceBeforeProfilesAreSaved() {
        TradeManager trade = start(); a.contents[9] = new ItemStack(Material.DIAMOND, 2);
        trade.addItem(a.player, 9); trade.setCoins(b.player, 150);
        new TradeListener().onLogout(new PlayerQuitEvent(a.player, null));
        TradeManager.cancelAll();
        assertEquals(2, a.count(Material.DIAMOND)); assertEquals(1000, b.profile.getCoins(), 0);
        assertTrue(TradeManager.trades.isEmpty());
        start().setCoins(a.player, 200);
        TradeManager.cancelAll(); TradeManager.cancelAll();
        assertEquals(1000, a.profile.getCoins(), 0);
    }
    @Test public void dailyLimitsRecheckedAndOfferChangeClearsBothConfirmations() {
        TradeManager trade = start(); trade.setCoins(a.player, 100); expire(trade); trade.setAConfirm(true);
        trade.setCoins(b.player, 200); assertFalse(trade.isAConfirm()); assertFalse(trade.isBConfirm());
        a.profile.getTradeLimit().setTimes(25); finish(trade);
        assertTrue(trade.isEnd()); assertEquals(1000, a.profile.getCoins(), 0); assertEquals(1000, b.profile.getCoins(), 0);
    }
    private OfferButton offer(double price) {
        OfferData offer = new OfferData();
        offer.createOffer(a.player.getUniqueId(), new ItemStack(Material.DIAMOND, 3), price);
        b.profile.setOfferData(offer);
        return new OfferButton(b.player, offer);
    }
    @Test public void offerSettlementRejectsReplayAndCountsAmountNotTimes() {
        OfferButton button = offer(100);
        a.profile.getTradeLimit().setLastRefresh(System.currentTimeMillis());
        a.profile.getTradeLimit().setAmount(250);
        b.profile.getTradeLimit().setLastRefresh(System.currentTimeMillis());
        b.profile.getTradeLimit().setAmount(500);
        new OfferMenu(b.player).openMenu(a.player);
        a.click(15, ClickType.LEFT);
        button.clicked(a.player, 15, ClickType.LEFT, -1, null);
        assertEquals(3, a.count(Material.DIAMOND)); assertEquals(900, a.profile.getCoins(), 0);
        assertEquals(1100, b.profile.getCoins(), 0); assertEquals(350, a.profile.getTradeLimit().getAmount(), 0);
        assertEquals(600, b.profile.getTradeLimit().getAmount(), 0);
        assertFalse(b.profile.getOfferData().hasActiveOffer()); assertEquals(1, logs.constructed().size());
    }
    @Test public void staleExpiredWrongBuyerAndFullInventoryOffersDoNotTransfer() {
        OfferButton stale = offer(100); OfferData original = b.profile.getOfferData();
        original.setEndTime(0); stale.clicked(a.player, 15, ClickType.LEFT, -1, null);
        OfferButton current = offer(200);
        stale.clicked(a.player, 15, ClickType.LEFT, -1, null);
        Surface stranger = new Surface("Stranger"); current.clicked(stranger.player, 15, ClickType.LEFT, -1, null);
        a.full(); current.clicked(a.player, 15, ClickType.LEFT, -1, null);
        assertEquals(0, a.count(Material.DIAMOND)); assertEquals(1000, a.profile.getCoins(), 0);
        assertTrue(b.profile.getOfferData().hasActiveOffer()); assertTrue(logs.constructed().isEmpty());
    }
    @Test public void logFailureAfterCommitCannotLeaveRefundableEscrow() {
        logs.close();
        logs = mockConstruction(TradeData.class, (data, context) -> doThrow(new IllegalStateException("log unavailable")).when(data).save());
        TradeManager trade = start(); trade.setCoins(a.player, 100); finish(trade);
        trade.onPlayerCancel(a.player);
        assertTrue(trade.isEnd()); assertTrue(TradeManager.trades.isEmpty());
        assertEquals(900, a.profile.getCoins(), 0); assertEquals(1100, b.profile.getCoins(), 0);
    }

    @Test public void temporaryInventorySwitchCancelsBeforeBackupCanLoseRefunds() {
        TradeManager trade = start();
        a.contents[9] = new ItemStack(Material.DIAMOND, 2);
        trade.addItem(a.player, 9);
        a.profile.setInventory(cn.charlotte.pit.data.sub.PlayerInv.fromPlayerInventory(a.inventory));
        a.profile.setTempInvUsing(true);
        assertTrue(trade.isEnd());
        assertEquals(2, Arrays.stream(a.profile.getInventory().getContents()).filter(Objects::nonNull)
                .filter(i -> i.getType() == Material.DIAMOND).mapToInt(ItemStack::getAmount).sum());
        assertNotNull(TradeManager.startFailure(a.player, b.player));
        assertTrue(TradeManager.trades.isEmpty());
    }

    @Test public void inventoryWriteFailureRollsBackBothSidesAndRefundsOnce() {
        TradeManager trade = start();
        a.contents[9] = new ItemStack(Material.DIAMOND, 2);
        b.contents[9] = new ItemStack(Material.EMERALD, 4);
        trade.addItem(a.player, 9); trade.addItem(b.player, 9); trade.setCoins(a.player, 100);
        doThrow(new IllegalStateException("write failed once")).doAnswer(call -> {
            b.contents = TradeInventory.copy(call.getArgument(0)); return null;
        }).when(b.inventory).setContents(any(ItemStack[].class));
        expire(trade); trade.setAConfirm(true);
        assertThrows(IllegalStateException.class, () -> trade.setBConfirm(true));
        assertTrue(trade.isEnd()); assertTrue(TradeManager.trades.isEmpty());
        assertEquals(2, a.count(Material.DIAMOND)); assertEquals(0, a.count(Material.EMERALD));
        assertEquals(4, b.count(Material.EMERALD)); assertEquals(0, b.count(Material.DIAMOND));
        assertEquals(1000, a.profile.getCoins(), 0); assertEquals(1000, b.profile.getCoins(), 0);
        assertTrue(logs.constructed().isEmpty());
    }

    @Test public void rejectedMenuOpenReleasesSessionWithoutTouchingAnotherMenu() {
        when(b.player.openInventory(any(Inventory.class))).thenReturn(null);
        TradeManager trade = TradeManager.start(a.player, b.player);
        assertNotNull(trade); assertTrue(trade.isEnd());
        assertTrue(TradeManager.trades.isEmpty());
        assertEquals(1000, a.profile.getCoins(), 0);
    }

    @Test public void repeatedMixedTransactionsPreserveGlobalItemAndCoinTotals() {
        Random random = new Random(74301);
        for (int round = 0; round < 60; round++) {
            a.contents = new ItemStack[36]; b.contents = new ItemStack[36];
            a.profile.getTradeLimit().setTimes(0); a.profile.getTradeLimit().setAmount(0);
            b.profile.getTradeLimit().setTimes(0); b.profile.getTradeLimit().setAmount(0);
            a.profile.setCoins(1000); b.profile.setCoins(1000);
            int first = 1 + random.nextInt(64), second = 1 + random.nextInt(64);
            a.contents[9] = new ItemStack(Material.DIAMOND, first);
            b.contents[9] = new ItemStack(Material.DIAMOND, second);
            TradeManager trade = start();
            trade.addItem(a.player, 9); trade.addItem(b.player, 9);
            trade.setCoins(a.player, random.nextInt(1001)); trade.setCoins(b.player, random.nextInt(1001));
            long staleRevision = trade.getRevision();
            if (random.nextBoolean()) {
                assertTrue(trade.removeItem(a.player, 0, staleRevision));
                assertFalse(trade.removeItem(a.player, 0, staleRevision));
            }
            if (random.nextBoolean()) finish(trade); else trade.onPlayerCancel(b.player);
            trade.onPlayerCancel(a.player);
            assertEquals("round " + round, first + second, a.count(Material.DIAMOND) + b.count(Material.DIAMOND));
            assertEquals(2000, a.profile.getCoins() + b.profile.getCoins(), 0);
            assertTrue(TradeManager.trades.isEmpty());
        }
    }
}
