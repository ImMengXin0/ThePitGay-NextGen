package net.mizukilab.pit.item;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerOption;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Abilities;
import net.mizukilab.pit.PaperTestBootstrap;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftInventoryPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.*;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual Paper inventory behavior: empty armor slots return AIR rather than null. */
public class DefaultArmorSupplyTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<PlayerProfile> profiles;
    private CraftInventoryPlayer inventory;
    private Player player;
    private PlayerOption options;

    @Before public void setup() {
        bukkit = PaperTestBootstrap.mockBukkit();
        try (var warehouses = mockConstruction(cn.charlotte.pit.data.sub.PlayerWarehouse.class);
             var enderChests = mockConstruction(cn.charlotte.pit.data.sub.PlayerEnderChest.class)) {
            profiles = mockStatic(PlayerProfile.class);
        }
        var human = mock(ServerPlayer.class);
        when(human.getAbilities()).thenReturn(new Abilities());
        when(human.getOffhandItem()).thenReturn(net.minecraft.world.item.ItemStack.EMPTY);
        var container = new net.minecraft.world.entity.player.Inventory(human, new EntityEquipment());
        when(human.getInventory()).thenReturn(container);
        inventory = new CraftInventoryPlayer(container);
        player = mock(Player.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var profile = mock(PlayerProfile.class);
        options = new PlayerOption();
        when(profile.getPlayerOption()).thenReturn(options);
        when(profile.getChosePerk()).thenReturn(new HashMap<>());
        when(profile.getUnlockedPerkMap()).thenReturn(new HashMap<>());
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).thenReturn(profile);
        inventory.setItem(0, new ItemBuilder(Material.IRON_SWORD).internalName("default_sword").defaultItem()
                .canDrop(false).canSaveToEnderChest(false).buildWithUnbreakable());
        inventory.setItem(1, new ItemBuilder(Material.BOW).internalName("default_bow").defaultItem()
                .canDrop(false).canSaveToEnderChest(false).buildWithUnbreakable());
    }

    @After public void cleanup() {
        if (profiles != null) profiles.close();
        if (bukkit != null) bukkit.close();
    }

    @Test public void repeatedRespawnSupplyEquipsArmorInAirSlotsAndPreservesWeapons() {
        ItemStack sword = inventory.getItem(0).clone(), bow = inventory.getItem(1).clone();
        for (int life = 0; life < 3; life++) {
            inventory.setArmorContents(null);
            assertTrue(inventory.getChestplate().getType().isAir());
            InventoryUtil.supplyItems(player);
            var armor = List.of(inventory.getChestplate(), inventory.getLeggings(), inventory.getBoots());
            assertEquals(1L, armor.stream().filter(item -> item.getType().name().startsWith("IRON_")).count());
            for (var piece : armor) {
                assertEquals("default_armor", ItemUtil.getInternalName(piece));
                assertTrue(ItemUtil.isDefaultItem(piece));
                assertTrue(piece.getItemMeta().isUnbreakable());
            }
            assertTrue(inventory.getHelmet().getType().isAir());
            assertEquals(sword, inventory.getItem(0));
            assertEquals(bow, inventory.getItem(1));
        }
    }

    @Test public void customAndNonreplaceableArmorSurviveSupply() {
        var chest = new ItemBuilder(Material.DIAMOND_CHESTPLATE).internalName("special_chest").buildWithUnbreakable();
        var leggings = new ItemBuilder(Material.LEATHER_LEGGINGS).internalName("special_leggings")
                .defaultItem().canReplace(false).buildWithUnbreakable();
        var helmet = new ItemBuilder(Material.GOLDEN_HELMET).internalName("special_helmet").buildWithUnbreakable();
        inventory.setChestplate(chest);
        inventory.setLeggings(leggings);
        inventory.setHelmet(helmet);
        InventoryUtil.supplyItems(player);
        assertEquals(chest, inventory.getChestplate());
        assertEquals(leggings, inventory.getLeggings());
        assertEquals(helmet, inventory.getHelmet());
        assertEquals("default_armor", ItemUtil.getInternalName(inventory.getBoots()));
    }

    @Test public void disabledOutfitDoesNotReceiveArmor() {
        options.setOutfit(false);
        InventoryUtil.supplyItems(player);
        for (var piece : inventory.getArmorContents()) assertTrue(piece == null || piece.getType().isAir());
    }

    @Test public void invalidArmorIsRemovedFromEquipmentSlotsWithoutDeletingHotbarItems() {
        var sword = inventory.getItem(0).clone();
        var bow = inventory.getItem(1).clone();
        var removed = new ItemBuilder(Material.DIAMOND_HELMET).internalName("removed_helmet").removeOnJoin(true).build();
        inventory.setHelmet(removed);
        InventoryUtil.supplyItems(player);
        assertTrue(inventory.getHelmet().getType().isAir());
        assertEquals(sword, inventory.getItem(0));
        assertEquals(bow, inventory.getItem(1));
        assertEquals("default_armor", ItemUtil.getInternalName(inventory.getChestplate()));
    }

    @Test public void loginSupplyMakesExistingUberDropsStackableWithoutChangingTheirAmounts() {
        var old = new ItemBuilder(new net.mizukilab.pit.item.type.UberDrop().toItemStack()).dontStack().amount(5).build();
        inventory.setItem(7, old);
        InventoryUtil.supplyItems(player);
        assertEquals(5, inventory.getItem(7).getAmount());
        assertTrue(inventory.getItem(7).isSimilar(new net.mizukilab.pit.item.type.UberDrop().toItemStack()));
    }
}
