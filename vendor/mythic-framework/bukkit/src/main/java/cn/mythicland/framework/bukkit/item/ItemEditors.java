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
    private static final Method MODERN_UNBREAKABLE_SETTER = modernUnbreakableMethod("setUnbreakable", boolean.class);
    private static final Method MODERN_UNBREAKABLE_GETTER = modernUnbreakableMethod("isUnbreakable");

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
        return applyUnbreakableNbt(itemStack, enabled);
    }

    public static boolean isUnbreakable(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        if (itemStack.getType() == Material.AIR) {
            throw new IllegalArgumentException("Cannot read unbreakable state from AIR item");
        }
        Boolean nbtState = readUnbreakableNbt(itemStack);
        if (nbtState != null) return nbtState;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException(
                    "Cannot read unbreakable state from item without meta: " + itemStack.getType());
        }
        Boolean modernState = readModernUnbreakable(meta);
        if (modernState != null) return modernState;
        Boolean spigotState = readSpigotUnbreakable(meta);
        if (spigotState != null) return spigotState;
        throw new IllegalStateException("Cannot read unbreakable state from item: " + itemStack.getType());
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
        if (meta == null) return;
        if (tryModernUnbreakable(meta, enabled)) return;
        trySpigotUnbreakable(meta, enabled);
    }

    private static ItemStack applyUnbreakableNbt(ItemStack itemStack, boolean enabled) {
        if (itemStack == null || itemStack.getType() == Material.AIR) return itemStack;
        try {
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack");
            Class<?> nbtTagCompoundClass = Class.forName("net.minecraft.server.v1_8_R3.NBTTagCompound");
            Object nmsStack = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class).invoke(null, itemStack);
            Method hasTagMethod = nmsStack.getClass().getMethod("hasTag");
            Method getTagMethod = nmsStack.getClass().getMethod("getTag");
            Object rootTag = Boolean.TRUE.equals(hasTagMethod.invoke(nmsStack))
                    ? getTagMethod.invoke(nmsStack)
                    : nbtTagCompoundClass.getConstructor().newInstance();
            nbtTagCompoundClass.getMethod(
                    "setBoolean",
                    String.class,
                    boolean.class
            ).invoke(rootTag, "Unbreakable", enabled);
            nmsStack.getClass().getMethod("setTag", nbtTagCompoundClass).invoke(nmsStack, rootTag);
            return (ItemStack) craftItemStackClass.getMethod("asBukkitCopy", nmsStack.getClass())
                    .invoke(null, nmsStack);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return itemStack;
        }
    }

    private static Boolean readUnbreakableNbt(ItemStack itemStack) {
        try {
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack");
            Object nmsStack = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class).invoke(null, itemStack);
            Method hasTagMethod = nmsStack.getClass().getMethod("hasTag");
            if (!Boolean.TRUE.equals(hasTagMethod.invoke(nmsStack))) return false;
            Object rootTag = nmsStack.getClass().getMethod("getTag").invoke(nmsStack);
            if (rootTag == null) return false;
            Object value = rootTag.getClass().getMethod("getBoolean", String.class).invoke(rootTag, "Unbreakable");
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Method modernUnbreakableMethod(String name, Class<?>... parameters) {
        try {
            return ItemMeta.class.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static boolean tryModernUnbreakable(ItemMeta meta, boolean enabled) {
        try {
            if (MODERN_UNBREAKABLE_SETTER == null) return false;
            MODERN_UNBREAKABLE_SETTER.invoke(meta, enabled);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static Boolean readModernUnbreakable(ItemMeta meta) {
        try {
            if (MODERN_UNBREAKABLE_GETTER == null) return null;
            Object value = MODERN_UNBREAKABLE_GETTER.invoke(meta);
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Boolean readSpigotUnbreakable(ItemMeta meta) {
        try {
            Method spigotMethod = ItemMeta.class.getMethod("spigot");
            Object spigot = spigotMethod.invoke(meta);
            if (spigot == null) return null;
            Method unbreakableMethod = ItemMeta.Spigot.class.getMethod("isUnbreakable");
            Object value = unbreakableMethod.invoke(spigot);
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static void trySpigotUnbreakable(ItemMeta meta, boolean enabled) {
        try {
            Method spigotMethod = ItemMeta.class.getMethod("spigot");
            Object spigot = spigotMethod.invoke(meta);
            if (spigot == null) return;
            Method unbreakableMethod = ItemMeta.Spigot.class.getMethod("setUnbreakable", boolean.class);
            unbreakableMethod.invoke(spigot, enabled);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static void applySkullTexture(SkullMeta meta, String textureValue) {
        if (meta == null || textureValue.isBlank()) return;
        try {
            Object profile = createGameProfile(textureValue);
            if (profile == null) return;
            Field profileField = findProfileField(meta.getClass());
            if (profileField == null) return;
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Field findProfileField(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField("profile");
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object createGameProfile(String textureValue) throws ReflectiveOperationException {
        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
        Constructor<?> gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
        Object profile = gameProfileConstructor.newInstance(
                UUID.nameUUIDFromBytes(textureValue.getBytes(StandardCharsets.UTF_8)),
                null
        );
        Method getPropertiesMethod = gameProfileClass.getMethod("getProperties");
        Object properties = getPropertiesMethod.invoke(profile);
        Constructor<?> propertyConstructor = propertyClass.getConstructor(String.class, String.class);
        Object textureProperty = propertyConstructor.newInstance("textures", textureValue);
        Method putMethod = properties.getClass().getMethod("put", Object.class, Object.class);
        putMethod.invoke(properties, "textures", textureProperty);
        return profile;
    }
}
