package cn.charlotte.pit.events;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.mail.Mail;
import cn.charlotte.pit.util.hologram.Hologram;
import cn.charlotte.pit.util.hologram.HologramAPI;
import com.google.common.util.concurrent.AtomicDouble;
import net.mizukilab.pit.OnlineServerTestContext;
import net.mizukilab.pit.events.impl.*;
import net.mizukilab.pit.menu.pack.PackageMenu;
import net.mizukilab.pit.menu.pack.button.ItemButton;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.cooldown.Cooldown;
import net.mizukilab.pit.util.item.ItemUtil;
import net.mizukilab.pit.util.level.LevelUtil;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.*;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EventRegressionTest extends net.mizukilab.pit.BukkitDataTestSupport {
    private ThePit plugin;
    private MockedStatic<ThePit> pit;
    private MockedStatic<CC> chat;
    private OnlineServerTestContext server;
    private EventFactory factory;
    private final List<Runnable> callbacks = new ArrayList<>();

    @Before public void setup() throws Exception {
        plugin = mock(ThePit.class, RETURNS_DEEP_STUBS);
        pit = mockStatic(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        chat = mockStatic(CC.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        server = new OnlineServerTestContext(scheduler, mock(PluginManager.class));
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
            callbacks.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        factory = new EventFactory();
        when(plugin.getEventFactory()).thenReturn(factory);
    }
    @After public void close() throws Exception {
        PackageMenu.getItems().clear();
        if (server != null) server.close();
        if (chat != null) chat.close();
        if (pit != null) pit.close();
    }
    private Player player() {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    @Test public void doubleRewardsMeansTwiceAndStopsAfterEvent() {
        DoubleRewardsEvent event = new DoubleRewardsEvent();
        AtomicDouble coins = new AtomicDouble(12), xp = new AtomicDouble(7);
        event.onActive(); event.handlePlayerKilled(0, null, null, coins, xp);
        assertEquals(24, coins.get(), 0); assertEquals(14, xp.get(), 0);
        event.onInactive(); event.handlePlayerKilled(0, null, null, coins, xp);
        assertEquals(24, coins.get(), 0); assertEquals(14, xp.get(), 0);
    }

    @Test public void quickMathQueuesAwardsOncePerPlayerAndAtMostFive() {
        try (MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class);
             MockedStatic<LevelUtil> levels = mockStatic(LevelUtil.class)) {
            QuickMathEvent event = new QuickMathEvent("6 * 7", "42");
            factory.activeEvent(event);
            event.setStartTime(System.currentTimeMillis() - 10_000);
            List<PlayerProfile> recipients = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                Player player = player(); PlayerProfile profile = mock(PlayerProfile.class);
                recipients.add(profile); when(profile.isLoaded()).thenReturn(true);
                profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
                AsyncPlayerChatEvent answer = new AsyncPlayerChatEvent(true, player, " 42 ", Set.of());
                event.onChat(answer); event.onChat(answer);
                assertTrue(answer.isCancelled()); verify(profile, never()).setCoins(anyDouble());
            }
            new ArrayList<>(callbacks).forEach(Runnable::run);
            assertEquals(5, event.getTop()); assertNull(factory.getActiveNormalEvent());
            for (int i = 0; i < 5; i++) verify(recipients.get(i)).setCoins(100);
            verify(recipients.get(5), never()).setCoins(anyDouble());
        }
    }

    @Test public void quickMathRejectsSubstringAnswerAndOldRoundCallbacks() {
        QuickMathEvent event = new QuickMathEvent("6 * 7", "42");
        factory.activeEvent(event);
        AsyncPlayerChatEvent wrong = new AsyncPlayerChatEvent(true, player(), "1420", Set.of());
        event.onChat(wrong); assertFalse(wrong.isCancelled()); assertTrue(callbacks.isEmpty());
        event.onChat(new AsyncPlayerChatEvent(true, player(), "42", Set.of()));
        Runnable old = callbacks.get(0);
        factory.inactiveEvent(event);
        event.TheEquation = "42"; event.TheEquationQuests = "40 + 2";
        factory.activeEvent(event); old.run();
        assertEquals(0, event.getTop()); assertSame(event, factory.getActiveNormalEvent());
    }

    @Test public void dragonEggRewardsOnlyTheActualEggAndRestoresOriginalBlock() throws Exception {
        World world = mock(World.class);
        Block block = mock(Block.class); BlockState original = mock(BlockState.class);
        Location location = new Location(world, 10, 80, 20);
        when(world.getBlockAt(10, 80, 20)).thenReturn(block);
        when(block.getLocation()).thenReturn(location); when(block.getState()).thenReturn(original);
        when(block.getType()).thenReturn(Material.DRAGON_EGG);
        when(plugin.getPitConfig().getDragonEggLoc()).thenReturn(location);
        Player player = player(); PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.isLoaded()).thenReturn(true);
        try (MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class)) {
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
            DragonEggsEvent event = new DragonEggsEvent();
            // Simulate a placed egg; packet holograms require a live CraftServer.
            for (var entry : Map.of("eggLocation", location, "previousBlock", original, "isActive", true, "isClick", true).entrySet()) {
                var field = DragonEggsEvent.class.getDeclaredField(entry.getKey());
                field.setAccessible(true); field.set(event, entry.getValue());
            }
            Block other = mock(Block.class); when(other.getLocation()).thenReturn(new Location(world, 11, 80, 20));
            when(other.getType()).thenReturn(Material.DRAGON_EGG);
            event.onInteract(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, other, BlockFace.UP));
            verify(profile, never()).setCoins(anyDouble());
            PlayerInteractEvent click = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP);
            event.onInteract(click); assertTrue(click.isCancelled()); verify(profile).setCoins(doubleThat(value -> value > 0));
            event.onInactive(); verify(original).update(true, false);
        }
    }

    @Test public void carePackageOldMenuCannotClaimNewRoundsLoot() {
        CarePackageEvent event = mock(CarePackageEvent.class);
        EventFactory mockFactory = mock(EventFactory.class);
        when(plugin.getEventFactory()).thenReturn(mockFactory);
        when(mockFactory.getActiveNormalEvent()).thenReturn(event);
        CarePackageEvent.ChestData first = new CarePackageEvent.ChestData(); first.setNum(0);
        when(event.getChestData()).thenReturn(first);
        ItemStack loot = mock(ItemStack.class);
        ItemButton button = new ItemButton(loot);
        CarePackageEvent.ChestData next = new CarePackageEvent.ChestData(); next.setNum(0);
        when(event.getChestData()).thenReturn(next); PackageMenu.getItems().put(0, loot);
        Player player = player(); button.clicked(player, 0, ClickType.LEFT, 0, loot);
        assertSame(loot, PackageMenu.getItems().get(0)); verify(player, never()).getInventory();
    }

    @Test public void carePackageFullInventoryKeepsLootAvailable() {
        CarePackageEvent event = mock(CarePackageEvent.class);
        EventFactory mockFactory = mock(EventFactory.class); when(plugin.getEventFactory()).thenReturn(mockFactory);
        when(mockFactory.getActiveNormalEvent()).thenReturn(event);
        CarePackageEvent.ChestData data = new CarePackageEvent.ChestData(); data.setNum(0);
        when(event.getChestData()).thenReturn(data);
        ItemStack loot = mock(ItemStack.class); PackageMenu.getItems().put(0, loot);
        Player player = player(); when(player.getInventory().firstEmpty()).thenReturn(-1);
        PlayerProfile profile = mock(PlayerProfile.class); when(profile.isLoaded()).thenReturn(true);
        try (MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class);
             MockedStatic<ItemUtil> items = mockStatic(ItemUtil.class)) {
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
            new ItemButton(loot).clicked(player, 0, ClickType.LEFT, 0, loot);
            assertSame(loot, PackageMenu.getItems().get(0)); assertTrue(data.getRewarded().isEmpty());
        }
    }

    @Test public void cancelledAuctionRefundsOfflineBidOnceEvenAfterExpiry() {
        AuctionEvent event = new AuctionEvent();
        UUID uuid = UUID.randomUUID();
        event.getBidHistories().add(new AuctionEvent.BidHistory(uuid, 1, 100));
        event.getBidHistories().add(new AuctionEvent.BidHistory(uuid, 2, 200));
        event.setTimer(new Cooldown(0)); ((AbstractEvent)event).markAborting();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<AuctionEvent> auction = mockStatic(AuctionEvent.class)) {
            event.onInactive(); event.onInactive();
            auction.verify(() -> AuctionEvent.sendMail(eq(uuid), argThat(mail -> mail.getCoins() == 200)), times(1));
        }
    }

    @Test public void worldEditsKeepBuildUndoAndNextBuildOrdered() throws Exception {
        DoubleRewardsEvent event = new DoubleRewardsEvent(); ((AbstractEvent)event).beginLifecycle();
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        EventWorldEdits.build(event, () -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker gate timed out"); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            order.add("old build");
        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            ((AbstractEvent)event).endLifecycle();
            EventWorldEdits.undo(() -> order.add("old undo"));
            ((AbstractEvent)event).beginLifecycle();
            EventWorldEdits.build(event, () -> order.add("new build"));
        } finally { release.countDown(); }
        EventWorldEdits.undo(done::countDown);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("old build", "old undo", "new build"), order);
    }

    @Test public void blockHeadDoesNotOverwriteContainersAndRestoresClaimedBlockOnce() throws Exception {
        var event = new net.mizukilab.pit.events.impl.major.BlockHeadEvent();
        var data = new net.mizukilab.pit.events.impl.major.BlockHeadEvent.PlayerBlockHeadData();
        var material = data.getClass().getDeclaredField("block"); material.setAccessible(true); material.set(data, Material.WOOL);
        var claim = event.getClass().getDeclaredMethod("claimBlock", Player.class, data.getClass(), Location.class);
        claim.setAccessible(true);
        World world = mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        Location location = new Location(world, 1, 70, 1);
        Block block = mock(Block.class); when(world.getBlockAt(1, 70, 1)).thenReturn(block);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(block.getType()).thenReturn(Material.CHEST); when(block.getState()).thenReturn(mock(Chest.class));
        Player player = player();
        assertEquals(false, claim.invoke(event, player, data, location));
        verify(block, never()).setType(any());
        BlockState original = mock(BlockState.class);
        when(block.getType()).thenReturn(Material.STONE); when(block.getState()).thenReturn(original);
        assertEquals(true, claim.invoke(event, player, data, location));
        assertEquals(true, claim.invoke(event, player, data, location));
        assertEquals(1, data.getBelong(), 0);
        ((AbstractEvent)event).markAborting(); event.onInactive();
        verify(original).update(true, false);
    }

    @Test public void missingEpicMapCannotMutatePlayersOrScheduleBuilds() {
        class UnconfiguredSpire extends AbstractEvent implements cn.charlotte.pit.events.trigger.type.IEpicEvent {
            int starts;
            public String getEventInternalName() { return "spire"; }
            public String getEventName() { return "Spire"; }
            public int requireOnline() { return 0; }
            public void onActive() { starts++; }
            public void onInactive() { fail("Nothing started, so no cleanup is necessary"); }
        }
        UnconfiguredSpire event = new UnconfiguredSpire();
        factory.activeEvent(event);
        assertEquals(0, event.starts); assertFalse(event.isActive()); assertNull(factory.getActiveEpicEvent());
    }

    @Test public void blockHeadPaletteCanCycleForHundredsOfPlayers() {
        var event = new net.mizukilab.pit.events.impl.major.BlockHeadEvent();
        for (int i = 0; i < 200; i++) {
            Material material = event.pickRandomBlockMaterial();
            assertTrue(material.isSolid());
            assertNotEquals(Material.FURNACE, material); assertNotEquals(Material.BURNING_FURNACE, material);
        }
    }
}
