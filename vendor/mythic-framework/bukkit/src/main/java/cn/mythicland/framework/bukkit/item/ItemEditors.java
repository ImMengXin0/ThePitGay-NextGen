package cn.mythicland.framework.bukkit.item;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public final class ItemEditors {

    private ItemEditors() {
    }

    public static ItemEditor type(Material material) {
        Objects.requireNonNull(material, "material");
        return itemStack -> itemStack.setType(material);
    }

    public static ItemEditor amount(int amount) {
        return itemStack -> itemStack.setAmount(amount);
    }

    public static ItemEditor durability(int durability) {
        return itemStack -> itemStack.setDurability((short) durability);
    }

    public static ItemEditor name(String displayName) {
        return meta(meta -> meta.setDisplayName(color(displayName)));
    }

    public static ItemEditor clearName() {
        return meta(meta -> meta.setDisplayName(null));
    }

    public static ItemEditor lore(List<String> loreLines) {
        return meta(meta -> meta.setLore(colorLines(loreLines)));
    }

    public static ItemEditor lore(String... loreLines) {
        return lore(asList(loreLines));
    }

    public static ItemEditor appendLore(List<String> loreLines) {
        return meta(meta -> {
            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();
            lore.addAll(colorLines(loreLines));
            meta.setLore(lore);
        });
    }

    public static ItemEditor appendLore(String... loreLines) {
        return appendLore(asList(loreLines));
    }

    public static ItemEditor clearLore() {
        return meta(meta -> meta.setLore(Collections.emptyList()));
    }

    public static ItemEditor enchant(Enchantment enchantment, int level) {
        Objects.requireNonNull(enchantment, "enchantment");
        return itemStack -> itemStack.addUnsafeEnchantment(enchantment, level);
    }

    public static ItemEditor enchants(Map<Enchantment, Integer> enchants) {
        Objects.requireNonNull(enchants, "enchants");
        return itemStack -> enchants.forEach((enchantment, level) -> enchant(enchantment, level).apply(itemStack));
    }

    public static ItemEditor clearEnchantments() {
        return itemStack -> {
            for (Enchantment enchantment : new ArrayList<>(itemStack.getEnchantments().keySet())) {
                itemStack.removeEnchantment(enchantment);
            }
        };
    }

    public static ItemEditor unbreakable(boolean enabled) {
        return itemStack -> applyUnbreakableMeta(itemStack, enabled);
    }

    public static ItemStack applyUnbreakable(ItemStack itemStack, boolean enabled) {
        applyUnbreakableMeta(itemStack, enabled);
        return itemStack;
    }

    public static boolean isUnbreakable(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        if (itemStack.getType() == Material.AIR) {
            throw new IllegalArgumentException("Cannot read unbreakable state from AIR item");
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) throw new IllegalStateException("Item has no metadata: " + itemStack.getType());
        return meta.isUnbreakable();
    }

    public static ItemEditor flags(ItemFlag... flags) {
        Objects.requireNonNull(flags, "flags");
        return meta(meta -> meta.addItemFlags(flags));
    }

    public static ItemEditor leatherColor(Color color) {
        Objects.requireNonNull(color, "color");
        return meta(
                LeatherArmorMeta.class,
                meta -> meta.setColor(color)
        );
    }

    public static ItemEditor skullOwner(String owner) {
        Objects.requireNonNull(owner, "owner");
        return meta(
                SkullMeta.class,
                meta -> meta.setOwner(owner)
        );
    }

    public static ItemEditor skullTexture(String textureValue) {
        Objects.requireNonNull(textureValue, "textureValue");
        return meta(
                SkullMeta.class,
                meta -> applySkullTexture(meta, textureValue)
        );
    }

    public static ItemEditor edit(Consumer<ItemStack> editor) {
        Objects.requireNonNull(editor, "editor");
        return editor::accept;
    }

    public static ItemEditor meta(Consumer<ItemMeta> editor) {
        Objects.requireNonNull(editor, "editor");
        return itemStack -> {
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta == null) return;

            editor.accept(itemMeta);
            itemStack.setItemMeta(itemMeta);
        };
    }

    public static <M extends ItemMeta> ItemEditor meta(Class<M> metaType, Consumer<M> editor) {
        Objects.requireNonNull(metaType, "metaType");
        Objects.requireNonNull(editor, "editor");
        return meta(meta -> {
            if (metaType.isInstance(meta)) {
                editor.accept(metaType.cast(meta));
            }
        });
    }

    private static List<String> colorLines(List<String> loreLines) {
        Objects.requireNonNull(loreLines, "loreLines");
        List<String> lore = new ArrayList<>(loreLines.size());
        for (String loreLine : loreLines) lore.add(color(loreLine));
        return lore;
    }

    private static List<String> asList(String... lines) {
        Objects.requireNonNull(lines, "lines");
        List<String> list = new ArrayList<>(lines.length);
        Collections.addAll(list, lines);
        return list;
    }

    private static String color(String text) {
        return text == null ? null : ChatColor.translateAlternateColorCodes('&', text);
    }

    private static void applyUnbreakableMeta(ItemStack itemStack, boolean enabled) {
        if (itemStack == null || itemStack.getType() == Material.AIR) return;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return;
        applyUnbreakableMeta(meta, enabled);
        itemStack.setItemMeta(meta);
    }

    private static void applyUnbreakableMeta(ItemMeta meta, boolean enabled) {
        if (meta != null) meta.setUnbreakable(enabled);
    }
    private static void applySkullTexture(SkullMeta meta, String textureValue) {
        if (meta == null || textureValue.isBlank()) return;
        var profile = org.bukkit.Bukkit.createProfile(UUID.nameUUIDFromBytes(textureValue.getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", textureValue));
        meta.setPlayerProfile(profile);
    }

}
