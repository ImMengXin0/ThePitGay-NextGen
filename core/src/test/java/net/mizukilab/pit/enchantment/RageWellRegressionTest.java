package net.mizukilab.pit.enchantment;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerEnderChest;
import cn.charlotte.pit.data.sub.PlayerWarehouse;
import net.minecraft.server.v1_8_R3.AttributeModifier;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.mizukilab.pit.enchantment.menu.MythicWellMenu;
import net.mizukilab.pit.enchantment.menu.button.EnchantDisplayButton;
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity;
import net.mizukilab.pit.enchantment.type.rage.Brakes;
import net.mizukilab.pit.enchantment.type.rage.NewDealEnchant;
import net.mizukilab.pit.enchantment.type.ragerare.ThinkOfThePeopleEnchant;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.item.MythicColor;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.item.ItemUtil;
import net.mizukilab.pit.util.random.RandomUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercise the real well deposit, serialized NBT, item cache and rage selection. */
public class RageWellRegressionTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerProfile> profiles;
    private MockedStatic<PlayerUtil> perks;
    private MockedConstruction<PlayerWarehouse> warehouses;
    private MockedConstruction<PlayerEnderChest> enderChests;
    private PitInternalHook previousApi;
    private Player player;
    private PlayerInventory inventory;
    private PlayerProfile profile;
    private MythicWellMenu menu;
    private ItemFactory items;
    private EnchantmentFactor factor;
    private final ItemStack[] contents = new ItemStack[36];
    private final NewDealEnchant normal = new NewDealEnchant();
    private final ThinkOfThePeopleEnchant rare = new ThinkOfThePeopleEnchant();

    @BeforeClass
    public static void registerVanillaItems() {
        PrintStream out = System.out, err = System.err;
        try (var attributes = mockConstruction(AttributeModifier.class)) { DispenserRegistry.c(); }
        finally { System.setOut(out); System.setErr(err); }
    }

    @Before
    public void setup() throws Exception {
        warehouses = mockConstruction(PlayerWarehouse.class);
        enderChests = mockConstruction(PlayerEnderChest.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
        Server server = mock(Server.class, RETURNS_DEEP_STUBS);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        org.bukkit.plugin.PluginManager pluginManager = server.getPluginManager();
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
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
        when(player.getName()).thenReturn("RageTester");
        when(player.isOnline()).thenReturn(true);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.iterator()).thenAnswer(call -> Arrays.asList(contents).listIterator());
        doAnswer(call -> { contents[call.getArgument(0)] = call.getArgument(1); return null; })
                .when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        profile = new PlayerProfile(player.getUniqueId(), player.getName());
        Field loaded = PlayerProfile.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.set(profile, true);
        profile.setCoins(10000);
        profiles = mockStatic(PlayerProfile.class);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
        perks = mockStatic(PlayerUtil.class, CALLS_REAL_METHODS);
        perks.when(() -> PlayerUtil.getPlayerUnlockedPerkLevel(player, "Mythicism")).thenReturn(9);
        factor = new EnchantmentFactor();
        factor.registerEnchantment(normal);
        factor.registerEnchantment(rare);
        factor.registerEnchantment(new Brakes());
        when(plugin.getEnchantmentFactor()).thenReturn(factor);
        items = new ItemFactory();
        when(plugin.getItemFactory()).thenReturn(items);
        menu = spy(new MythicWellMenu(player));
        doNothing().when(menu).openMenu(player);
    }

    @After
    public void tearDown() {
        ThePit.api = previousApi;
        if (perks != null) perks.close();
        if (profiles != null) profiles.close();
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
        if (enderChests != null) enderChests.close();
        if (warehouses != null) warehouses.close();
    }

    private MythicLeggingsItem pants(UUID id) {
        MythicLeggingsItem item = new MythicLeggingsItem();
        item.setColor(MythicColor.RAGE);
        item.setUuid(id);
        item.setVersion(PublicUtil.itemVersion);
        return item;
    }

    private IMythicItem primeOldResult(UUID id) {
        MythicLeggingsItem stale = pants(id);
        stale.setTier(3);
        stale.setLive(22);
        stale.setMaxLive(22);
        stale.getEnchantments().put(rare, 3);
        return items.getIMythicItem0(stale.toItemStack(), "mythic_leggings", true);
    }

    private IMythicItem deposit(ItemStack input) {
        profile.setEnchantingItem(null);
        String original = InventoryUtil.serializeItemStack(input);
        clickInput(input);
        assertEquals(original, InventoryUtil.serializeItemStack(input));
        assertTrue(contents[8] == null || contents[8].getType() == Material.AIR);
        assertEquals(10000, profile.getCoins(), 0D);
        assertFalse(menu.getAnimationData().isStartEnchanting());
        assertNotNull(profile.getEnchantingItem());
        return Utils.getMythicItem0(InventoryUtil.deserializeItemStack(profile.getEnchantingItem()));
    }

    private void clickInput(ItemStack input) {
        contents[8] = input;
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getCurrentItem()).thenReturn(input);
        when(event.getSlot()).thenReturn(8);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        menu.onClickEvent(event);
        verify(event).setCancelled(true);
    }

    @Test
    public void blankRagePantsCannotInheritAnOldResultWithTheSameUuid() {
        UUID id = new UUID(0, 20260918);
        IMythicItem cached = primeOldResult(id);
        ItemStack input = pants(id).toItemStack();
        assertEquals(id, ItemUtil.getUUIDObj(input));
        assertEquals(id, cached.getUuid());
        assertNotSame(cached, items.getIMythicItem(input));
        assertTrue(items.getIMythicItem(input).getEnchantments().isEmpty());
        IMythicItem deposited = deposit(input);
        assertEquals(0, deposited.getTier());
        assertTrue(deposited.getEnchantments().isEmpty());
        assertEquals(id, deposited.getUuid());
        assertEquals(MythicColor.RAGE, deposited.getColor());
        assertTrue(deposited.getEnchantmentRecords().isEmpty());
    }

    @Test
    public void blankRagePantsCannotInheritAnUnrelatedUuidHashCollision() {
        UUID inputId = new UUID(0, 101), cachedId = new UUID(101, 0);
        assertNotEquals(inputId, cachedId);
        assertEquals(inputId.hashCode(), cachedId.hashCode());
        primeOldResult(cachedId);
        IMythicItem deposited = deposit(pants(inputId).toItemStack());
        assertEquals(0, deposited.getTier());
        assertTrue(deposited.getEnchantments().isEmpty());
        assertEquals(inputId, deposited.getUuid());
    }

    @Test
    public void takingPantsOutCannotRestoreACollidingCachedEnchantment() {
        UUID inputId = new UUID(0, 101), cachedId = new UUID(101, 0);
        primeOldResult(cachedId);
        deposit(pants(inputId).toItemStack());
        ItemStack inWell = InventoryUtil.deserializeItemStack(profile.getEnchantingItem());
        doAnswer(call -> {
            contents[8] = call.getArgument(0);
            return new HashMap<Integer, ItemStack>();
        }).when(inventory).addItem(any(ItemStack.class));
        new EnchantDisplayButton(inWell, menu).clicked(player, 20, ClickType.LEFT, -1, inWell);
        assertNull(InventoryUtil.deserializeItemStack(profile.getEnchantingItem()));
        assertNotNull(contents[8]);
        IMythicItem worn = items.getIMythicItem(contents[8]);
        assertEquals(inputId, worn.getUuid());
        assertEquals(0, worn.getTier());
        assertTrue(worn.getEnchantments().isEmpty());
        assertSame(worn, items.getIMythicItem(contents[8]));
    }

    @Test
    public void uuidLookupsNeverReturnAnotherItemWithTheSameHash() {
        UUID cachedId = new UUID(101, 0), otherId = new UUID(0, 101);
        IMythicItem cached = primeOldResult(cachedId);
        assertNull(items.getItem(otherId));
        assertFalse(items.hasItem(otherId));
        assertNull(items.getIMythicItemFromUUIDString(otherId.toString()));
        assertSame(cached, items.getItem(cachedId));
    }

    @Test
    public void normalUuidLookupReusesItsOwnCachedItem() {
        UUID id = UUID.fromString("abfedc98-7654-4321-9234-fedcba987654");
        IMythicItem cached = primeOldResult(id);
        assertSame(cached, items.getIMythicItem(cached.toItemStack()));
        assertSame(cached, items.getIMythicItemFromUUIDString(id.toString()));
    }

    @Test
    public void factoryDoesNotChangeANormalUuidWhoseHashEqualsTheDefault() {
        UUID id = new UUID(2, 3);
        ItemStack input = CraftItemStack.asCraftMirror(Utils.toNMStackQuick(pants(id).toItemStack()).cloneItemStack());
        assertEquals(id, items.getIMythicItem(input).getUuid());
        assertEquals(id, ItemUtil.getUUIDObj(input));
    }

    @Test
    public void migratingIdentityCannotReturnThePreviousCachedResult() {
        UUID id = new UUID(0, 6758);
        primeOldResult(id);
        ItemStack input = CraftItemStack.asCraftMirror(Utils.toNMStackQuick(pants(id).toItemStack()).cloneItemStack());
        String previousVersion = PublicUtil.itemVersion;
        try {
            PublicUtil.itemVersion = "rage-review-uuid";
            IMythicItem migrated = items.getIMythicItem(input);
            assertNotEquals(id, migrated.getUuid());
            assertEquals(ItemUtil.getUUIDObj(input), migrated.getUuid());
            assertEquals(0, migrated.getTier());
            assertTrue(migrated.getEnchantments().isEmpty());
            assertSame(migrated, items.getIMythicItem(input));
        } finally {
            PublicUtil.itemVersion = previousVersion;
        }
    }

    @Test
    public void alreadyEnchantedRagePantsKeepTheirActualEnchantNameAndLives() {
        UUID id = new UUID(0, 20260919);
        MythicLeggingsItem actual = pants(id);
        actual.setTier(1);
        actual.setMaxLive(9);
        actual.setLive(4);
        actual.setCustomName("§b自己的裤子");
        actual.getEnchantments().put(normal, 1);
        ItemStack input = actual.toItemStack();
        IMythicItem cached = primeOldResult(id);
        assertEquals(id, ItemUtil.getUUIDObj(input));
        assertNotSame(cached, items.getIMythicItem(input));
        assertEquals(actual.getEnchantments(), items.getIMythicItem(input).getEnchantments());
        IMythicItem deposited = deposit(input);
        assertEquals(1, deposited.getTier());
        assertEquals(actual.getEnchantments(), deposited.getEnchantments());
        assertEquals(9, deposited.getMaxLive());
        assertEquals(4, deposited.getLive());
        assertEquals(actual.getCustomName(), deposited.getCustomName());
    }

    @Test
    public void newBlankPantsGetAnIndependentIdentityWithoutReceivingEnchantments() {
        IMythicItem first = deposit(pants(IMythicItem.getDefUUID()).toItemStack());
        IMythicItem second = deposit(pants(IMythicItem.getDefUUID()).toItemStack());
        assertNotEquals(IMythicItem.getDefUUID(), first.getUuid());
        assertNotEquals(first.getUuid(), second.getUuid());
        assertEquals(0, first.getTier());
        assertTrue(first.getEnchantments().isEmpty());
        assertTrue(second.getEnchantments().isEmpty());
        IMythicItem reinserted = deposit(first.toItemStack());
        assertEquals(first.getUuid(), reinserted.getUuid());
        assertTrue(reinserted.getEnchantments().isEmpty());
    }

    @Test
    public void legacyPantsWithoutAnIdentityGetAUuidWithoutEnchanting() {
        ItemStack input = new ItemBuilder(pants(IMythicItem.getDefUUID()).toItemStack())
                .changeNbt("uuid", (String) null).build();
        assertNull(ItemUtil.getUUIDObj(input));
        IMythicItem deposited = deposit(input);
        assertNotNull(deposited.getUuid());
        assertNotEquals(IMythicItem.getDefUUID(), deposited.getUuid());
        assertTrue(deposited.getEnchantments().isEmpty());
        assertEquals(0, deposited.getTier());
        assertNull(ItemUtil.getUUIDObj(input));
    }

    @Test
    public void realUuidWithDefaultHashIsNotMistakenForTheDefaultIdentity() {
        UUID id = new UUID(2, 3);
        assertEquals(IMythicItem.getDefUUID().hashCode(), id.hashCode());
        assertEquals(id, deposit(pants(id).toItemStack()).getUuid());
    }

    @Test
    public void versionMigrationPreservesExistingEnchantmentsAndDoesNotEditSource() {
        MythicLeggingsItem actual = pants(UUID.randomUUID());
        actual.setTier(1);
        actual.setLive(4);
        actual.setMaxLive(9);
        actual.setCustomName("§b迁移测试");
        actual.getEnchantments().put(normal, 2);
        ItemStack input = actual.toItemStack();
        String previousVersion = PublicUtil.itemVersion;
        try {
            PublicUtil.itemVersion = "rage-regression-uuid";
            IMythicItem deposited = deposit(input);
            assertNotEquals(actual.getUuid(), deposited.getUuid());
            assertEquals(PublicUtil.itemVersion, deposited.getVersion());
            assertEquals(1, deposited.getTier());
            assertEquals(actual.getEnchantments(), deposited.getEnchantments());
            assertEquals(4, deposited.getLive());
            assertEquals(9, deposited.getMaxLive());
            assertEquals(actual.getCustomName(), deposited.getCustomName());
        } finally {
            PublicUtil.itemVersion = previousVersion;
        }
    }

    @Test
    public void replacingTheWellItemReturnsItThroughTheClickedSlot() {
        ItemStack previous = pants(new UUID(0, 7654)).toItemStack();
        profile.setEnchantingItem(InventoryUtil.serializeItemStack(previous));
        ItemStack input = pants(new UUID(0, 7655)).toItemStack();
        clickInput(input);
        assertEquals(previous, contents[8]);
        IMythicItem deposited = Utils.getMythicItem0(InventoryUtil.deserializeItemStack(profile.getEnchantingItem()));
        assertEquals(ItemUtil.getUUIDObj(input), deposited.getUuid());
        assertEquals(0, deposited.getTier());
        assertTrue(deposited.getEnchantments().isEmpty());
        verify(inventory, never()).addItem(any(ItemStack[].class));
    }

    @Test
    public void failedSerializationKeepsBothTheWellAndInventoryItems() {
        ItemStack previous = pants(new UUID(0, 8754)).toItemStack();
        String encoded = InventoryUtil.serializeItemStack(previous);
        profile.setEnchantingItem(encoded);
        ItemStack input = pants(IMythicItem.getDefUUID()).toItemStack();
        String original = InventoryUtil.serializeItemStack(input);
        try (MockedStatic<InventoryUtil> serialization = mockStatic(InventoryUtil.class, CALLS_REAL_METHODS)) {
            serialization.when(() -> InventoryUtil.serializeItemStack(any(ItemStack.class))).thenReturn(null);
            clickInput(input);
        }
        assertEquals(encoded, profile.getEnchantingItem());
        assertSame(input, contents[8]);
        assertEquals(original, InventoryUtil.serializeItemStack(input));
        verify(inventory, never()).setItem(anyInt(), nullable(ItemStack.class));
        verify(inventory, never()).addItem(any(ItemStack[].class));
        verify(menu, never()).openMenu(player);
    }

    @Test
    public void firstTierCanSelectEveryRegisteredNormalRageEnchantment() {
        EnchantmentTable table = new EnchantmentTable(factor);
        Set<String> selected = new HashSet<>();
        try (MockedStatic<RandomUtil> random = mockStatic(RandomUtil.class, CALLS_REAL_METHODS)) {
            random.when(RandomUtil::random).thenReturn(new Random(20260918));
            for (int i = 0; i < 128; i++) {
                MythicLeggingsItem item = pants(UUID.randomUUID());
                assertFalse(EnchantmentTable.Tier.TIER_1.normal.invoke(0D, table.armorEnchant, item));
                assertEquals(1, item.getEnchantments().size());
                item.getEnchantments().forEach((enchant, level) -> {
                    assertEquals(EnchantmentRarity.RAGE, enchant.getRarity());
                    assertTrue(level >= 1 && level <= Math.min(2, enchant.getMaxEnchantLevel()));
                    selected.add(enchant.getNbtName());
                });
            }
        }
        assertEquals(Set.of(normal.getNbtName(), "brakes"), selected);
    }

    @Test
    public void rareRageSelectionUsesTheRarePoolWithoutChangingOtherRarityPools() {
        EnchantmentTable table = new EnchantmentTable(factor);
        MythicLeggingsItem item = pants(UUID.randomUUID());
        assertTrue(EnchantmentTable.Tier.TIER_1.normal.invoke(1D, table.armorEnchant, item));
        assertEquals(1, item.getEnchantments().size());
        assertTrue(item.getEnchantments().containsKey(rare));
        assertFalse(table.armorEnchant.containsKey(EnchantmentRarity.RARE));
    }
}
