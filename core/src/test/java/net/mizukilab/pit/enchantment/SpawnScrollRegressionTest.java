package net.mizukilab.pit.enchantment;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerEnderChest;
import cn.charlotte.pit.data.sub.PlayerWarehouse;
import net.minecraft.server.v1_8_R3.AttributeModifier;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.DataWatcher;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.mizukilab.pit.enchantment.info.EnchantRequest;
import net.mizukilab.pit.enchantment.menu.MythicWellMenu;
import net.mizukilab.pit.enchantment.menu.button.EnchantBookButton;
import net.mizukilab.pit.enchantment.param.item.WeaponOnly;
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity;
import net.mizukilab.pit.handlers.AnimationHandler;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.item.MythicColor;
import net.mizukilab.pit.item.type.MythicBook;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.listener.PlayerMarco;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.item.ItemUtil;
import net.mizukilab.pit.util.menu.Button;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real item NBT and enchantment transactions; server-owned inventories and transport are isolated. */
public class SpawnScrollRegressionTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerProfile> profiles;
    private MockedConstruction<PlayerWarehouse> warehouses;
    private MockedConstruction<PlayerEnderChest> enderChests;
    private PitInternalHook previousApi;
    private Player player;
    private PlayerInventory inventory;
    private final ItemStack[] contents = new ItemStack[36];
    private PlayerProfile profile;
    private MythicWellMenu menu;
    private EnchantmentFactor factor;
    private EnchantmentTable table;
    private IMythicItem sword;

    @BeforeClass
    public static void registerVanillaItems() {
        PrintStream out = System.out, err = System.err;
        try (var attributes = mockConstruction(AttributeModifier.class)) { DispenserRegistry.c(); }
        finally { System.setOut(out); System.setErr(err); }
    }

    @Before
    public void setUp() throws Exception {
        warehouses = mockConstruction(PlayerWarehouse.class);
        enderChests = mockConstruction(PlayerEnderChest.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
        Server server = mock(Server.class, RETURNS_DEEP_STUBS);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        pit = mockStatic(ThePit.class);
        ThePit plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        previousApi = ThePit.api;
        ThePit.api = mock(PitInternalHook.class);
        pit.when(ThePit::getApi).thenReturn(ThePit.api);
        when(ThePit.api.getWatermark()).thenReturn("test");
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("ScrollTester");
        when(player.isOnline()).thenReturn(true);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.iterator()).thenAnswer(call -> Arrays.asList(contents).listIterator());
        when(inventory.firstEmpty()).thenAnswer(call -> {
            for (int i = 0; i < contents.length; i++)
                if (contents[i] == null || contents[i].getType() == Material.AIR) return i;
            return -1;
        });
        when(inventory.getItem(anyInt())).thenAnswer(call -> contents[call.getArgument(0)]);
        doAnswer(call -> { contents[call.getArgument(0)] = call.getArgument(1); return null; })
                .when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        profile = new PlayerProfile(player.getUniqueId(), player.getName());
        Field loaded = PlayerProfile.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.set(profile, true);
        profiles = mockStatic(PlayerProfile.class);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
        factor = new EnchantmentFactor();
        factor.registerEnchantment(new ScrollRare());
        factor.registerEnchantment(new ScrollNormal());
        when(plugin.getEnchantmentFactor()).thenReturn(factor);
        table = new EnchantmentTable(factor);
        sword = new MythicSwordItem();
        sword.setColor(MythicColor.RED);
        profile.setEnchantingItem(InventoryUtil.serializeItemStack(sword.toItemStack()));
        AnimationHandler animator = mock(AnimationHandler.class);
        when(plugin.getAnimator()).thenReturn(animator);
        when(animator.holdOrGetAnimation(player)).thenReturn(new AnimationHandler.AnimationData(player));
        menu = spy(new MythicWellMenu(player));
        doNothing().when(menu).openMenu(player);
    }

    @After
    public void tearDown() {
        ThePit.api = previousApi;
        if (profiles != null) profiles.close();
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
        if (enderChests != null) enderChests.close();
        if (warehouses != null) warehouses.close();
    }

    @WeaponOnly
    public static class ScrollRare extends UnknownEnchantment {
        public ScrollRare() { super("scroll_test_rare"); }
        @Override public EnchantmentRarity getRarity() { return EnchantmentRarity.RARE; }
    }

    @WeaponOnly
    public static class ScrollNormal extends UnknownEnchantment {
        public ScrollNormal() { super("scroll_test_normal"); }
        @Override public EnchantmentRarity getRarity() { return EnchantmentRarity.NORMAL; }
    }

    // Vanilla reset internals need a running world; isolate only those calls.
    public static class ResetHandle extends EntityPlayer {
        DataWatcher watcher;
        public ResetHandle() { super(null, null, null, null); }
        @Override public DataWatcher getDataWatcher() { return watcher; }
        @Override public void removeAllEffects() { }
        @Override public void setAbsorptionHearts(float hearts) { }
    }

    private ItemStack scrolls(int count) {
        ItemStack result = MythicBook.toItemStack();
        result.setAmount(count);
        return result;
    }

    private InventoryClickEvent deposit(int slot, ClickType click) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getClick()).thenReturn(click);
        when(event.getCurrentItem()).thenReturn(contents[slot]);
        when(event.getSlot()).thenReturn(slot);
        menu.onClickEvent(event);
        verify(event).setCancelled(true);
        return event;
    }

    private EnchantRequest enchant(boolean useBook) {
        AtomicReference<EnchantRequest> result = new AtomicReference<>();
        table.enchantItem(player, profile, sword, useBook).promise(result::set);
        assertNotNull(result.get());
        return result.get();
    }

    @Test
    public void spawnAndRespawnResetKeepSelectedHotbarSlot() throws Exception {
        CraftPlayer craft = mock(CraftPlayer.class);
        Class<?> allocatorType = Class.forName("org.objenesis.ObjenesisStd");
        Object allocator = allocatorType.getConstructor().newInstance();
        ResetHandle handle;
        try (var attributes = mockConstruction(AttributeModifier.class)) {
            handle = (ResetHandle) allocatorType.getMethod("newInstance", Class.class)
                    .invoke(allocator, ResetHandle.class);
        }
        handle.watcher = mock(DataWatcher.class);
        when(craft.getHandle()).thenReturn(handle);
        when(craft.getInventory()).thenReturn(inventory);
        when(craft.getEnderChest()).thenReturn(mock(Inventory.class));
        AtomicInteger selected = new AtomicInteger(6);
        when(inventory.getHeldItemSlot()).thenAnswer(call -> selected.get());
        doAnswer(call -> { selected.set(call.getArgument(0)); return null; }).when(inventory).setHeldItemSlot(anyInt());
        PlayerUtil.resetPlayer(craft, true, false); // /spawn and death respawn
        assertEquals(6, inventory.getHeldItemSlot());
        PlayerUtil.resetPlayer(craft, false, false); // profile-ready initialization
        assertEquals(6, inventory.getHeldItemSlot());
        PlayerUtil.resetPlayer(craft, true, true); // full reset
        assertEquals(6, inventory.getHeldItemSlot());
        verify(inventory, never()).setHeldItemSlot(anyInt());
        verify(craft, times(3)).setFireTicks(0);
    }

    @Test
    public void newAndLegacyScrollsCannotBeDroppedAsSingleItemsOrStacks() {
        ItemStack fresh = scrolls(1);
        assertFalse(ItemUtil.getExtra(fresh).getBoolean("tradeAllow"));
        for (int count : new int[]{1, 64}) {
            ItemStack legacy = new ItemBuilder(scrolls(count)).canDrop(true).build();
            assertTrue(ItemUtil.getExtra(legacy).getBoolean("tradeAllow"));
            Item dropped = mock(Item.class);
            when(dropped.getItemStack()).thenReturn(legacy);
            PlayerDropItemEvent event = new PlayerDropItemEvent(player, dropped);
            new PlayerMarco().onDropItem(event);
            assertTrue(event.isCancelled());
            verify(dropped, never()).remove();
            assertEquals(count, legacy.getAmount());
        }
        assertTrue(ItemUtil.canDrop(new ItemBuilder(Material.PAPER).internalName("other_item").canDrop(true).build()));
    }

    @Test
    public void stackDepositEnchantAndWithdrawalConserveEveryUnusedScroll() {
        ItemStack books = scrolls(16);
        contents[8] = books;
        deposit(8, ClickType.SHIFT_LEFT);
        assertNull(contents[8]);
        assertEquals(16, profile.getEnchantingBookItemStackFormed().getAmount());
        EnchantRequest result = enchant(true);
        assertFalse(result.isFail());
        assertTrue(result.isUseBook());
        assertTrue(result.getMythic().isBoostedByBook());
        assertEquals(3, result.getMythic().getEnchantmentLevel("scroll_test_rare"));
        ItemStack remaining = profile.getEnchantingBookItemStackFormed();
        assertEquals(15, remaining.getAmount());
        assertTrue(books.isSimilar(remaining));
        EnchantBookButton button = new EnchantBookButton(menu, MythicColor.RED);
        assertEquals(15, button.getButtonItem(player).getAmount());
        button.clicked(player, 23, ClickType.LEFT, -1, remaining);
        assertNull(profile.getEnchantingBook());
        assertEquals(15, contents[0].getAmount());
        assertTrue(books.isSimilar(contents[0]));
    }

    @Test
    public void swappingIdenticalStacksUsesTheVacatedSlotEvenWhenInventoryIsFull() {
        ItemStack original = scrolls(8), incoming = original.clone();
        incoming.setAmount(12);
        profile.setEnchantingBook(InventoryUtil.serializeItemStack(original));
        Arrays.fill(contents, new ItemStack(Material.STONE, 64));
        contents[4] = incoming;
        deposit(4, ClickType.LEFT);
        assertEquals(12, profile.getEnchantingBookItemStackFormed().getAmount());
        assertEquals(8, contents[4].getAmount());
        assertTrue(original.isSimilar(contents[4]));
        verify(inventory, never()).addItem(any(ItemStack[].class));
    }

    @Test
    public void failedEnchantLeavesWholeStackAndOriginalEquipmentInTable() {
        factor.getEnchantmentMap().clear(); // No rare candidate: actual generator must fail.
        String books = InventoryUtil.serializeItemStack(scrolls(6));
        String before = profile.getEnchantingItem();
        profile.setEnchantingBook(books);
        assertTrue(enchant(true).isFail());
        assertEquals(before, profile.getEnchantingItem());
        assertEquals(books, profile.getEnchantingBook());
        assertEquals(0, sword.getTier());
    }

    @Test
    public void scrollRemainderSerializationFailureDoesNotConsumeAnything() {
        String books = InventoryUtil.serializeItemStack(scrolls(4));
        profile.setEnchantingBook(books);
        String before = profile.getEnchantingItem();
        try (MockedStatic<InventoryUtil> serializers = mockStatic(InventoryUtil.class, CALLS_REAL_METHODS)) {
            serializers.when(() -> InventoryUtil.serializeItemStack(argThat(stack -> stack != null
                    && stack.getType() == Material.PAPER && stack.getAmount() == 3))).thenReturn(null);
            assertTrue(enchant(true).isFail());
        }
        assertEquals(before, profile.getEnchantingItem());
        assertEquals(books, profile.getEnchantingBook());
    }

    @Test
    public void lastScrollIsConsumedAndUnusedScrollsStayWhenBookIsDisabled() {
        profile.setEnchantingBook(InventoryUtil.serializeItemStack(scrolls(1)));
        assertFalse(enchant(true).isFail());
        assertNull(profile.getEnchantingBook());
        String books = InventoryUtil.serializeItemStack(scrolls(7));
        profile.setEnchantingBook(books);
        assertFalse(enchant(false).isFail());
        assertEquals(books, profile.getEnchantingBook());
    }

    @Test
    public void fullInventoryWithdrawalAndDropKeyDoNotMoveScrolls() {
        String books = InventoryUtil.serializeItemStack(scrolls(64));
        profile.setEnchantingBook(books);
        Arrays.fill(contents, new ItemStack(Material.STONE, 64));
        new EnchantBookButton(menu, MythicColor.RED).clicked(player, 23, ClickType.LEFT, -1,
                profile.getEnchantingBookItemStackFormed());
        assertEquals(books, profile.getEnchantingBook());
        contents[5] = scrolls(10);
        for (ClickType click : new ClickType[]{ClickType.DROP, ClickType.CONTROL_DROP, ClickType.NUMBER_KEY}) {
            deposit(5, click);
            assertEquals(books, profile.getEnchantingBook());
            assertEquals(10, contents[5].getAmount());
        }
    }

    @Test
    public void remainingScrollsAreVisibleAfterFinalTierAndAfterEquipmentIsRemoved() {
        sword.getEnchantments().put(factor.getEnchantmentMap().get("scroll_test_normal"), 2);
        sword.setTier(2);
        profile.setEnchantingItem(InventoryUtil.serializeItemStack(sword.toItemStack()));
        profile.setEnchantingBook(InventoryUtil.serializeItemStack(scrolls(5)));
        assertFalse(enchant(true).isFail());
        assertEquals(3, Utils.getMythicItem0(InventoryUtil.deserializeItemStack(profile.getEnchantingItem())).getTier());
        for (boolean finished : new boolean[]{true, false}) {
            menu.getAnimationData().setFinished(finished);
            Button button = menu.getButtons(player).get(23);
            assertTrue(button instanceof EnchantBookButton);
            assertEquals(4, button.getButtonItem(player).getAmount());
        }
        profile.setEnchantingItem(InventoryUtil.serializeItemStack(new ItemStack(Material.AIR)));
        for (boolean finished : new boolean[]{true, false}) {
            menu.getAnimationData().setFinished(finished);
            Button button = menu.getButtons(player).get(23);
            assertTrue(button instanceof EnchantBookButton);
            assertEquals(4, button.getButtonItem(player).getAmount());
        }
        menu.getAnimationData().setStartEnchanting(true);
        assertFalse(menu.getButtons(player).values().stream().anyMatch(button -> button instanceof EnchantBookButton));
    }
}
