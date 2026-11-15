package net.mizukilab.pit.util.inventory;

import net.mizukilab.pit.util.item.ItemKinds;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerInv;
import net.mizukilab.pit.item.UberDropStacking;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.*;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.chat.CC;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * @Author: EmptyIrony
 * @Date: 2020/12/30 23:40
 */
public class InventoryUtil {

    private static final Random random = new Random();

    public static int getInventoryEmptySlots(ItemStack[] itemStacks) {
        int slot = 0;
        for (ItemStack itemStack : itemStacks) {
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                slot++;
            }
        }
        return slot;
    }

    public static int getInventoryFilledSlots(ItemStack[] itemStacks) {
        int slot = 0;
        for (ItemStack itemStack : itemStacks) {
            if (itemStack != null && itemStack.getType() != Material.AIR) {
                slot++;
            }
        }
        return slot;
    }

    public static int getAmountOfItem(Player player, ItemStack item) {
        int amount = 0;
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i) != null && player.getInventory().getItem(i).isSimilar(item)) {
                amount += player.getInventory().getItem(i).getAmount();
            }
        }
        return amount;
    }

    public static int getAmountOfItem(Player player, String internalName) {
        int amount = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = player.getInventory().getItem(i);
            String internalName1 = ItemUtil.getInternalName(itemStack);
            if (itemStack != null && internalName1 != null && internalName1.equals(internalName)) {
                amount += itemStack.getAmount();
            }
        }
        return amount;
    }
    public static boolean removeItem(Player player, ItemStack item, Integer amount) {
        if (getAmountOfItem(player, item) < amount) return false;
        int requirement = amount;
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack item1 = inventory.getItem(i);
            if (item1 != null && item1.isSimilar(item)) {
                if (item1.getAmount() <= requirement) {
                    requirement -= item1.getAmount();
                    inventory.setItem(i, null);
                } else {
                    inventory.setItem(i, new ItemBuilder(item).amount(item1.getAmount() - requirement).build());
                    return true;
                }
            }
        }
        return requirement <= 0;
    }

    public static boolean removeItem(Player player, String internalName, Integer amount) {
        if (getAmountOfItem(player, internalName) < amount) return false;
        int requirement = amount;
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && internalName.equals(ItemUtil.getInternalName(item))) {
                if (item.getAmount() <= requirement) {
                    requirement -= item.getAmount();
                    inventory.setItem(i, null);
                } else {
                    inventory.setItem(i, new ItemBuilder(item).amount(item.getAmount() - requirement).build());
                    return true;
                }
            }
        }
        return requirement <= 0;
    }

    public static void removeItemWithInternalName(Player player, String name) {
        if (name == null) {
            return;
        }
        if (name.equals(ItemUtil.getInternalName(player.getItemOnCursor()))) {
            player.setItemOnCursor(null);
        }

        if (name.equals(ItemUtil.getInternalName(player.getInventory().getHelmet()))) {
            player.getInventory().setHelmet(null);
        }
        if (name.equals(ItemUtil.getInternalName(player.getInventory().getChestplate()))) {
            player.getInventory().setChestplate(null);
        }
        if (name.equals(ItemUtil.getInternalName(player.getInventory().getLeggings()))) {
            player.getInventory().setLeggings(null);
        }
        if (name.equals(ItemUtil.getInternalName(player.getInventory().getBoots()))) {
            player.getInventory().setBoots(null);
        }

        int index = 0;
        final PlayerInventory inventory = player.getInventory();
        final List<ItemStack> contents = new ObjectArrayList<>(inventory.getContents());
        for (ItemStack itemStack : contents) {
            if (name.equals(ItemUtil.getInternalName(itemStack))) {
                inventory.setItem(index, null);
            }

            index++;
        }
    }

    public static ItemStack[] fixInventoryOrder(ItemStack[] source) {
        ItemStack[] fixed = new ItemStack[36];

        System.arraycopy(source, 0, fixed, 27, 9);
        System.arraycopy(source, 9, fixed, 0, 27);

        return fixed;
    }

    public static PlayerInv playerInventoryFromPlayer(Player player) {
        final PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (profile.isTempInvUsing()) {
            return profile.getInventory();
        }

        PlayerInv inv = new PlayerInv();

        inv.setContents(player.getInventory().getContents());
        inv.setArmorContents(player.getInventory().getArmorContents());

        return inv;
    }

    public static String itemsToString(ItemStack[] items) {
        if (items == null) return "null";

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < items.length; ++i) {
            builder.append(i).append("#").append(serializeItemStack(items[i]))
                    .append((i == items.length - 1) ? "" : ";");
        }

        return builder.toString();
    }

    public static ItemStack[] stringToItems(String in) {
        if (in == null || in.equals("unset") || in.equals("null") || in.equals("'null'")) return null;
        return parseSlots(in, 1_048_576);
    }

    private static ItemStack[] parseSlots(String encoded, int maximum) {
        if (encoded.length() > 64 * 1024 * 1024) throw new IllegalArgumentException("Inventory payload exceeds limit");
        if (encoded.isEmpty()) return new ItemStack[0];
        String[] entries = encoded.split(";", -1);
        if (entries.length > maximum) throw new IllegalArgumentException("Too many inventory slots");
        ItemStack[] result = new ItemStack[entries.length];
        boolean[] seen = new boolean[entries.length];
        for (String entry : entries) {
            int separator = entry.indexOf('#');
            if (separator < 1 || separator != entry.lastIndexOf('#'))
                throw new IllegalArgumentException("Invalid inventory entry");
            int slot;
            try { slot = Integer.parseInt(entry.substring(0, separator)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid inventory slot", e); }
            if (slot < 0 || slot >= result.length || seen[slot])
                throw new IllegalArgumentException("Duplicate or out-of-range inventory slot: " + slot);
            seen[slot] = true;
            String item = entry.substring(separator + 1);
            result[slot] = item.isEmpty() ? null : deserializeItemStack(item);
        }
        return result;
    }

    public static String playerInvToString(PlayerInv inv) {
        if (inv == null) return "null";

        StringBuilder builder = new StringBuilder();
        ItemStack[] armor = inv.getArmorContents();
        if (armor == null) {
            armor = new ItemStack[4];
        }

        serializeItemStack(builder, armor);

        if (inv.getContents() == null) {
            inv.setContents(new ItemStack[36]);
        }

        return prependContents(builder, inv.getContents());
    }

    @NotNull
    private static String prependContents(StringBuilder builder, ItemStack[] contents) {
        for (int i = 0; i < contents.length; ++i) {
            builder.append(i).append("#").append(serializeItemStack(contents[i]))
                    .append((i == contents.length - 1) ? "" : ";");
        }

        return builder.toString();
    }

    private static void serializeItemStack(StringBuilder builder, ItemStack[] armor) {
        for (int i = 0; i < armor.length; i++) {
            if (i == 3) {
                if (armor[i] == null) {
                    builder.append(serializeItemStack(new ItemStack(Material.AIR)));
                } else {
                    builder.append(serializeItemStack(armor[3]));
                }
            } else {
                if (armor[i] == null) {
                    builder.append(serializeItemStack(new ItemStack(Material.AIR))).append(";");
                } else {
                    builder.append(serializeItemStack(armor[i])).append(";");
                }
            }
        }

        builder.append("|");
    }

    public static String playerInventoryToString(PlayerInventory inv) {
        StringBuilder builder = new StringBuilder();
        ItemStack[] armor = inv.getArmorContents();

        serializeItemStack(builder, armor);

        return prependContents(builder, inv.getContents());
    }

    public static PlayerInv playerInventoryFromString(String in) {
        if (in == null || in.equals("unset") || in.equals("null") || in.equals("'null'")){
            return null;
        }

        String[] data = in.split("\\|", -1);
        if (data.length != 2) throw new IllegalArgumentException("Invalid player inventory sections");
        String[] entries = data[0].split(";", -1);
        if (entries.length != 4) throw new IllegalArgumentException("Player inventory requires four armor slots");
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < armor.length; i++) armor[i] = deserializeItemStack(entries[i]);
        ItemStack[] contents = parseSlots(data[1], 43);
        if (contents.length != 36 && contents.length != 40 && contents.length != 41 && contents.length != 43)
            throw new IllegalArgumentException("Invalid player inventory size: " + contents.length);
        PlayerInv inv = new PlayerInv();
        inv.setArmorContents(armor);
        inv.setContents(contents);
        return inv;
    }

    public static String inventoryToString(Inventory inv) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < inv.getContents().length; ++i) {
            builder.append(i).append("#").append(serializeItemStack(inv.getContents()[i]));
            if (i != inv.getContents().length - 1) {
                builder.append(";");
            }
        }

        return builder.toString();
    }

    public static Inventory inventoryFromString(String in) {
        ItemStack[] contents = parseSlots(in, 54);
        Inventory inv = Bukkit.createInventory(null, 54);
        inv.setContents(contents);
        return inv;
    }

    public static String serializeItemStack(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().equals(Material.AIR)) return "null";

        return java.util.Base64.getEncoder().encodeToString(itemStack.serializeAsBytes());
    }

    public static ItemStack deserializeItemStack(String itemStackString) {
        if (itemStackString == null || "null".equals(itemStackString)) return null;
        // Paper applies the full item DataFixer chain for pre-DataVersion 1.8 NBT.
        // Propagate malformed data so callers cannot silently replace a saved item with air.
        if (itemStackString.length() > 11_184_812) throw new IllegalArgumentException("Item payload exceeds limit");
        byte[] bytes = java.util.Base64.getDecoder().decode(itemStackString);
        if (bytes.length > 8 * 1024 * 1024) throw new IllegalArgumentException("Item payload exceeds limit");
        ItemStack item;
        try { item = ItemStack.deserializeBytes(bytes); }
        catch (RuntimeException failure) { throw new IllegalArgumentException("Invalid saved item", failure); }
        if (item == null || item.getType().isAir()) throw new IllegalArgumentException("Saved item decoded as air");
        return item;
    }

    public static boolean isInvFull(Player player) {
        for (ItemStack itemStack : player.getInventory()) {
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                return false;
            }
        }
        return true;
    }
    public static boolean isInvFull(Player player,int vl) {
        int vlC = 0;
        for (ItemStack itemStack : player.getInventory()) {
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                vlC++;
                if(vlC > vl) {
                    return false;
                }
            }
        }
        return true;
    }

    private static final ItemStack DEFAULT_SWORD = new ItemBuilder(Material.IRON_SWORD).internalName("default_sword").defaultItem().canDrop(false).canSaveToEnderChest(false).buildWithUnbreakable();
    private static final ItemStack DEFAULT_BOW = new ItemBuilder(Material.BOW).internalName("default_bow").defaultItem().canDrop(false).canSaveToEnderChest(false).buildWithUnbreakable();

    //THERE IS NO SUPPLY ITEMS TO DETECT UNFAIR ITEMS
    public static void supplyItems(Player player) {
        int arrowAmount = 0;
        boolean swordFound = false;
        boolean bowFound = false;
        boolean miner = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId()).getChosePerk().entrySet().stream().anyMatch(entry -> entry.getValue().getPerkInternalName().equals("Miner"));
        int cobblestone = 0;
        PlayerProfile playerProfile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());

        int slot = 0;
        int illegalItems = 0;
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory) {
            if (isEmpty(item)) {
                slot++;
                continue;
            }
            ItemStack normalized = UberDropStacking.normalize(item);
            if (normalized != item) {
                item = normalized;
                inventory.setItem(slot, item);
            }
            if (ItemUtil.isIllegalItem(item)) {
                player.getInventory().remove(item);
                illegalItems++;
                slot++;
                continue;
            }

            //fixme: why remove the default item (sword & bow slot will reset)

            if (ItemUtil.isDefaultItem(item) && ItemUtil.canReplace(item)) {
                //player.getInventory().remove(item);
                net.minecraft.world.item.ItemStack nmStackQuick = PublicUtil.toNMStackQuick(item);
                if (ItemKinds.isArmor(nmStackQuick)) {
                    inventory.remove(item);
                }
                if (ItemKinds.isSword(nmStackQuick) && swordFound) {
                    inventory.remove(item);
                    swordFound = false;
                }
                if (ItemKinds.isBow(nmStackQuick) && bowFound) {
                    inventory.remove(item);
                    swordFound = false;
                }
            }


            if (ItemUtil.isRemovedOnJoin(item)) {
                inventory.remove(item);
                slot++;
                continue;
            }

            if ("default_sword".equals(ItemUtil.getInternalName(item))) {
                swordFound = true;
            } else if ("default_bow".equals(ItemUtil.getInternalName(item))) {
                bowFound = true;
            } else if (item.getType() == Material.ARROW) {
                arrowAmount += item.getAmount();
            }
            if (item.getType() == Material.COBBLESTONE) {
                cobblestone += item.getAmount();
            }
            slot++;
        }


        if (miner) {
            if (cobblestone < 32) {
                inventory.addItem(new ItemBuilder(Material.COBBLESTONE).deathDrop(true).amount(32 - cobblestone).canDrop(false).canSaveToEnderChest(false).internalName("perk_miner").build());
            }
        }
        for (EquipmentSlot armorSlot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armorPiece = inventory.getItem(armorSlot);
            if (isEmpty(armorPiece)) {
                continue;
            }
            boolean isIllegal = ItemUtil.isIllegalItem(armorPiece);
            boolean isRemoved = ItemUtil.isRemovedOnJoin(armorPiece);

            if (isIllegal || isRemoved) {
                inventory.setItem(armorSlot, null);
                if (isIllegal) {
                    illegalItems++;
                }
            }
        }

        if (illegalItems > 0) {
            player.sendMessage(CC.translate("&c今从君之囊中寻得 &e" + illegalItems + "&c 异物，已悉数除却，恕罪"));
        }
        if (playerProfile.getPlayerOption().isOutfit()) {
            if (ItemUtil.isDefaultItem(inventory.getHelmet()) && ItemUtil.canReplace(inventory.getHelmet())) {
                inventory.setHelmet(new ItemStack(Material.AIR));
            }
            if (ItemUtil.isDefaultItem(inventory.getChestplate()) && ItemUtil.canReplace(inventory.getChestplate())) {
                inventory.setChestplate(new ItemStack(Material.AIR));
            }
            if (ItemUtil.isDefaultItem(inventory.getLeggings()) && ItemUtil.canReplace(inventory.getLeggings())) {
                inventory.setLeggings(new ItemStack(Material.AIR));
            }
            if (ItemUtil.isDefaultItem(inventory.getBoots()) && ItemUtil.canReplace(inventory.getBoots())) {
                inventory.setBoots(new ItemStack(Material.AIR));
            }

            if (!swordFound) {
                inventory
                        .addItem(DEFAULT_SWORD);
            }
            if (!bowFound) {
                inventory
                        .addItem(DEFAULT_BOW);
            }
        }
        /*
        player.getInventory()
                .remove(Material.ARROW);
        InventoryUtil.addInvReverse(player.getInventory(),
                (new ItemBuilder(Material.ARROW).internalName("default_arrow").defaultItem().canDrop(false).canSaveToEnderChest(false).amount(32).build()));

         */

        int maxArrow = 32 + Math.max(0, PlayerUtil.getPlayerUnlockedPerkLevel(player, "arrow_armory_perk") * 8);
        if (arrowAmount > 0 && arrowAmount <= maxArrow) {
            if (arrowAmount != maxArrow) {
                ItemBuilder arrowBuilder = new ItemBuilder(Material.ARROW).internalName("default_arrow").defaultItem().canDrop(false).canSaveToEnderChest(false);

                inventory.addItem(
                        arrowBuilder.amount(maxArrow - arrowAmount).build()
                );
            }
        } else {
            if (arrowAmount > maxArrow) {
                inventory.remove(Material.ARROW);
            }
            ItemBuilder arrowBuilder = new ItemBuilder(Material.ARROW).internalName("default_arrow").defaultItem().canDrop(false).canSaveToEnderChest(false);
            InventoryUtil.addInvReverse(inventory,
                    (arrowBuilder.amount(maxArrow).build()));
        }

        if (playerProfile.getPlayerOption().isOutfit()) {
            int ironArmorSlot = random.nextInt(3);

            if (isEmpty(inventory.getChestplate()) || (ItemUtil.isDefaultItem(inventory.getChestplate()) && ItemUtil.canReplace(inventory.getChestplate()))) {
                if (ironArmorSlot == 0) {
                    inventory.setChestplate(new ItemBuilder(Material.IRON_CHESTPLATE).defaultItem().internalName("default_armor").canDrop(false).canSaveToEnderChest(true).buildWithUnbreakable());
                } else {
                    inventory.setChestplate(new ItemBuilder(Material.CHAINMAIL_CHESTPLATE).defaultItem().internalName("default_armor").canDrop(false).canSaveToEnderChest(true).buildWithUnbreakable());
                }
            }
            if (isEmpty(inventory.getLeggings()) || (ItemUtil.isDefaultItem(inventory.getLeggings()) && ItemUtil.canReplace(inventory.getLeggings()))) {
                if (ironArmorSlot == 1) {
                    inventory.setLeggings(new ItemBuilder(Material.IRON_LEGGINGS).defaultItem().internalName("default_armor").canDrop(false).canSaveToEnderChest(true).buildWithUnbreakable());
                } else {
                    inventory.setLeggings(new ItemBuilder(Material.CHAINMAIL_LEGGINGS).defaultItem().internalName("default_armor").canDrop(false).canSaveToEnderChest(true).buildWithUnbreakable());
                }
            }
            if (isEmpty(inventory.getBoots()) || (ItemUtil.isDefaultItem(inventory.getBoots()) && ItemUtil.canReplace(inventory.getBoots()))) {
                if (ironArmorSlot == 2) {
                    inventory.setBoots(new ItemBuilder(Material.IRON_BOOTS).defaultItem().internalName("default_armor").canDrop(false).canSaveToEnderChest(true).buildWithUnbreakable());
                } else {
                    inventory.setBoots(new ItemBuilder(Material.CHAINMAIL_BOOTS).defaultItem().internalName("default_armor").canDrop(false).canSaveToEnderChest(true).buildWithUnbreakable());
                }
            }
        }
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    public static int getArmorSlot(Material material) {
        String name = material.name();
        if (name.contains("HELMET")) {
            return 3;
        }
        if (name.contains("CHESTPLATE")) {
            return 2;
        }
        if (name.contains("LEGGINGS")) {
            return 1;
        }
        if (name.contains("BOOTS")) {
            return 0;
        }
        return -1;
    }

    private static int firstPartial(PlayerInventory playerInv, ItemStack item) {
        ItemStack[] inventory = playerInv.getContents();
        ItemStack filteredItem = CraftItemStack.asCraftCopy(item);
        for (int i = 35; i > -1; i--) {
            ItemStack cItem = inventory[i];
            if (cItem != null && cItem.getAmount() < cItem.getMaxStackSize() && cItem.isSimilar(filteredItem)) {
                return i;
            }
        }
        return -1;
    }

    private static int firstEmpty(PlayerInventory playerInv) {
        ItemStack[] inventory = playerInv.getContents();
        for (int i = 35; i > -1; i--) {
            if (inventory[i] == null) {
                return i;
            }
        }
        return -1;
    }

    public static void addInvReverse(PlayerInventory inventory, ItemStack item) {
        while (true) {
            // Do we already have a stack of it?
            int firstPartial = firstPartial(inventory, item);

            // Drat! no partial stack
            if (firstPartial == -1) {
                // Find a free spot!
                int firstFree = firstEmpty(inventory);

                if (firstFree == -1) {
                    break;
                } else {
                    // More than a single stack!
                    if (item.getAmount() > inventory.getMaxStackSize()) {
                        CraftItemStack stack = CraftItemStack.asCraftCopy(item);
                        stack.setAmount(inventory.getMaxStackSize());
                        inventory.setItem(firstFree, stack);
                        item.setAmount(item.getAmount() - inventory.getMaxStackSize());
                    } else {
                        // Just store it
                        inventory.setItem(firstFree, item);
                        break;
                    }
                }
            } else {
                // So, apparently it might only partially fit, well lets do just that
                ItemStack partialItem = inventory.getItem(firstPartial);

                int amount = item.getAmount();
                int partialAmount = partialItem.getAmount();
                int maxAmount = partialItem.getMaxStackSize();

                // Check if it fully fits
                if (amount + partialAmount <= maxAmount) {
                    partialItem.setAmount(amount + partialAmount);
                    // To make sure the packet is sent to the client
                    inventory.setItem(firstPartial, partialItem);
                    break;
                }

                // It fits partially
                partialItem.setAmount(maxAmount);
                // To make sure the packet is sent to the client
                inventory.setItem(firstPartial, partialItem);
                item.setAmount(amount + partialAmount - maxAmount);
            }
        }
    }

    public static boolean isInvFull(PlayerInventory inventory) {
        for (ItemStack itemStack : inventory) {
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                return false;
            }
        }
        return true;
    }
}
