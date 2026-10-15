package net.mizukilab.pit.menu;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerEnderChest;
import cn.charlotte.pit.data.sub.PlayerWarehouse;
import net.minecraft.server.v1_8_R3.AttributeModifier;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.mizukilab.pit.config.NewConfiguration;
import net.mizukilab.pit.config.PitWorldConfig;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.EnchantmentTable;
import net.mizukilab.pit.enchantment.UnknownEnchantment;
import net.mizukilab.pit.enchantment.menu.MythicWellMenu;
import net.mizukilab.pit.enchantment.param.item.WeaponOnly;
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity;
import net.mizukilab.pit.handlers.AnimationHandler;
import net.mizukilab.pit.impl.PitInternalImpl;
import net.mizukilab.pit.item.MythicColor;
import net.mizukilab.pit.item.type.MythicBook;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.util.ParticleUtil;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.menu.ButtonListener;
import net.mizukilab.pit.util.menu.Menu;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftInventory;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftInventoryCustom;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real menus, CraftBukkit inventory wrappers, item NBT, enchanting and animator ticks. */
public class MythicAnimationIntegrationTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerProfile> profiles;
    private MockedStatic<ParticleUtil> particles;
    private MockedConstruction<PlayerWarehouse> warehouses;
    private MockedConstruction<PlayerEnderChest> enderChests;
    private PitInternalHook previousApi;
    private boolean previousRapid;
    private Player player;
    private PlayerProfile profile;
    private AnimationHandler animator;
    private InventoryView view;
    private PlayerInventory bottom;
    private Inventory crafting;
    private final ButtonListener listener = new ButtonListener();

    @BeforeClass
    public static void registerItems() {
        PrintStream out = System.out, err = System.err;
        try (var attributes = mockConstruction(AttributeModifier.class)) { DispenserRegistry.c(); }
        finally { System.setOut(out); System.setErr(err); }
    }

    @Before
    public void setUp() throws Exception {
        warehouses = mockConstruction(PlayerWarehouse.class);
        enderChests = mockConstruction(PlayerEnderChest.class);
        bukkit = mockStatic(Bukkit.class);
        pit = mockStatic(ThePit.class);
        // Packet delivery is server-owned; menu frames and block/sound calls remain observable.
        particles = mockStatic(ParticleUtil.class);
        particles.when(() -> ParticleUtil.getColorFromMythicColor(anyString())).thenReturn(new int[]{255, 0, 0});
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
        Server server = mock(Server.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
                .thenAnswer(call -> new CraftInventoryCustom(call.getArgument(0), (int) call.getArgument(1), call.getArgument(2)));
        ThePit plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        previousApi = ThePit.api;
        ThePit.api = mock(PitInternalHook.class);
        pit.when(ThePit::getApi).thenReturn(ThePit.api);
        when(ThePit.api.getWatermark()).thenReturn("test");
        previousRapid = NewConfiguration.INSTANCE.getRapidEnchanting();
        NewConfiguration.INSTANCE.setRapidEnchanting(false);

        World world = mock(World.class);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        PitWorldConfig config = mock(PitWorldConfig.class);
        when(config.getEnchantLocation()).thenReturn(new Location(world, 0, 100, 0));
        when(plugin.getPitConfig()).thenReturn(config);
        animator = new AnimationHandler();
        when(plugin.getAnimator()).thenReturn(animator);
        EnchantmentFactor factor = new EnchantmentFactor();
        factor.registerEnchantment(new AnimationRare());
        when(plugin.getEnchantmentFactor()).thenReturn(factor);
        when(plugin.getEnchTable()).thenReturn(new EnchantmentTable(factor));

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("AnimatorTester");
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenAnswer(call -> new Location(world, 0, 100, 0));
        bottom = mock(PlayerInventory.class);
        when(bottom.getSize()).thenReturn(36);
        when(player.getInventory()).thenReturn(bottom);
        crafting = mock(Inventory.class);
        when(crafting.getType()).thenReturn(InventoryType.CRAFTING);
        view = viewOf(crafting);
        when(player.getOpenInventory()).thenAnswer(call -> view);
        doAnswer(call -> {
            listener.onInventoryClose(new InventoryCloseEvent(view));
            view = viewOf(crafting);
            return null;
        }).when(player).closeInventory();
        when(player.openInventory(any(Inventory.class))).thenAnswer(call -> {
            listener.onInventoryClose(new InventoryCloseEvent(view));
            CraftInventory inventory = call.getArgument(0);
            view = viewOf(new CraftInventory(inventory.getInventory()));
            return view;
        });
        profile = new PlayerProfile(player.getUniqueId(), player.getName());
        Field loaded = PlayerProfile.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.set(profile, true);
        profile.setCoins(100000);
        profiles = mockStatic(PlayerProfile.class);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
        resetInput();
    }

    private void resetInput() {
        MythicSwordItem sword = new MythicSwordItem();
        sword.setColor(MythicColor.RED);
        profile.setEnchantingItem(InventoryUtil.serializeItemStack(sword.toItemStack()));
        ItemStack scrolls = MythicBook.toItemStack();
        scrolls.setAmount(6);
        profile.setEnchantingBook(InventoryUtil.serializeItemStack(scrolls));
    }

    @WeaponOnly
    public static class AnimationRare extends UnknownEnchantment {
        public AnimationRare() { super("animation_test_rare"); }
        @Override public EnchantmentRarity getRarity() { return EnchantmentRarity.RARE; }
    }

    private InventoryView viewOf(Inventory top) {
        return new InventoryView() {
            @Override public Inventory getTopInventory() { return top; }
            @Override public Inventory getBottomInventory() { return bottom; }
            @Override public HumanEntity getPlayer() { return player; }
            @Override public InventoryType getType() { return top.getType(); }
        };
    }

    private void clickEnchant() {
        listener.onButtonPress(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                25, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    private void assertFullAnimation(MythicWellMenu menu) {
        clickEnchant();
        AnimationHandler.AnimationData data = menu.getAnimationData();
        assertTrue(data.isStartEnchanting());
        assertTrue(data.isEnd()); // Result committed; visual sequence still must play.
        assertFalse(data.isFinished());
        assertEquals(5, profile.getEnchantingBookItemStackFormed().getAmount());
        assertEquals(Material.STAINED_GLASS_PANE, view.getTopInventory().getItem(20).getType());
        assertFalse(menu.getButtons().containsKey(23));
        for (int tick = 0; tick < 60; tick++) {
            animator.run();
            assertFalse("Ended early at frame " + tick, data.isFinished());
            if (tick == 12 || tick == 36) {
                assertEquals(Material.INK_SACK, view.getTopInventory().getItem(20).getType());
            }
        }
        animator.run();
        assertTrue(data.isFinished());
        assertEquals(Material.GOLD_SWORD, view.getTopInventory().getItem(20).getType());
        assertEquals(5, view.getTopInventory().getItem(23).getAmount());
        assertSame(data, animator.getAnimations().get(player.getUniqueId()));
        verify(player, atLeastOnce()).sendBlockChange(any(Location.class), eq(Material.STAINED_GLASS), anyByte());
        verify(player, atLeastOnce()).playSound(any(Location.class), eq(Sound.LEVEL_UP), anyFloat(), anyFloat());
    }

    @Test
    public void scrollEnchantPlaysAllFramesAndCanAnimateAgainAfterCompletion() {
        MythicWellMenu menu = new MythicWellMenu(player);
        menu.openMenu(player);
        assertFullAnimation(menu);
        resetInput();
        menu.openMenu(player);
        assertFullAnimation(menu);
        assertEquals(98000, profile.getCoins(), 0);
        verify(player, times(1)).openInventory(any(Inventory.class));
    }

    @Test
    public void replacingAnOpenWellKeepsNewAnimationRegistered() {
        MythicWellMenu oldMenu = new MythicWellMenu(player);
        oldMenu.openMenu(player);
        MythicWellMenu replacement = new MythicWellMenu(player);
        replacement.openMenu(player);
        assertSame(replacement, Menu.find(player, view.getTopInventory()));
        assertSame(replacement.getAnimationData(), animator.getAnimations().get(player.getUniqueId()));
        assertFalse(replacement.getAnimationData().isFinished());
        assertFullAnimation(replacement);
    }

    @Test
    public void reopeningClosedMenuInstanceRegistersAnimationAgain() {
        MythicWellMenu menu = new MythicWellMenu(player);
        menu.openMenu(player);
        player.closeInventory();
        assertFalse(animator.getAnimations().containsKey(player.getUniqueId()));
        menu.openMenu(player);
        assertSame(menu.getAnimationData(), animator.getAnimations().get(player.getUniqueId()));
        assertFalse(menu.getAnimationData().isFinished());
        assertFullAnimation(menu);
    }

    @Test
    public void rapidEnchantingStillPlaysAllFramesWithoutManualSkip() {
        NewConfiguration.INSTANCE.setRapidEnchanting(true);
        MythicWellMenu menu = new MythicWellMenu(player);
        menu.openMenu(player);
        assertFullAnimation(menu);
    }

    @Test
    public void rapidModeAllowsClosingToSkipWithoutChangingResultOrScrolls() {
        NewConfiguration.INSTANCE.setRapidEnchanting(true);
        MythicWellMenu menu = new MythicWellMenu(player);
        menu.openMenu(player);
        clickEnchant();
        assertFalse(menu.getAnimationData().isFinished());
        String result = profile.getEnchantingItem();
        animator.run();
        player.closeInventory();
        assertTrue(menu.getAnimationData().isFinished());
        assertNull(Menu.find(player));
        assertFalse(animator.getAnimations().containsKey(player.getUniqueId()));
        PitInternalImpl.INSTANCE.openMythicWellMenu(player);
        assertEquals(Material.GOLD_SWORD, view.getTopInventory().getItem(20).getType());
        assertEquals(result, profile.getEnchantingItem());
        assertEquals(5, profile.getEnchantingBookItemStackFormed().getAmount());
        assertEquals(99000, profile.getCoins(), 0);
    }

    @Test
    public void duplicateApiOpenRetainsTheRunningAnimationAndChargesOnlyOnce() {
        PitInternalImpl.INSTANCE.openMythicWellMenu(player);
        MythicWellMenu menu = (MythicWellMenu) Menu.find(player);
        clickEnchant();
        for (int tick = 0; tick < 13; tick++) animator.run();
        AnimationHandler.AnimationData data = menu.getAnimationData();
        int frame = data.getAnimationTick();
        PitInternalImpl.INSTANCE.openMythicWellMenu(player);
        PitInternalImpl.INSTANCE.openMythicWellMenu(player);
        assertSame(menu, Menu.find(player));
        assertSame(data, menu.getAnimationData());
        assertSame(data, animator.getAnimations().get(player.getUniqueId()));
        assertEquals(frame, data.getAnimationTick());
        assertFalse(data.isFinished());
        for (int tick = frame; tick <= 60; tick++) animator.run();
        assertTrue(data.isFinished());
        assertEquals(5, view.getTopInventory().getItem(23).getAmount());
        assertEquals(99000, profile.getCoins(), 0);
        verify(player, times(1)).openInventory(any(Inventory.class));
    }

    @Test
    public void finishedAnimationDoesNotDecodeOrResendGlassUntilSourceChanges() {
        MythicWellMenu menu = new MythicWellMenu(player);
        menu.openMenu(player);
        assertFullAnimation(menu);
        clearInvocations(player);
        try (var inventoryUtil = mockStatic(InventoryUtil.class, CALLS_REAL_METHODS)) {
            for (int i = 0; i < 200; i++) animator.run();
            inventoryUtil.verify(() -> InventoryUtil.deserializeItemStack(anyString()), never());
            verify(player, never()).sendBlockChange(any(Location.class), any(Material.class), anyByte());
            verify(player, never()).updateInventory();
            MythicSwordItem replacement = new MythicSwordItem();
            replacement.setColor(MythicColor.BLUE);
            String encoded = InventoryUtil.serializeItemStack(replacement.toItemStack());
            profile.setEnchantingItem(encoded);
            for (int i = 0; i < 20; i++) animator.run();
            inventoryUtil.verify(() -> InventoryUtil.deserializeItemStack(encoded), times(1));
            verify(player, times(8)).sendBlockChange(any(Location.class), eq(Material.STAINED_GLASS), eq(MythicColor.BLUE.getColorByte()));
        }
    }

    @Test
    public void animationKeepsSixtyTicksButUpdatesMenuOnlyWhenItsFrameChanges() {
        MythicWellMenu menu = new MythicWellMenu(player);
        menu.openMenu(player);
        clickEnchant();
        clearInvocations(player);
        for (int i = 0; i < 60; i++) animator.run();
        assertFalse(menu.getAnimationData().isFinished());
        verify(player, times(28)).updateInventory();
        animator.run();
        assertTrue(menu.getAnimationData().isFinished());
        verify(player, times(29)).updateInventory();
        assertEquals(Material.GOLD_SWORD, view.getTopInventory().getItem(20).getType());
        assertEquals(5, profile.getEnchantingBookItemStackFormed().getAmount());
    }

    @After
    public void tearDown() {
        if (player != null && Menu.find(player) != null) {
            Menu.find(player).setClosedByMenu(true);
            player.closeInventory();
        }
        NewConfiguration.INSTANCE.setRapidEnchanting(previousRapid);
        ThePit.api = previousApi;
        if (profiles != null) profiles.close();
        if (particles != null) particles.close();
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
        if (enderChests != null) enderChests.close();
        if (warehouses != null) warehouses.close();
    }
}
