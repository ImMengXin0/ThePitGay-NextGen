package net.mizukilab.pit.item;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import net.minecraft.server.v1_8_R3.AttributeModifier;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.type.rage.NewDealEnchant;
import net.mizukilab.pit.item.factory.ItemFactory;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.*;
import org.mockito.MockedStatic;

import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Runs the unmodified Addon card builder and right-click listener against real core NBT. */
public class AddonMagicCardIntegrationTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private URLClassLoader loader;
    private PitInternalHook previousApi;
    private Player player;
    private PlayerInventory inventory;
    private ItemFactory factory;
    private final ItemStack[] contents = new ItemStack[36];
    private final AbstractEnchantment enchant = new NewDealEnchant();

    @BeforeClass
    public static void registerVanillaItems() {
        PrintStream out = System.out, err = System.err;
        try (var attributes = mockConstruction(AttributeModifier.class)) { DispenserRegistry.c(); }
        finally { System.setOut(out); System.setErr(err); }
    }

    @Before
    public void setUp() throws Exception {
        Path server = Path.of(System.getProperty("pitAddonJar"));
        Path addonLoader = Path.of(System.getProperty("pitAddonLoaderJar"));
        assertTrue("Addon server fixture missing", Files.isRegularFile(server));
        assertTrue("Addon loader fixture missing", Files.isRegularFile(addonLoader));
        loader = new URLClassLoader(new URL[]{server.toUri().toURL(), addonLoader.toUri().toURL(),
                Path.of("build/libs/ThePitGay-V.jar").toUri().toURL()}, getClass().getClassLoader());
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        pit = mockStatic(ThePit.class);
        ThePit plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        EnchantmentFactor factor = new EnchantmentFactor();
        factor.registerEnchantment(enchant);
        when(plugin.getEnchantmentFactor()).thenReturn(factor);
        factory = new ItemFactory();
        when(plugin.getItemFactory()).thenReturn(factory);
        previousApi = ThePit.api;
        ThePit.api = mock(PitInternalHook.class);
        pit.when(ThePit::getApi).thenReturn(ThePit.api);
        when(ThePit.api.getWatermark()).thenReturn("test");
        player = mock(Player.class);
        when(player.getName()).thenReturn("CardTester");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInHand()).thenAnswer(call -> contents[0]);
        doAnswer(call -> { contents[0] = call.getArgument(0); return null; })
                .when(inventory).setItemInHand(nullable(ItemStack.class));
        when(inventory.iterator()).thenAnswer(call -> Arrays.asList(contents).listIterator());
        doAnswer(call -> { contents[call.getArgument(0)] = call.getArgument(1); return null; })
                .when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        // Addon's flush calls remove(int), Bukkit's legacy material-id overload.
        doAnswer(call -> {
            int materialId = call.getArgument(0);
            for (int i = 0; i < contents.length; i++)
                if (contents[i] != null && contents[i].getTypeId() == materialId) contents[i] = null;
            return null;
        }).when(inventory).remove(anyInt());
        doAnswer(call -> {
            ItemStack added = call.getArgument(0);
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] == null || contents[i].getType() == Material.AIR) {
                    contents[i] = added;
                    return new HashMap<Integer, ItemStack>();
                }
            }
            return new HashMap<>(java.util.Map.of(0, added));
        }).when(inventory).addItem(any(ItemStack.class));
    }

    @After
    public void close() throws Exception {
        ThePit.api = previousApi;
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
        if (loader != null) loader.close();
    }

    private ItemStack card(String type, int tier, int lives, int maxLives) throws Exception {
        Class<?> cardType = loader.loadClass("cn.apnf.mistral.items.MagicCard$CardType");
        Object selected = Arrays.stream(cardType.getEnumConstants())
                .filter(value -> ((Enum<?>) value).name().equals(type)).findFirst().orElseThrow();
        HashMap<AbstractEnchantment, Integer> enchants = new HashMap<>();
        enchants.put(enchant, 2);
        Class<?> cardClass = loader.loadClass("cn.apnf.mistral.items.MagicCard");
        Object card = cardClass.getConstructor(cardType, int.class, int.class, int.class, HashMap.class)
                .newInstance(selected, tier, lives, maxLives, enchants);
        return (ItemStack) cardClass.getMethod("build").invoke(card);
    }

    private void rightClick(Action action) throws Exception {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(action);
        Class<?> listenerClass = loader.loadClass("cn.apnf.mistral.listeners.MagicCardListener");
        listenerClass.getMethod("onPlayerInteract", PlayerInteractEvent.class)
                .invoke(listenerClass.getConstructor().newInstance(), event);
        verify(event).setCancelled(true);
    }

    private void assertStats(ItemStack stack, int tier, int lives, int maxLives) {
        assertNotNull(stack);
        assertEquals(Integer.valueOf(tier), ItemUtil.getItemIntData(stack, "tier"));
        assertEquals(Integer.valueOf(lives), ItemUtil.getItemIntData(stack, "live"));
        assertEquals(Integer.valueOf(maxLives), ItemUtil.getItemIntData(stack, "maxLive"));
        String roman = switch (tier) { case 1 -> "I"; case 2 -> "II"; default -> "III"; };
        assertTrue(stack.getItemMeta().getDisplayName(),
                ChatColor.stripColor(stack.getItemMeta().getDisplayName()).contains(roman + " 阶"));
        assertTrue(stack.getItemMeta().getLore().stream().map(ChatColor::stripColor)
                .anyMatch(line -> line.contains("生命: " + lives + "/" + maxLives)));
        IMythicItem parsed = Utils.getMythicItem0(stack);
        assertEquals(2, parsed.getEnchantments().getInt(enchant));
        assertEquals(1, parsed.getEnchantmentRecords().size());
        assertNotEquals(IMythicItem.getDefUUID(), parsed.getUuid());
    }

    @Test
    public void allCardTypesKeepTierLivesEnchantmentsAndHistoryAfterRightClickAndReload() throws Exception {
        for (String type : List.of("SWORD", "BOW", "NORMAL", "DARK", "RAGE")) {
            for (int tier = 1; tier <= (type.equals("DARK") ? 2 : 3); tier++) {
                Arrays.fill(contents, null);
                contents[0] = card(type, tier, 7, 22);
                rightClick(tier % 2 == 0 ? Action.RIGHT_CLICK_BLOCK : Action.RIGHT_CLICK_AIR);
                assertNull(contents[0]);
                assertEquals(1, Arrays.stream(contents).filter(java.util.Objects::nonNull).count());
                ItemStack result = contents[1];
                assertStats(result, tier, 7, 22);
                if (type.equals("DARK") || type.equals("RAGE"))
                    assertEquals(type.toLowerCase(), ItemUtil.getItemStringData(result, "mythic_color"));
                IMythicItem reloaded = new ItemFactory().getIMythicItem(
                        InventoryUtil.deserializeItemStack(InventoryUtil.serializeItemStack(result)));
                assertStats(reloaded.toItemStack(), tier, 7, 22);
            }
        }
    }

    @Test
    public void stackedCardsConsumeOnePerClickAndCreateIndependentItems() throws Exception {
        contents[0] = card("RAGE", 3, 0, 18);
        contents[0].setAmount(2);
        rightClick(Action.RIGHT_CLICK_AIR);
        assertEquals(1, contents[0].getAmount());
        assertStats(contents[1], 3, 0, 18);
        UUID first = ItemUtil.getUUIDObj(contents[1]);
        rightClick(Action.RIGHT_CLICK_BLOCK);
        assertNull(contents[0]);
        assertStats(contents[1], 3, 0, 18);
        assertStats(contents[2], 3, 0, 18);
        assertNotEquals(first, ItemUtil.getUUIDObj(contents[2]));
    }

    @Test
    public void writingItemStatsThroughThePublicBuilderInvalidatesOnlyTheEditedItem() {
        MythicSwordItem sword = new MythicSwordItem();
        sword.setUuid(UUID.randomUUID());
        sword.setVersion(PublicUtil.itemVersion);
        sword.setTier(1);
        sword.setLive(4);
        sword.setMaxLive(12);
        sword.getEnchantments().put(enchant, 1);
        ItemStack original = sword.toItemStack();
        IMythicItem cached = factory.getIMythicItem(original);
        MythicSwordItem other = new MythicSwordItem();
        other.setUuid(UUID.randomUUID());
        other.setVersion(PublicUtil.itemVersion);
        IMythicItem otherCached = factory.getIMythicItem(other.toItemStack());
        ItemStack edited = new ItemBuilder(original).tier(3).live(9).maxLive(24).build();
        IMythicItem refreshed = factory.getIMythicItem(edited);
        assertEquals(3, refreshed.getTier());
        assertEquals(9, refreshed.getLive());
        assertEquals(24, refreshed.getMaxLive());
        assertNotSame(cached, refreshed);
        assertSame(otherCached, factory.getItem(otherCached.getUuid()));
        assertSame(refreshed, factory.getIMythicItem(refreshed.toItemStack()));
    }

    @Test
    public void rewritingUnchangedStatsAndDisplayKeepsCachedItemAndCustomName() {
        MythicSwordItem sword = new MythicSwordItem();
        sword.setUuid(UUID.randomUUID());
        sword.setVersion(PublicUtil.itemVersion);
        sword.setTier(2);
        sword.setLive(6);
        sword.setMaxLive(12);
        sword.setCustomName("§b卡牌测试武器");
        sword.getEnchantments().put(enchant, 2);
        IMythicItem cached = factory.getIMythicItem(sword.toItemStack());
        ItemStack rewritten = new ItemBuilder(cached.toItemStack()).tier(2).live(6).maxLive(12).buildWithUnbreakable();
        assertSame(cached, factory.getIMythicItem(rewritten));
        assertEquals(sword.getCustomName(), rewritten.getItemMeta().getDisplayName());
        ItemStack edited = new ItemBuilder(rewritten).live(4).buildWithUnbreakable();
        IMythicItem refreshed = factory.getIMythicItem(edited);
        assertEquals(4, refreshed.getLive());
        assertEquals(12, refreshed.getMaxLive());
        assertEquals(2, refreshed.getTier());
        assertEquals(sword.getCustomName(), refreshed.toItemStack().getItemMeta().getDisplayName());
    }
}
