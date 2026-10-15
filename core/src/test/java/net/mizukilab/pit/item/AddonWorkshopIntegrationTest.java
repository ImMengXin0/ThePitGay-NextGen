package net.mizukilab.pit.item;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerEnderChest;
import cn.charlotte.pit.data.sub.PlayerWarehouse;
import net.minecraft.server.v1_8_R3.*;
import net.mizukilab.pit.enchantment.*;
import net.mizukilab.pit.enchantment.info.EnchantRequest;
import net.mizukilab.pit.enchantment.param.item.WeaponOnly;
import net.mizukilab.pit.enchantment.param.item.BowOnly;
import net.mizukilab.pit.enchantment.param.item.ArmorOnly;
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.item.type.MythicBook;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.item.type.mythic.MythicBowItem;
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.item.ItemUtil;
import net.mizukilab.pit.util.menu.Button;
import net.mizukilab.pit.util.menu.Menu;
import org.bukkit.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.*;
import org.mockito.MockedStatic;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Executes unmodified Addon workshop buttons with real core serialization and scroll enchanting. */
public class AddonWorkshopIntegrationTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerProfile> profiles;
    private URLClassLoader loader;
    private PitInternalHook oldApi;
    private Player player;
    private ItemFactory factory;
    private EnchantmentFactor factor;
    private PlayerProfile profile;
    private final ItemStack[] contents = new ItemStack[36];
    private final AbstractEnchantment normal = new Normal(), rare = new Rare(), added = new Added();
    private Object pitUtil;

    @BeforeClass public static void vanilla() {
        PrintStream out = System.out, err = System.err;
        try (var attributes = mockConstruction(AttributeModifier.class)) { DispenserRegistry.c(); }
        finally { System.setOut(out); System.setErr(err); }
    }

    @Before public void setup() throws Exception {
        loader = new URLClassLoader(new URL[]{Path.of(System.getProperty("pitAddonJar")).toUri().toURL(),
                Path.of(System.getProperty("pitAddonLoaderJar")).toUri().toURL(),
                Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()}, getClass().getClassLoader());
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
        pit = mockStatic(ThePit.class);
        ThePit plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        factor = new EnchantmentFactor();
        for (AbstractEnchantment e : List.of(normal, rare, added)) factor.registerEnchantment(e);
        when(plugin.getEnchantmentFactor()).thenReturn(factor);
        factory = new ItemFactory();
        when(plugin.getItemFactory()).thenReturn(factory);
        oldApi = ThePit.api;
        ThePit.api = mock(PitInternalHook.class);
        pit.when(ThePit::getApi).thenReturn(ThePit.api);
        when(ThePit.api.getWatermark()).thenReturn("test");
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("WorkshopTester");
        when(player.isOnline()).thenReturn(true);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getItemInHand()).thenAnswer(c -> contents[0]);
        doAnswer(c -> { contents[0] = c.getArgument(0); return null; }).when(player).setItemInHand(any(ItemStack.class));
        when(inventory.getSize()).thenReturn(contents.length);
        when(inventory.getItem(anyInt())).thenAnswer(c -> contents[c.getArgument(0)]);
        when(inventory.iterator()).thenAnswer(c -> Arrays.asList(contents).listIterator());
        doAnswer(c -> { contents[c.getArgument(0)] = c.getArgument(1); return null; })
                .when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        doAnswer(c -> {
            int material = c.getArgument(0);
            for (int i = 0; i < contents.length; i++)
                if (contents[i] != null && contents[i].getTypeId() == material) contents[i] = null;
            return null;
        }).when(inventory).remove(anyInt());
        Inventory menuInventory = mock(Inventory.class);
        when(menuInventory.getSize()).thenReturn(27);
        bukkit.when(() -> Bukkit.createInventory(player, 27, "附魔升级")).thenReturn(menuInventory);
        try (var warehouses = mockConstruction(PlayerWarehouse.class); var chests = mockConstruction(PlayerEnderChest.class)) {
            profile = new PlayerProfile(player.getUniqueId(), player.getName());
        }
        var loaded = PlayerProfile.class.getDeclaredField("loaded");
        loaded.setAccessible(true); loaded.set(profile, true);
        profile.setCoins(10000);
        profiles = mockStatic(PlayerProfile.class);
        profiles.when(() -> PlayerProfile.getRawCache(player.getUniqueId())).thenReturn(profile);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
        Class<? extends JavaPlugin> loaderType = loader.loadClass("cn.apnf.mistral.Loader").asSubclass(JavaPlugin.class);
        JavaPlugin addon = mock(loaderType);
        when(addon.getConfig()).thenReturn(new YamlConfiguration());
        var instance = loaderType.getDeclaredField("instance"); instance.setAccessible(true); instance.set(null, addon);
        Class<?> util = loader.loadClass("cn.apnf.mistral.util.PitUtil");
        pitUtil = util.getField("INSTANCE").get(null);
    }

    @After public void close() throws Exception {
        if (player != null) Menu.currentlyOpenedMenus.remove(player.getUniqueId());
        ThePit.api = oldApi;
        if (profiles != null) profiles.close();
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
        if (loader != null) loader.close();
    }

    @WeaponOnly @BowOnly @ArmorOnly public static class Normal extends UnknownEnchantment {
        public Normal() { super("workshop_normal"); }
        @Override public EnchantmentRarity getRarity() { return EnchantmentRarity.NORMAL; }
    }
    @WeaponOnly @BowOnly @ArmorOnly public static class Rare extends UnknownEnchantment {
        public Rare() { super("workshop_scroll_rare"); }
        @Override public EnchantmentRarity getRarity() { return EnchantmentRarity.RARE; }
    }
    @WeaponOnly @BowOnly @ArmorOnly public static class Added extends Normal {
        @Override public String getNbtName() { return "workshop_added"; }
    }

    private IMythicItem sword() {
        return prepared(new MythicSwordItem());
    }

    private IMythicItem prepared(IMythicItem item) {
        item.setUuid(UUID.randomUUID()); item.setVersion(PublicUtil.itemVersion); item.setColor(MythicColor.RED);
        item.setTier(1); item.setLive(4); item.setMaxLive(8); item.setCustomName("§bWorkshop sword");
        item.getEnchantments().put(normal, 2);
        contents[0] = item.toItemStack();
        return factory.getIMythicItem(contents[0]);
    }

    private IMythicItem scrollEnchant() {
        return scrollEnchant(new MythicSwordItem());
    }

    private IMythicItem scrollEnchant(IMythicItem template) {
        IMythicItem cached = prepared(template);
        profile.setEnchantingItem(InventoryUtil.serializeItemStack(contents[0]));
        profile.setEnchantingBook(InventoryUtil.serializeItemStack(MythicBook.toItemStack()));
        AtomicReference<EnchantRequest> result = new AtomicReference<>();
        new EnchantmentTable(factor).enchantItem(player, profile, cached, true).promise(result::set);
        assertNotNull(result.get()); assertFalse(result.get().isFail());
        contents[0] = InventoryUtil.deserializeItemStack(profile.getEnchantingItem());
        IMythicItem actual = Utils.getMythicItem0(contents[0]);
        assertEquals(3, actual.getEnchantments().getInt(rare));
        return actual;
    }

    private void click(String menuName, String buttonName, AbstractEnchantment enchant, Integer level) throws Exception {
        Class<?> type = loader.loadClass("cn.apnf.mistral.menus." + menuName);
        Object menu = type.getConstructor().newInstance();
        Class<?> buttonType = loader.loadClass(type.getName() + "$" + buttonName);
        var constructor = level == null ? buttonType.getDeclaredConstructor(type, AbstractEnchantment.class)
                : buttonType.getDeclaredConstructor(type, AbstractEnchantment.class, int.class);
        constructor.setAccessible(true);
        Button button = (Button) (level == null ? constructor.newInstance(menu, enchant) : constructor.newInstance(menu, enchant, level));
        button.clicked(player, 13, ClickType.LEFT, -1, null);
    }

    private void assertPreserved(IMythicItem before, int normalLevel) {
        ItemStack reloaded = InventoryUtil.deserializeItemStack(InventoryUtil.serializeItemStack(contents[0]));
        IMythicItem after = new ItemFactory().getIMythicItem(reloaded);
        assertEquals(3, after.getEnchantments().getInt(rare));
        assertEquals(normalLevel, after.getEnchantments().getInt(normal));
        assertEquals(before.getUuid(), after.getUuid());
        assertEquals(before.getCustomName(), after.getCustomName());
        assertTrue(after.isBoostedByBook());
        assertEquals(before.getEnchantmentRecords().size(), after.getEnchantmentRecords().size());
    }

    @Test public void scrollEnchantSurvivesMaterialPaymentUpgradeAndReloadWithFullInventory() throws Exception {
        for (IMythicItem template : List.of(new MythicSwordItem(), new MythicBowItem(), new MythicLeggingsItem())) {
        IMythicItem before = scrollEnchant(template);
        for (int i = 1; i < contents.length; i++) contents[i] = new ItemStack(Material.OBSIDIAN, 64);
        contents[1] = MythicBook.toItemStack(); contents[1].setAmount(7);
        click("EnchantUpgradeMenu", "UpgradeButton", normal, 2);
        assertPreserved(before, 3);
        IMythicItem after = Utils.getMythicItem0(contents[0]);
        assertEquals(before.getLive(), after.getLive()); assertEquals(before.getMaxLive(), after.getMaxLive());
        assertEquals(before.getTier(), after.getTier()); assertEquals(2, contents[1].getAmount());
        for (int i = 2; i < contents.length; i++) assertEquals(64, contents[i].getAmount());
        }
    }

    @Test public void scrollEnchantSurvivesTargetedEnchantAndSubsequentInventoryRefresh() throws Exception {
        for (IMythicItem template : List.of(new MythicSwordItem(), new MythicBowItem(), new MythicLeggingsItem())) {
        profile.setCoins(10000);
        IMythicItem before = scrollEnchant(template);
        click("TargetedEnchantMenu", "EnchantApplyButton", added, null);
        pitUtil.getClass().getMethod("flushPlayerItem", Player.class).invoke(pitUtil, player);
        assertPreserved(before, 2);
        IMythicItem after = Utils.getMythicItem0(contents[0]);
        assertEquals(1, after.getEnchantments().getInt(added)); assertEquals(3, after.getTier());
        assertEquals(8000, profile.getCoins(), 0);
        }
    }

    @Test public void insufficientMaterialsAndMaxedEnchantDoNotAlterTheItemOrConsumeScrolls() throws Exception {
        scrollEnchant();
        contents[1] = MythicBook.toItemStack(); contents[1].setAmount(4);
        // Parsing may repair the legacy derived UUID tags, but no gameplay data may change.
        NBTTagCompound before = (NBTTagCompound) Utils.toNMStackQuick(contents[0]).getTag().clone();
        UUID id = ItemUtil.getUUIDObj(contents[0]);
        ItemUtil.updateMagic(before.getCompound("extra"), id);
        click("EnchantUpgradeMenu", "UpgradeButton", normal, 2);
        assertEquals(before, Utils.toNMStackQuick(contents[0]).getTag());
        assertEquals(4, contents[1].getAmount());
        click("EnchantUpgradeMenu", "UpgradeButton", rare, 3);
        assertEquals(before, Utils.toNMStackQuick(contents[0]).getTag());
        assertEquals(4, contents[1].getAmount()); assertEquals(10000, profile.getCoins(), 0);
    }

    @Test public void upgradePreservesUnknownEnchantmentsAndOtherScrollEnchantedInventoryItems() throws Exception {
        IMythicItem other = scrollEnchant(new MythicBowItem());
        contents[5] = contents[0];
        IMythicItem before = scrollEnchant();
        UnknownEnchantment legacy = new UnknownEnchantment("legacy_scroll_enchant");
        before.getEnchantments().put(legacy, 2);
        contents[0] = before.toItemStack();
        contents[1] = MythicBook.toItemStack(); contents[1].setAmount(5);
        click("EnchantUpgradeMenu", "UpgradeButton", normal, 2);
        assertPreserved(before, 3);
        IMythicItem after = Utils.getMythicItem0(contents[0]);
        assertEquals(2, after.getEnchantmentLevel("legacy_scroll_enchant"));
        IMythicItem otherAfter = Utils.getMythicItem0(contents[5]);
        assertEquals(3, otherAfter.getEnchantments().getInt(rare));
        assertEquals(other.getUuid(), otherAfter.getUuid());
        assertEquals(other.getLive(), otherAfter.getLive());
    }

    @Test public void postEnchantEventResultReplacesAnyCacheCreatedByTheListener() throws Exception {
        IMythicItem result = scrollEnchant();
        factory.getIMythicItem(contents[0]);
        result.getEnchantments().put(added, 1);
        var end = net.mizukilab.pit.enchantment.menu.button.EnchantButton.class
                .getDeclaredMethod("end", Player.class, IMythicItem.class);
        end.setAccessible(true); end.invoke(null, player, result);
        contents[0] = InventoryUtil.deserializeItemStack(profile.getEnchantingItem());
        pitUtil.getClass().getMethod("flushPlayerItem", Player.class).invoke(pitUtil, player);
        assertPreserved(result, 2);
        assertEquals(1, Utils.getMythicItem0(contents[0]).getEnchantments().getInt(added));
    }

    @Test public void builderAndRawListEditsInvalidateOnlyChangedEnchantments() {
        IMythicItem cached = sword();
        Map<AbstractEnchantment, Integer> enchants = new HashMap<>(cached.getEnchantments());
        enchants.put(rare, 3);
        ItemStack edited = new ItemBuilder(contents[0]).enchant(enchants).build();
        IMythicItem refreshed = factory.getIMythicItem(edited);
        assertEquals(3, refreshed.getEnchantments().getInt(rare));
        assertSame(refreshed, factory.getIMythicItem(refreshed.toItemStack()));
        NBTTagList list = new NBTTagList(); list.add(new NBTTagString(normal.getNbtName() + ":3"));
        list.add(new NBTTagString(rare.getNbtName() + ":2"));
        edited = new ItemBuilder(edited).changeNbtList("ench", list).build();
        assertEquals(2, factory.getIMythicItem(edited).getEnchantments().getInt(rare));
    }
}
