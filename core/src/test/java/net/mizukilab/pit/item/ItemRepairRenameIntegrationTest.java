package net.mizukilab.pit.item;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.data.sub.PlayerEnderChest;
import cn.charlotte.pit.data.sub.PlayerWarehouse;
import cn.charlotte.pit.data.sub.PlayerInv;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minecraft.server.v1_8_R3.*;
import net.mizukilab.pit.command.PitCommands;
import net.mizukilab.pit.config.PitWorldConfig;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.impl.PitInternalImpl;
import net.mizukilab.pit.item.type.*;
import net.mizukilab.pit.item.type.mythic.*;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftInventoryCustom;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.PrintStream;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual 1.8 item NBT, inventory click events, command handlers and disk serialization. */
public class ItemRepairRenameIntegrationTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedConstruction<PlayerWarehouse> warehouses;
    private MockedConstruction<PlayerEnderChest> enderChests;
    private PitInternalHook previousApi;
    private ThePit plugin;
    private ItemFactory factory;
    private Player player;
    private PlayerInventory inventory;
    private ItemStack[] contents;
    private ItemStack cursor;
    private InventoryView view;
    private final MythicRepairKit kit = new MythicRepairKit();

    @BeforeClass
    public static void registerVanillaItems() {
        PrintStream out = System.out, err = System.err;
        try (var attributes = mockConstruction(AttributeModifier.class)) { DispenserRegistry.c(); }
        finally { System.setOut(out); System.setErr(err); }
    }

    @Before
    public void setUp() {
        warehouses = mockConstruction(PlayerWarehouse.class);
        enderChests = mockConstruction(PlayerEnderChest.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class, RETURNS_DEEP_STUBS));
        pit = mockStatic(ThePit.class);
        plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getPitConfig()).thenReturn(mock(PitWorldConfig.class));
        when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());
        previousApi = ThePit.api;
        ThePit.api = mock(PitInternalHook.class);
        pit.when(ThePit::getApi).thenReturn(ThePit.api);
        when(ThePit.api.getWatermark()).thenReturn("test");
        freshFactory();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(anyString())).thenReturn(true);
        inventory = mock(PlayerInventory.class);
        when(inventory.getSize()).thenReturn(36);
        when(inventory.getHeldItemSlot()).thenReturn(2);
        when(player.getInventory()).thenReturn(inventory);
        contents = new ItemStack[40];
        when(inventory.getItem(anyInt())).thenAnswer(call -> contents[(int) call.getArgument(0)]);
        doAnswer(call -> { contents[(int) call.getArgument(0)] = call.getArgument(1); return null; })
                .when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        when(player.getItemInHand()).thenAnswer(call -> contents[2]);
        doAnswer(call -> { contents[2] = call.getArgument(0); return null; })
                .when(player).setItemInHand(nullable(ItemStack.class));
        when(player.getItemOnCursor()).thenAnswer(call -> cursor);
        doAnswer(call -> { cursor = call.getArgument(0); return null; })
                .when(player).setItemOnCursor(nullable(ItemStack.class));
        view = viewOf(new CraftInventoryCustom(player, InventoryType.CRAFTING));
        when(player.getOpenInventory()).thenAnswer(call -> view);
    }

    @After
    public void close() {
        ThePit.api = previousApi;
        pit.close();
        bukkit.close();
        enderChests.close();
        warehouses.close();
    }

    private void freshFactory() {
        factory = new ItemFactory();
        when(plugin.getItemFactory()).thenReturn(factory);
    }

    private InventoryView viewOf(Inventory top) {
        return new InventoryView() {
            @Override public Inventory getTopInventory() { return top; }
            @Override public Inventory getBottomInventory() { return inventory; }
            @Override public HumanEntity getPlayer() { return player; }
            @Override public InventoryType getType() { return top.getType(); }
        };
    }

    private ItemStack weapon(IMythicItem type) {
        type.setUuid(UUID.randomUUID());
        type.setVersion(PublicUtil.itemVersion);
        type.setLive(7);
        type.setMaxLive(30);
        type.setColor(MythicColor.RED);
        // Include a real identity so these tests also exercise the UUID cache.
        return edit(type.toItemStack(), tag -> ItemUtil.setUUIDObj(tag.getCompound("extra"), type.getUuid()));
    }

    private ItemStack kits(int count) {
        ItemStack result = MythicRepairKit.toItemStack0();
        result.setAmount(count);
        return result;
    }

    private InventoryClickEvent click(int slot, ClickType type) {
        int raw = view.getType() == InventoryType.CRAFTING ? (slot < 9 ? slot + 36 : slot)
                : view.getTopInventory().getSize() + (slot < 9 ? slot + 27 : slot - 9);
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                raw, type, InventoryAction.SWAP_WITH_CURSOR);
        assertEquals(slot, event.getSlot());
        return event;
    }

    private static ItemStack edit(ItemStack original, Consumer<NBTTagCompound> action) {
        var nms = CraftItemStack.asNMSCopy(original);
        nms.setTag((NBTTagCompound) nms.getTag().clone());
        action.accept(nms.getTag());
        return CraftItemStack.asBukkitCopy(nms);
    }

    private static void assertLife(ItemStack stack, int life) {
        assertEquals(life, ItemUtil.getExtra(stack).getInt("live"));
    }

    @Test
    public void heldSwordAndBowRepairToFullLife() {
        for (IMythicItem type : List.of(new MythicSwordItem(), new MythicBowItem())) {
            contents[2] = weapon(type);
            contents[12] = kits(1);
            InventoryClickEvent event = click(12, ClickType.RIGHT);
            kit.onInventoryClick(event);
            assertTrue(event.isCancelled());
            assertLife(contents[2], 30);
            assertTrue(contents[12] == null || contents[12].getType() == Material.AIR);
        }
    }

    @Test
    public void cursorKitRepairsClickedWeaponAndConsumesOnlyOne() {
        cursor = kits(3);
        contents[12] = weapon(new MythicSwordItem());
        kit.onInventoryClick(click(12, ClickType.RIGHT));
        assertLife(contents[12], 30);
        assertEquals(2, cursor.getAmount());
    }

    @Test
    public void cursorWeaponRepairsWithoutTouchingHeldWeapon() {
        cursor = weapon(new MythicBowItem());
        contents[2] = weapon(new MythicSwordItem());
        contents[12] = kits(2);
        kit.onInventoryClick(click(12, ClickType.RIGHT));
        assertLife(cursor, 30);
        assertLife(contents[2], 7);
        assertEquals(1, contents[12].getAmount());
    }

    @Test
    public void stackedKitsConsumeOneAndRepeatClickOnFullWeaponConsumesNone() {
        contents[2] = weapon(new MythicSwordItem());
        contents[12] = kits(5);
        kit.onInventoryClick(click(12, ClickType.RIGHT));
        assertEquals(4, contents[12].getAmount());
        kit.onInventoryClick(click(12, ClickType.RIGHT));
        assertEquals(4, contents[12].getAmount());
    }

    @Test
    public void repairPreservesAllExtraAndRootTagsAndOtherLore() {
        ItemStack original = edit(weapon(new MythicSwordItem()), tag -> {
            tag.setString("extension-root", "preserve");
            NBTTagCompound extra = tag.getCompound("extra");
            extra.setString("extension-data", "preserve");
            extra.setString("customName", "§b测试武器");
            extra.setBoolean("forceCanTrade", false);
            NBTTagList ench = new NBTTagList();
            ench.add(new NBTTagString("extension_enchant_not_registered:3"));
            extra.set("ench", ench);
            tag.getCompound("display").setString("Name", "§b测试武器");
            NBTTagList lore = new NBTTagList();
            lore.add(new NBTTagString("§7生命: §c7§7/30§a ♦§6 ᥀"));
            lore.add(new NBTTagString("§d扩展插件的描述"));
            tag.getCompound("display").set("Lore", lore);
        });
        contents[2] = original;
        contents[12] = kits(1);
        kit.onInventoryClick(click(12, ClickType.RIGHT));
        NBTTagCompound expected = (NBTTagCompound) Utils.toNMStackQuick(original).getTag().clone();
        NBTTagCompound actual = (NBTTagCompound) Utils.toNMStackQuick(contents[2]).getTag().clone();
        actual.getCompound("extra").setInt("live", 7);
        actual.getCompound("display").set("Lore", expected.getCompound("display").get("Lore"));
        assertEquals(expected, actual);
        assertEquals(List.of("§7生命: §a30§7/30§a ♦§6 ᥀", "§d扩展插件的描述"),
                contents[2].getItemMeta().getLore());
        assertLife(original, 7);
    }

    @Test
    public void numericLegacyLifeTagsAreAccepted() {
        contents[2] = edit(weapon(new MythicSwordItem()), tag -> {
            tag.getCompound("extra").setShort("maxLive", (short) 30);
            tag.getCompound("extra").setByte("live", (byte) 7);
        });
        contents[12] = kits(1);
        kit.onInventoryClick(click(12, ClickType.RIGHT));
        assertLife(contents[2], 30);
    }

    @Test
    public void cancelledClicksAndMenusCannotConsumeOrRepair() {
        contents[2] = weapon(new MythicSwordItem());
        contents[12] = kits(2);
        InventoryClickEvent cancelled = click(12, ClickType.RIGHT);
        cancelled.setCancelled(true);
        kit.onInventoryClick(cancelled);
        assertLife(contents[2], 7);
        assertEquals(2, contents[12].getAmount());
        view = viewOf(new CraftInventoryCustom(player, 27, "交易"));
        kit.onInventoryClick(click(12, ClickType.RIGHT));
        assertLife(contents[2], 7);
        assertEquals(2, contents[12].getAmount());
    }

    @Test
    public void leftShiftNumberDropAndDoubleClicksDoNotRepair() {
        contents[2] = weapon(new MythicSwordItem());
        contents[12] = kits(2);
        for (ClickType type : List.of(ClickType.LEFT, ClickType.SHIFT_RIGHT, ClickType.SHIFT_LEFT,
                ClickType.NUMBER_KEY, ClickType.DROP, ClickType.CONTROL_DROP, ClickType.DOUBLE_CLICK)) {
            kit.onInventoryClick(click(12, type));
            assertLife(contents[2], 7);
            assertEquals(2, contents[12].getAmount());
        }
    }

    @Test
    public void invalidFullAndStackedWeaponsDoNotConsumeKits() {
        ItemStack base = weapon(new MythicSwordItem());
        ItemStack stacked = base.clone();
        stacked.setAmount(2);
        for (ItemStack invalid : List.of(new ItemStack(Material.GOLD_SWORD), weapon(new LuckyChestplate()),
                new ItemBuilder(base.clone()).maxLive(0).build(),
                new ItemBuilder(base.clone()).live(30).build(), stacked)) {
            freshFactory();
            contents[2] = invalid;
            contents[12] = kits(2);
            kit.onInventoryClick(click(12, ClickType.RIGHT));
            assertEquals(2, contents[12].getAmount());
            assertEquals(invalid, contents[2]);
        }
    }

    @Test
    public void repairReadsActualNbtAndInvalidatesOldCache() {
        contents[2] = weapon(new MythicSwordItem());
        IMythicItem cached = factory.getIMythicItem(contents[2]);
        cached.setLive(29);
        contents[12] = kits(1);
        kit.onInventoryClick(click(12, ClickType.RIGHT));
        assertEquals(30, factory.getIMythicItem(contents[2]).getLive());
        assertNotSame(cached, factory.getIMythicItem(contents[2]));
    }

    @Test
    public void allMythicNamesSurviveSerializationAndFreshFactory() {
        PitCommands commands = new PitCommands();
        for (IMythicItem type : List.of(new MythicSwordItem(), new MythicBowItem(), new MythicLeggingsItem(),
                new AngelChestplate(), new AngelHelmet(), new AngelLeggings(), new AngelBoots(),
                new ArmageddonBoots(), new GoldenHelmet(), new LuckyChestplate(), new JewelSword(), new MagicFishingRod())) {
            contents[2] = weapon(type);
            assertEquals("§a已重命名", commands.rename(player, "&b重启后保留"));
            assertEquals(type.getInternalName(), "§b重启后保留", contents[2].getItemMeta().getDisplayName());
            contents[2] = InventoryUtil.deserializeItemStack(InventoryUtil.serializeItemStack(contents[2]));
            freshFactory(); // There is no surviving Java object after a restart.
            IMythicItem loaded = factory.getIMythicItem(contents[2]);
            assertEquals(type.getInternalName(), "§b重启后保留", loaded.getCustomName());
            contents[2] = loaded.toItemStack();
            assertEquals(type.getInternalName(), "§b重启后保留", contents[2].getItemMeta().getDisplayName());
            assertEquals("§b重启后保留", ItemUtil.getItemStringData(contents[2], "customName"));
            assertEquals(0, Utils.getMythicItem0(contents[2]).getForceCanTrade());
            assertEquals("§a成功取消重命名!", commands.unRename(player));
            freshFactory();
            assertNull(factory.getIMythicItem(contents[2]).getCustomName());
            assertEquals(-1, factory.getIMythicItem(contents[2]).getForceCanTrade());
        }
    }

    @Test
    public void renameAndUnrenamePreserveExtensionDataAndClearStaleCache() {
        contents[2] = new ItemBuilder(weapon(new MythicSwordItem())).changeNbt("extension-data", "keep").build();
        ItemStack original = contents[2];
        String originalNbt = Utils.toNMStackQuick(original).getTag().toString();
        PitCommands commands = new PitCommands();
        assertEquals("§a已重命名", commands.rename(player, "&b新名字"));
        assertEquals(originalNbt, Utils.toNMStackQuick(original).getTag().toString());
        assertEquals("keep", ItemUtil.getItemStringData(contents[2], "extension-data"));
        ItemStack renamed = contents[2];
        assertEquals("§a成功取消重命名!", commands.unRename(player));
        assertEquals("§b新名字", ItemUtil.getItemStringData(renamed, "customName"));
        assertEquals("keep", ItemUtil.getItemStringData(contents[2], "extension-data"));
        assertNull(ItemUtil.getItemStringData(contents[2], "customName"));
        freshFactory();
        assertNull(factory.getIMythicItem(contents[2]).getCustomName());
        assertNotEquals("§b新名字", factory.getIMythicItem(contents[2]).toItemStack().getItemMeta().getDisplayName());
    }

    @Test
    public void loadingUnrenamedNbtClearsNamesAndTradeFlagsOnReusedObjects() {
        for (IMythicItem type : List.of(new MythicSwordItem(), new AngelChestplate(), new ArmageddonBoots(),
                new GoldenHelmet(), new LuckyChestplate())) {
            ItemStack plain = weapon(type);
            type.setCustomName("旧名称");
            type.setForceCanTrade(0);
            type.loadFromItemStack(plain);
            assertNull(type.getInternalName(), type.getCustomName());
            assertEquals(type.getInternalName(), -1, type.getForceCanTrade());
        }
    }

    @Test
    public void fullSavedBackpackAndArmorKeepNamesWhenReformattedAfterRestart() throws Exception {
        when(ThePit.api.reformatPitItem(nullable(ItemStack.class)))
                .thenAnswer(call -> PitInternalImpl.INSTANCE.reformatPitItem(call.getArgument(0)));
        ItemStack[] storage = new ItemStack[36], armor = new ItemStack[4];
        List<IMythicItem> types = List.of(new MythicSwordItem(), new MythicBowItem(), new MythicLeggingsItem(),
                new AngelBoots(), new AngelLeggings(), new AngelChestplate(), new AngelHelmet());
        for (int i = 0; i < types.size(); i++) {
            contents[2] = weapon(types.get(i));
            assertEquals("§a已重命名", new PitCommands().rename(player, "&b测试 §c名字 " + i));
            if (i < 3) storage[i] = contents[2];
            else armor[i - 3] = contents[2];
        }
        ObjectMapper mapper = new ObjectMapper();
        String saved = mapper.writeValueAsString(new PlayerInv(storage, armor));
        freshFactory();
        PlayerInv restored = mapper.readValue(saved, PlayerInv.class);
        assertNotNull(restored);
        for (int i = 0; i < types.size(); i++) {
            ItemStack stack = i < 3 ? restored.getContents()[i] : restored.getArmorContents()[i - 3];
            assertNotNull(types.get(i).getInternalName(), stack);
            assertEquals("§b测试 §c名字 " + i, stack.getItemMeta().getDisplayName());
            assertEquals("§b测试 §c名字 " + i, Utils.getMythicItem0(stack).getCustomName());
        }
    }

    @Test
    public void storedCustomNamesRestoreStaleDisplayNamesAfterReload() {
        for (IMythicItem type : List.of(new MythicSwordItem(), new MythicBowItem(), new AngelBoots(),
                new ArmageddonBoots(), new GoldenHelmet(), new LuckyChestplate())) {
            ItemStack legacy = new ItemBuilder(weapon(type)).name(type.getItemDisplayName())
                    .customName("§d旧版本名字").forceCanTrade(false).build();
            IMythicItem reloaded = Utils.getMythicItem0(legacy);
            assertEquals(type.getInternalName(), "§d旧版本名字", reloaded.getCustomName());
            ItemStack rewritten = reloaded.toItemStack();
            assertEquals("§d旧版本名字", ItemUtil.getItemStringData(rewritten, "customName"));
            assertEquals("§d旧版本名字", rewritten.getItemMeta().getDisplayName());
        }
    }

    @Test
    public void repairingRenamedWeaponKeepsNameAfterDiskRoundTrip() {
        contents[2] = weapon(new MythicBowItem());
        assertEquals("§a已重命名", new PitCommands().rename(player, "&d修复后保留"));
        contents[12] = kits(1);
        kit.onInventoryClick(click(12, ClickType.RIGHT));
        freshFactory();
        ItemStack saved = InventoryUtil.deserializeItemStack(InventoryUtil.serializeItemStack(contents[2]));
        ItemStack rebuilt = factory.getIMythicItem(saved).toItemStack();
        assertLife(rebuilt, 30);
        assertEquals("§d修复后保留", rebuilt.getItemMeta().getDisplayName());
    }

    @Test
    public void invalidNamesAndUnauthorizedColorsDoNotModifyItem() {
        contents[2] = weapon(new MythicSwordItem());
        ItemStack original = contents[2].clone();
        PitCommands commands = new PitCommands();
        assertEquals("§c不可以这样哦!", commands.rename(player, "&a  "));
        when(player.hasPermission("pit.rename-color")).thenReturn(false);
        for (String name : List.of("&b彩色", "§b彩色", "   ")) {
            assertTrue(commands.rename(player, name).startsWith("§c"));
            assertEquals(original, contents[2]);
        }
        assertEquals("§a已重命名", commands.rename(player, "普通名字"));
        freshFactory();
        assertEquals("普通名字", factory.getIMythicItem(contents[2]).getCustomName());
    }

    @Test
    public void nonKitCursorCannotAccidentallyRepairHeldWeapon() {
        cursor = new ItemStack(Material.STONE);
        contents[2] = weapon(new MythicSwordItem());
        contents[12] = kits(3);
        kit.onInventoryClick(click(12, ClickType.RIGHT));
        assertLife(contents[2], 7);
        assertEquals(3, contents[12].getAmount());
        assertEquals(Material.STONE, cursor.getType());
    }
}
