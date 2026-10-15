package net.mizukilab.pit.item;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerWarehouse;
import cn.charlotte.pit.data.sub.PlayerEnderChest;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.AttributeModifier;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.config.PitWorldConfig;
import net.mizukilab.pit.impl.PitInternalImpl;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.item.type.AngelBoots;
import net.mizukilab.pit.item.type.AngelChestplate;
import net.mizukilab.pit.item.type.AngelHelmet;
import net.mizukilab.pit.item.type.AngelLeggings;
import net.mizukilab.pit.item.type.MythicEnchantingTable;
import net.mizukilab.pit.listener.GameEffectListener;
import net.mizukilab.pit.listener.PlayerMarco;
import net.mizukilab.pit.menu.admin.item.AdminItemMenu;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.item.ItemUtil;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.menu.Button;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Uses real CraftBukkit item metadata/NBT, core parsing and the actual damage listener. */
public class AngelBootsIntegrationTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedConstruction<PlayerWarehouse> warehouses;
    private MockedConstruction<PlayerEnderChest> enderChests;
    private PitInternalHook previousApi;
    private ItemFactory factory;

    @BeforeClass
    public static void registerVanillaItems() {
        PrintStream out = System.out, err = System.err;
        // Vanilla attribute initialization calls a removed commons-lang overload on the test classpath.
        // These item tests need the real item/NBT registry, but do not evaluate vanilla attributes.
        try (MockedConstruction<AttributeModifier> modifiers = mockConstruction(AttributeModifier.class)) {
            DispenserRegistry.c();
        }
        finally { System.setOut(out); System.setErr(err); }
    }

    @Before
    public void setUpCore() {
        warehouses = mockConstruction(PlayerWarehouse.class);
        enderChests = mockConstruction(PlayerEnderChest.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
        Server server = mock(Server.class, RETURNS_DEEP_STUBS);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        pit = mockStatic(ThePit.class);
        ThePit plugin = mock(ThePit.class);
        when(plugin.getPitConfig()).thenReturn(mock(PitWorldConfig.class));
        pit.when(ThePit::getInstance).thenReturn(plugin);
        previousApi = ThePit.api;
        ThePit.api = mock(PitInternalHook.class);
        pit.when(ThePit::getApi).thenReturn(ThePit.api);
        when(ThePit.api.getWatermark()).thenReturn("test");
        ItemFactor items = new ItemFactor();
        items.registerItem(new AngelBoots());
        items.registerItem(new AngelChestplate());
        items.registerItem(new AngelHelmet());
        items.registerItem(new AngelLeggings());
        items.getItemMap().put("enchant_table_mobile", MythicEnchantingTable.class);
        when(plugin.getItemFactor()).thenReturn(items);
        when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());
        factory = new ItemFactory();
        factory.setClientSide(true);
        when(plugin.getItemFactory()).thenReturn(factory);
    }

    @After
    public void closeCore() {
        ThePit.api = previousApi;
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
        if (enderChests != null) enderChests.close();
        if (warehouses != null) warehouses.close();
    }

    @Test
    public void bootsMatchChestplateLifeLoreAndPersistenceFlags() {
        ItemStack boots = new AngelBoots().toItemStack();
        ItemStack chestplate = new AngelChestplate().toItemStack();
        assertEquals(Material.DIAMOND_BOOTS, boots.getType());
        assertEquals("§f天使之靴", boots.getItemMeta().getDisplayName());
        assertEquals(chestplate.getItemMeta().getLore(), boots.getItemMeta().getLore());
        assertTrue(boots.getItemMeta().getLore().get(0).contains("100§7/100"));
        assertTrue(boots.getItemMeta().getLore().stream().anyMatch(line -> line.contains("-10%")));
        assertEquals("angel_boots", ItemUtil.getInternalName(boots));
        NBTTagCompound extra = ItemUtil.getExtra(boots), chestExtra = ItemUtil.getExtra(chestplate);
        for (String key : List.of("deathDrop", "tradeAllow", "canTrade", "enderChest", "removeOnJoin")) {
            assertTrue(key, extra.hasKey(key));
            assertEquals(key, chestExtra.getBoolean(key), extra.getBoolean(key));
        }
        assertTrue(Utils.toNMStackQuick(boots).getTag().getBoolean("Unbreakable"));
        assertNotEquals(ItemUtil.getUUIDObj(chestplate), ItemUtil.getUUIDObj(boots));
    }

    @Test
    public void parsingReformatAndDeathLifeKeepBootsIdentity() {
        AngelBoots original = new AngelBoots();
        original.setLive(23);
        original.setCustomName("§b自定义靴子");
        original.setForceCanTrade(0);
        ItemStack stack = original.toItemStack();
        assertTrue(Utils.needToCheck("angel_boots"));
        IMythicItem loaded = factory.getIMythicItem(stack);
        assertTrue(loaded instanceof AngelBoots);
        assertEquals(original.getUuid(), loaded.getUuid());
        assertEquals(23, loaded.getLive());
        assertEquals(100, loaded.getMaxLive());
        assertEquals(0, loaded.getForceCanTrade());
        ItemStack refreshed = PitInternalImpl.INSTANCE.reformatPitItem(stack);
        assertEquals(Material.DIAMOND_BOOTS, refreshed.getType());
        assertEquals(original.getUuid(), ItemUtil.getUUIDObj(refreshed));
        assertEquals("§b自定义靴子", refreshed.getItemMeta().getDisplayName());
        ItemStack worn = Utils.subtractLive(refreshed);
        assertEquals(22, Utils.getMythicItem0(worn).getLive());
        loaded.setLive(1);
        assertEquals(Material.AIR, Utils.subtractLive(loaded).getType());
        loaded.setLive(0);
        assertNull(PitInternalImpl.INSTANCE.reformatPitItem(loaded.toItemStack()));
    }

    @Test
    public void adminMenuDispensesIndependentFullLifeBoots() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        Button button = new AdminItemMenu().getButtons(player).get(13);
        assertNotNull("The /pi menu must include the boots", button);
        ItemStack preview = button.getButtonItem(player);
        List<ItemStack> given = new ArrayList<>();
        doAnswer(call -> { given.add(call.getArgument(0)); return new HashMap<Integer, ItemStack>(); })
                .when(inventory).addItem(any(ItemStack.class));
        button.clicked(player, 13, ClickType.LEFT, -1, preview);
        button.clicked(player, 13, ClickType.LEFT, -1, preview);
        assertEquals(2, given.size());
        UUID first = ItemUtil.getUUIDObj(given.get(0)), second = ItemUtil.getUUIDObj(given.get(1));
        assertNotEquals(first, second);
        assertNotEquals(ItemUtil.getUUIDObj(preview), first);
        for (ItemStack stack : given) {
            assertEquals("angel_boots", ItemUtil.getInternalName(stack));
            assertEquals(100, Utils.getMythicItem0(stack).getLive());
        }
        button.clicked(player, 13, ClickType.RIGHT, -1, preview);
        assertEquals(2, given.size());
        verify(player).sendMessage("这个物品的NBTName为: angel_boots");
        Map<Integer, Button> publicButtons = new AdminItemMenu(false).getButtons(player);
        assertEquals(15, publicButtons.size());
        assertEquals("mythic_leggings", ItemUtil.getInternalName(publicButtons.get(13).getButtonItem(player)));
        assertEquals(Material.DIAMOND_BOOTS,
                PitInternalImpl.INSTANCE.getMythicItemItemStack("angel_boots").getType());
    }

    @Test
    public void damageListenerAppliesTenPercentPerEquippedAngelPiece() {
        CraftPlayer victim = mock(CraftPlayer.class);
        when(victim.getHealth()).thenReturn(20D);
        when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(victim.getInventory()).thenReturn(inventory);
        PlayerProfile profile = mock(PlayerProfile.class, RETURNS_DEEP_STUBS);
        when(profile.getPrestige()).thenReturn(10);
        try (MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class);
             MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class, CALLS_REAL_METHODS)) {
            players.when(() -> PlayerUtil.isNPC(victim)).thenReturn(false);
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(victim.getUniqueId())).thenReturn(profile);
            GameEffectListener listener = new GameEffectListener();
            ItemStack boots = new AngelBoots().toItemStack();
            assertDamage(listener, victim, 10);
            when(inventory.getBoots()).thenReturn(boots);
            assertTrue(PlayerUtil.isEquippingAngelBoots(victim));
            assertDamage(listener, victim, 9);
            ItemStack chestplate = new AngelChestplate().toItemStack();
            when(inventory.getChestplate()).thenReturn(chestplate);
            assertDamage(listener, victim, 8);
            when(inventory.getBoots()).thenReturn(new ItemStack(Material.DIAMOND_BOOTS));
            assertDamage(listener, victim, 9);
            when(inventory.getChestplate()).thenReturn(boots); // The wrong equipment slot grants no angel bonus.
            assertDamage(listener, victim, 10);
            when(inventory.getChestplate()).thenReturn(null);
            ItemStack helmet = new AngelHelmet().toItemStack(), leggings = new AngelLeggings().toItemStack();
            when(inventory.getHelmet()).thenReturn(helmet);
            assertDamage(listener, victim, 9);
            when(inventory.getHelmet()).thenReturn(null);
            when(inventory.getLeggings()).thenReturn(leggings);
            assertDamage(listener, victim, 9);
            when(inventory.getHelmet()).thenReturn(helmet);
            when(inventory.getChestplate()).thenReturn(chestplate);
            when(inventory.getBoots()).thenReturn(boots);
            assertDamage(listener, victim, 6);
            // Putting the new pieces in each other's slots must not grant either bonus.
            when(inventory.getHelmet()).thenReturn(leggings);
            when(inventory.getLeggings()).thenReturn(helmet);
            assertDamage(listener, victim, 8);
        }
    }

    private static void assertDamage(GameEffectListener listener, Player victim, double expected) {
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(mock(Zombie.class), victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10D);
        listener.onPlayerDamagePlayer(hit);
        assertFalse(hit.isCancelled());
        assertEquals(expected, hit.getDamage(), 0.00001);
    }

    @Test
    public void pickingUpDiamondBootsCannotReplaceEquippedAngelBoots() {
        assertPickupKeepsAngelPiece(new AngelBoots());
        assertPickupKeepsAngelPiece(new AngelHelmet());
        assertPickupKeepsAngelPiece(new AngelLeggings());
    }

    private void assertPickupKeepsAngelPiece(AngelChestplate piece) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack equipped = piece.toItemStack();
        Material material = equipped.getType();
        int slot = InventoryUtil.getArmorSlot(material);
        ItemStack[] armor = new ItemStack[4];
        armor[slot] = equipped;
        when(inventory.getArmorContents()).thenReturn(armor);
        org.bukkit.entity.Item dropped = mock(org.bukkit.entity.Item.class);
        ItemStack pickup = new ItemBuilder(material).internalName("shopItem").build();
        when(dropped.getItemStack()).thenReturn(pickup);
        try (MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class);
             MockedStatic<InventoryUtil> inventories = mockStatic(InventoryUtil.class)) {
            inventories.when(() -> InventoryUtil.getArmorSlot(material)).thenReturn(slot);
            PlayerPickupItemEvent event = new PlayerPickupItemEvent(player, dropped, 0);
            new PlayerMarco().onPlayerPickupItem(event);
            assertTrue(event.isCancelled());
            verify(inventory, never()).setArmorContents(any(ItemStack[].class));
            inventories.verify(() -> InventoryUtil.addInvReverse(eq(inventory),
                    argThat(stack -> stack.getType() == material)));
        }
    }

    @Test
    public void newAngelPiecesKeepLifeLoreAndIdentityThroughReformatAndDeath() {
        List<AngelChestplate> pieces = List.of(new AngelHelmet(), new AngelLeggings());
        Material[] materials = {Material.DIAMOND_HELMET, Material.DIAMOND_LEGGINGS};
        String[] names = {"§f天使之盔", "§f天使之裤"};
        List<String> lore = new AngelChestplate().toItemStack().getItemMeta().getLore();
        for (int i = 0; i < pieces.size(); i++) {
            AngelChestplate piece = pieces.get(i);
            ItemStack stack = piece.toItemStack();
            assertEquals(materials[i], stack.getType());
            assertEquals(names[i], stack.getItemMeta().getDisplayName());
            assertEquals(lore, stack.getItemMeta().getLore());
            assertTrue(Utils.needToCheck(piece.getInternalName()));
            assertEquals(100, factory.getIMythicItem(stack).getLive());
            assertEquals(materials[i], PitInternalImpl.INSTANCE.getMythicItemItemStack(piece.getInternalName()).getType());
            piece.setLive(18);
            piece.setCustomName("§d自定义天使装备");
            piece.setForceCanTrade(0);
            ItemStack reformatted = PitInternalImpl.INSTANCE.reformatPitItem(piece.toItemStack());
            IMythicItem loaded = factory.getIMythicItem(reformatted);
            assertEquals(piece.getClass(), loaded.getClass());
            assertEquals(piece.getUuid(), loaded.getUuid());
            assertEquals(piece.getCustomName(), reformatted.getItemMeta().getDisplayName());
            assertEquals(18, loaded.getLive());
            assertEquals(100, loaded.getMaxLive());
            assertEquals(0, loaded.getForceCanTrade());
            ItemStack worn = Utils.subtractLive(reformatted);
            assertEquals(materials[i], worn.getType());
            assertEquals(piece.getUuid(), ItemUtil.getUUIDObj(worn));
            assertEquals(17, Utils.getMythicItem0(worn).getLive());
            loaded.setLive(1);
            assertEquals(Material.AIR, Utils.subtractLive(loaded).getType());
            loaded.setLive(0);
            assertNull(PitInternalImpl.INSTANCE.reformatPitItem(loaded.toItemStack()));
        }
    }

    @Test
    public void sortedMenuRendersAllRowsAndKeepsTheAngelSetTogether() {
        Player player = mock(Player.class);
        try (MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class);
             MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class)) {
            PlayerProfile profile = mock(PlayerProfile.class, RETURNS_DEEP_STUBS);
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
            players.when(() -> PlayerUtil.isPlayerUnlockedPerk(eq(player), anyString())).thenReturn(true);
            AdminItemMenu menu = new AdminItemMenu();
            Map<Integer, Button> buttons = menu.getButtons(player);
            assertEquals(54, menu.size(buttons));
            assertEquals(52, buttons.size()); // Six labels, 46 items; no entry is overwritten by another category.
            Set<String> internalNames = new HashSet<>();
            for (var entry : buttons.entrySet()) {
                assertTrue(entry.getKey() >= 0 && entry.getKey() < 54);
                ItemStack item = entry.getValue().getButtonItem(player);
                assertNotNull("Slot " + entry.getKey(), item);
                assertNotEquals(Material.AIR, item.getType());
                if (entry.getKey() % 9 == 0) {
                    assertEquals(Material.STAINED_GLASS_PANE, item.getType());
                    PlayerInventory inventory = mock(PlayerInventory.class);
                    when(player.getInventory()).thenReturn(inventory);
                    entry.getValue().clicked(player, entry.getKey(), ClickType.LEFT, -1, item);
                    verifyNoInteractions(inventory);
                } else internalNames.add(ItemUtil.getInternalName(item));
            }
            String[] set = {"angel_helmet", "angel_chestplate", "angel_leggings", "angel_boots"};
            Set<UUID> ids = new HashSet<>();
            for (int i = 0; i < set.length; i++) {
                Button button = buttons.get(10 + i);
                for (int j = 0; j < 2; j++) {
                    ItemStack item = button.getButtonItem(player);
                    assertEquals(set[i], ItemUtil.getInternalName(item));
                    assertEquals(100, Utils.getMythicItem0(item).getLive());
                    assertTrue(ids.add(ItemUtil.getUUIDObj(item)));
                }
            }
            assertEquals(12, Utils.getMythicItem0(buttons.get(14).getButtonItem(player)).getLive());
            assertEquals(66, Utils.getMythicItem0(buttons.get(15).getButtonItem(player)).getLive());
            assertEquals(3, Utils.getMythicItem0(buttons.get(16).getButtonItem(player)).getLive());
            assertTrue(internalNames.containsAll(List.of("angel_helmet", "angel_leggings", "angel_boots", "angel_chestplate",
                    "mythic_sword", "mythic_bow", "mythic_leggings", "armageddon_boots", "kings_helmet")));
            assertEquals(Material.OBSIDIAN, buttons.get(50).getButtonItem(player).getType());
            assertEquals(64, buttons.get(50).getButtonItem(player).getAmount());
            assertEquals(Material.GOLD_PICKAXE, buttons.get(24).getButtonItem(player).getType());
        }
    }
}
