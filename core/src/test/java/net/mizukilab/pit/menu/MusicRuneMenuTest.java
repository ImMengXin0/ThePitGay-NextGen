package net.mizukilab.pit.menu;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.sub.EnchantmentRecord;
import cn.charlotte.pit.menu.rune.*;
import cn.charlotte.pit.menu.rune.button.DJEnchantButton;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity;
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.item.ItemUtil;
import net.mizukilab.pit.util.menu.Button;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.*;
import org.mockito.*;

import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MusicRuneMenuTest {
    private MockedStatic<ThePit> pit;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<Utils> utils;
    private MockedStatic<ItemUtil> itemUtil;
    private MockedConstruction<ItemBuilder> builders;
    private EnchantmentFactor factor;
    private Player player;
    private PlayerInventory inventory;
    private final ItemStack pants = new ItemStack(Material.LEATHER_LEGGINGS);
    private final ItemStack rune = new ItemStack(Material.NETHER_STAR, 2);
    private MythicLeggingsItem parsed;

    @Before public void setup() {
        pit = mockStatic(ThePit.class); bukkit = mockStatic(Bukkit.class);
        utils = mockStatic(Utils.class); itemUtil = mockStatic(ItemUtil.class);
        org.bukkit.inventory.ItemFactory factory = mock(org.bukkit.inventory.ItemFactory.class);
        bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
        when(factory.equals(any(), any())).thenReturn(true);
        ThePit plugin = mock(ThePit.class); pit.when(ThePit::getInstance).thenReturn(plugin);
        factor = new EnchantmentFactor(); when(plugin.getEnchantmentFactor()).thenReturn(factor);
        player = mock(Player.class); inventory = mock(PlayerInventory.class); when(player.getInventory()).thenReturn(inventory);
        when(player.getName()).thenReturn("RuneTest"); when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
        when(inventory.getSize()).thenReturn(36); when(inventory.getHeldItemSlot()).thenReturn(0);
        when(inventory.getItem(10)).thenReturn(pants); when(inventory.getItemInHand()).thenReturn(rune);
        parsed = new MythicLeggingsItem(); utils.when(() -> Utils.getMythicItem(pants)).thenReturn(parsed);
        itemUtil.when(() -> ItemUtil.getInternalName(any())).thenAnswer(call -> {
            ItemStack item = call.getArgument(0);
            return item == null ? null : item.getType() == Material.NETHER_STAR ? "musical_rune"
                    : item.getType() == Material.LEATHER_LEGGINGS ? "mythic_leggings" : null;
        });
        builders = mockConstruction(ItemBuilder.class, withSettings().defaultAnswer(RETURNS_SELF), (builder, context) ->
                when(builder.build()).thenReturn(new ItemStack((Material) context.arguments().get(0))));
    }

    private AbstractEnchantment dj(int number) {
        return enchant("dj_" + number, "DJ #" + number);
    }
    private AbstractEnchantment enchant(String nbt, String name) {
        AbstractEnchantment e = mock(AbstractEnchantment.class);
        when(e.getNbtName()).thenReturn(nbt); when(e.getEnchantName()).thenReturn(name);
        when(e.getRarity()).thenReturn(EnchantmentRarity.GENESIS);
        when(e.getUsefulnessLore(anyInt())).thenReturn("&7音乐/s&b歌曲");
        factor.getEnchantmentMap().put(nbt, e); return e;
    }
    private void click(Button button) {
        button.clicked(player, 10, ClickType.LEFT, -1, new ItemStack(Material.JUKEBOX));
    }

    @Test public void registeredDjsAppearWithoutPopulatingLegacyMusicListAndSortNumerically() {
        List<AbstractEnchantment> expected = new ArrayList<>();
        for (int i = 16; i >= 1; i--) expected.add(0, dj(i));
        enchant("unrelated", "普通附魔"); AbstractEnchantment pvz = enchant("dj_pvz_bundle", "DJ #PVZ"); expected.add(pvz);
        assertEquals(expected, MusicRuneOptions.INSTANCE.available());
        Map<Integer, Button> buttons = new SecondChoseRuneMenu(pants, 10).getButtons(player);
        assertEquals(17, buttons.size());
        // Empty hand no longer hides the music options.
        when(player.getItemInHand()).thenReturn(null);
        assertEquals(Material.JUKEBOX, buttons.get(10).getButtonItem(player).getType());
        verify(builders.constructed().get(0)).name("&9DJ #1");
        assertEquals(54, new SecondChoseRuneMenu(pants, 10).getSize());
    }

    @Test public void lateRegistrationReplacementAndRemovalAreReadFromLiveRegistry() {
        SecondChoseRuneMenu menu = new SecondChoseRuneMenu(pants, 10);
        assertTrue(menu.getButtons(player).isEmpty());
        dj(1); assertEquals(1, menu.getButtons(player).size());
        AbstractEnchantment replacement = enchant("dj_1", "DJ #1");
        assertSame(replacement, MusicRuneOptions.INSTANCE.available().get(0));
        when(replacement.getRarity()).thenReturn(EnchantmentRarity.DISABLED);
        assertTrue(menu.getButtons(player).isEmpty());
        factor.getEnchantmentMap().clear(); assertTrue(menu.getButtons(player).isEmpty());
    }

    @Test public void moreThanSevenAndMoreThanOnePageDoNotOverflowSlots() {
        for (int i = 1; i <= 35; i++) dj(i);
        SecondChoseRuneMenu menu = new SecondChoseRuneMenu(pants, 10);
        Map<Integer, Button> first = menu.getButtons(player);
        assertEquals(29, first.size()); assertTrue(first.containsKey(53));
        click(first.get(53));
        Map<Integer, Button> second = menu.getButtons(player);
        assertEquals(8, second.size()); assertTrue(second.containsKey(45));
        assertEquals(Material.JUKEBOX, second.get(10).getButtonItem(player).getType());
        verify(builders.constructed().get(0)).name("&9DJ #29");
        assertTrue(second.keySet().stream().allMatch(slot -> slot >= 0 && slot < 54));
    }

    @Test public void usedRuneOrExistingMusicPantsAreExcludedEvenAfterRegistryRemoval() {
        AbstractEnchantment music = dj(8);
        parsed.getEnchantments().put(music, 1); factor.getEnchantmentMap().clear();
        assertTrue(MusicRuneOptions.INSTANCE.hasMusic(parsed));
        parsed.getEnchantments().clear();
        parsed.getEnchantmentRecords().add(new EnchantmentRecord("player", "MusicRune", 1));
        when(inventory.iterator()).thenAnswer(call -> List.of(pants).listIterator());
        assertTrue(new MusicalRuneMenu().getButtons(player).isEmpty());
        parsed.getEnchantmentRecords().clear();
        assertEquals(1, new MusicalRuneMenu().getButtons(player).size());
    }

    @Test public void successfulClickUsesCurrentImplementationAndConsumesOnlyOneRuneOnce() {
        AbstractEnchantment old = dj(1);
        DJEnchantButton button = new DJEnchantButton(old, pants, 10);
        AbstractEnchantment current = enchant("dj_1", "DJ #1");
        List<EnchantmentRecord> records = new ArrayList<>();
        Object2IntOpenHashMap<AbstractEnchantment> enchants = new Object2IntOpenHashMap<>();
        ItemStack result = new ItemStack(Material.LEATHER_LEGGINGS);
        try (var decoded = mockConstruction(MythicLeggingsItem.class, (item, context) -> {
            when(item.getEnchantments()).thenReturn(enchants);
            when(item.getEnchantmentRecords()).thenReturn(records);
            when(item.toItemStack()).thenReturn(result);
        })) {
            click(button); click(button);
            assertEquals(1, decoded.constructed().size());
            assertSame(current, enchants.keySet().iterator().next()); assertEquals(1, enchants.getInt(current));
            assertEquals("MusicRune", records.get(0).getDescription());
            verify(inventory, times(1)).setItem(10, result);
            verify(inventory, times(1)).setItem(eq(0), argThat(item -> item != null && item.getAmount() == 1 && item.getType() == Material.NETHER_STAR));
            assertEquals(2, rune.getAmount()); // Never modify the captured inventory stack in place.
            verify(player, times(1)).closeInventory();
        }
    }

    @Test public void switchingRuneOrTargetBeforeClickCannotConsumeOrOverwriteItems() {
        DJEnchantButton button = new DJEnchantButton(dj(1), pants, 10);
        when(inventory.getItemInHand()).thenReturn(new ItemStack(Material.DIAMOND));
        click(button); verify(inventory, never()).setItem(anyInt(), any());
        when(inventory.getItemInHand()).thenReturn(rune);
        when(inventory.getItem(10)).thenReturn(new ItemStack(Material.DIAMOND_LEGGINGS));
        click(button); verify(inventory, never()).setItem(anyInt(), any());
        verify(player, never()).closeInventory();
    }

    @Test public void removedMusicOrNonOrdinaryClickCannotApply() {
        DJEnchantButton button = new DJEnchantButton(dj(1), pants, 10);
        button.clicked(player, 10, ClickType.NUMBER_KEY, 0, pants);
        factor.getEnchantmentMap().clear(); click(button);
        verify(inventory, never()).setItem(anyInt(), any());
    }

    @Test public void previouslyUsedTargetIsRecheckedAtApplyTime() {
        DJEnchantButton button = new DJEnchantButton(dj(1), pants, 10);
        try (var decoded = mockConstruction(MythicLeggingsItem.class, (item, context) -> {
            when(item.getEnchantmentRecords()).thenReturn(List.of(new EnchantmentRecord("player", "MusicRune", 1)));
        })) {
            click(button); verify(inventory, never()).setItem(anyInt(), any());
            verify(decoded.constructed().get(0), never()).toItemStack();
        }
    }

    @After public void teardown() {
        builders.close(); itemUtil.close(); utils.close(); pit.close(); bukkit.close();
    }
}
