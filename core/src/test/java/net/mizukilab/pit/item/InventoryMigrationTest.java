package net.mizukilab.pit.item;

import cn.charlotte.pit.data.sub.PlayerInv;
import cn.mythicland.framework.bukkit.item.PaperItemData;
import cn.mythicland.framework.bukkit.item.v1_8_R3.InventoryItemCodec;
import net.minecraft.nbt.*;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.Assert.*;

/** Exercises the real Paper data converters, including historical numeric item IDs. */
public class InventoryMigrationTest {
    private CompoundTag legacyTag() {
        CompoundTag tag = new CompoundTag(), extra = new CompoundTag(), display = new CompoundTag();
        extra.putString("uuid", "04b2e921-c385-40da-b671-00364d0d3cc9");
        extra.putString("internal", "mythic_sword");
        extra.putInt("live", 7); extra.putInt("maxLive", 30);
        ListTag enchants = new ListTag(); enchants.add(StringTag.valueOf("billionaire:3"));
        extra.put("ench", enchants); tag.put("extra", extra);
        tag.putString("extension-root", "preserve-me");
        tag.putBoolean("Unbreakable", true);
        display.putString("Name", "Legacy sword");
        ListTag lore = new ListTag(); lore.add(StringTag.valueOf("Legacy lore"));
        display.put("Lore", lore); tag.put("display", display);
        ListTag vanillaEnchants = new ListTag(); CompoundTag sharpness = new CompoundTag();
        sharpness.putShort("id", (short) 16); sharpness.putShort("lvl", (short) 2);
        vanillaEnchants.add(sharpness); tag.put("ench", vanillaEnchants);
        return tag;
    }
    private ItemStack legacyItem() throws IOException {
        CompoundTag root = new CompoundTag();
        root.putShort("id", (short) 283); root.putByte("Count", (byte) 1);
        root.putShort("Damage", (short) 5); root.put("tag", legacyTag());
        ByteArrayOutputStream out = new ByteArrayOutputStream(); NbtIo.writeCompressed(root, out);
        return InventoryUtil.deserializeItemStack(Base64.getEncoder().encodeToString(out.toByteArray()));
    }
    private void assertLegacyData(ItemStack item) {
        assertEquals(Material.GOLDEN_SWORD, item.getType());
        CompoundTag custom = PaperItemData.custom(CraftItemStack.asNMSCopy(item));
        assertEquals(legacyTag().getCompoundOrEmpty("extra"), custom.getCompoundOrEmpty("extra"));
        assertEquals("preserve-me", custom.getStringOr("extension-root", ""));
        assertEquals("Legacy sword", item.getItemMeta().getDisplayName());
        assertEquals(List.of("Legacy lore"), item.getItemMeta().getLore());
        assertTrue(item.getItemMeta().isUnbreakable());
        assertEquals(2, item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS));
        assertEquals(5, ((org.bukkit.inventory.meta.Damageable) item.getItemMeta()).getDamage());
    }
    @Test public void oldGzipItemRetainsIdentityEnchantmentsLifeAndDisplayThroughModernSave() throws Exception {
        ItemStack item = legacyItem(); assertLegacyData(item);
        assertLegacyData(InventoryUtil.deserializeItemStack(InventoryUtil.serializeItemStack(item)));
    }
    @Test public void frameworkVersionOneConvertsNumericItemsAndVersionTwoRetainsAllComponents() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(bytes); header.writeInt(0x50495449); header.writeByte(1);
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(header))) {
            out.writeInt(2); out.writeBoolean(true); out.writeInt(283); out.writeInt(1);
            out.writeShort(5); out.writeBoolean(true); NbtIo.write(legacyTag(), out);
            out.writeBoolean(false);
        }
        ItemStack migrated;
        try (var reader = new InventoryItemCodec.Reader(bytes.toByteArray())) {
            assertEquals(2, reader.length()); migrated = reader.read(); assertLegacyData(migrated);
            assertNull(reader.read()); reader.finish();
        }
        try (var reader = new InventoryItemCodec.Reader(Base64.getDecoder().decode(
                InventoryItemCodec.encode(new ItemStack[]{migrated, null})))) {
            assertLegacyData(reader.read()); assertNull(reader.read()); reader.finish();
        }
    }
    @Test public void fullPlayerInventoryRetainsOffhandAndLegacyThirtySixSlotsStillLoad() throws Exception {
        for (int size : new int[]{36, 41, 43}) {
            PlayerInv inv = new PlayerInv(); ItemStack[] items = new ItemStack[size];
            items[0] = legacyItem(); items[size - 1] = new ItemStack(Material.SHIELD);
            inv.setContents(items); inv.setArmorContents(new ItemStack[]{null, null, null, new ItemStack(Material.DIAMOND_HELMET)});
            PlayerInv loaded = InventoryUtil.playerInventoryFromString(InventoryUtil.playerInvToString(inv));
            assertEquals(size, loaded.getContents().length);
            assertLegacyData(loaded.getContents()[0]);
            assertEquals(Material.SHIELD, loaded.getContents()[size - 1].getType());
            assertEquals(Material.DIAMOND_HELMET, loaded.getArmorContents()[3].getType());
        }
    }
    @Test public void malformedSlotsAndInvalidItemsFailBeforeReturningAnInventory() {
        for (String bad : List.of("0#null;0#null", "2#null", "-1#null", "0#null;", "0#null#null", "0#broken", "missing"))
            assertThrows(bad, IllegalArgumentException.class, () -> InventoryUtil.stringToItems(bad));
        assertThrows(IllegalArgumentException.class, () -> InventoryUtil.playerInventoryFromString("null|null|null"));
        assertThrows(IllegalArgumentException.class, () -> InventoryUtil.playerInventoryFromString("null;null;null;null|0#null"));
    }
    @Test public void truncatedFrameworkPayloadAndTrailingEntriesAreRejected() throws Exception {
        byte[] valid = Base64.getDecoder().decode(InventoryItemCodec.encode(new ItemStack[]{legacyItem()}));
        assertThrows(IOException.class, () -> {
            try (var reader = new InventoryItemCodec.Reader(Arrays.copyOf(valid, valid.length / 2))) {
                reader.read(); reader.finish();
            }
        });
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream header = new DataOutputStream(bytes);
        header.writeInt(0x50495449); header.writeByte(2);
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(header))) { out.writeInt(0); out.writeByte(1); }
        try (var reader = new InventoryItemCodec.Reader(bytes.toByteArray())) { assertThrows(IOException.class, reader::finish); }
    }
    @Test public void legacyArmorIsRestoredAfterPaperClearsMissingSlots() throws Exception {
        var owner = org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        var nms = new net.minecraft.world.entity.player.Inventory(owner, new net.minecraft.world.entity.EntityEquipment());
        var inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryPlayer(nms);
        var player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(player.getInventory()).thenReturn(inventory);
        PlayerInv saved = new PlayerInv(new ItemStack[36],
                new ItemStack[]{null, null, null, new ItemStack(Material.DIAMOND_HELMET)});
        PlayerInv loaded = InventoryUtil.playerInventoryFromString(InventoryUtil.playerInvToString(saved));
        loaded.applyItemToPlayer(player);
        assertEquals(Material.DIAMOND_HELMET, inventory.getHelmet().getType());
        assertTrue(inventory.getItemInOffHand().getType().isAir());
        // 1.21.11 adds body armor and saddle after the existing offhand slot.
        assertEquals(43, inventory.getContents().length);
        inventory.setItem(40, new ItemStack(Material.SHIELD));
        inventory.setItem(41, new ItemStack(Material.ELYTRA));
        inventory.setItem(42, new ItemStack(Material.SADDLE));
        PlayerInv current = InventoryUtil.playerInventoryFromString(
                InventoryUtil.playerInventoryToString(inventory));
        assertEquals(Material.SHIELD, current.getContents()[40].getType());
        assertEquals(Material.ELYTRA, current.getContents()[41].getType());
        assertEquals(Material.SADDLE, current.getContents()[42].getType());
    }
    @Test public void invalidInventoryJsonCannotBecomeAnEmptyProfileInventory() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> mapper.readValue("{\"inv\":\"null;null;null;null|0#broken\"}", PlayerInv.class));
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> mapper.readValue("{}", PlayerInv.class));
    }
}
