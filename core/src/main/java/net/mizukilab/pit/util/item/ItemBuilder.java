package net.mizukilab.pit.util.item;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/1 1:04
 */

import cn.charlotte.pit.data.sub.EnchantmentRecord;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.*;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.util.PublicUtil;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;

public class ItemBuilder {

    private static int dontStack = 0;
    private ItemStack is;

    public ItemBuilder(Material mat) {
        this.is = new ItemStack(mat);
    }

    public ItemBuilder(ItemStack is) {
        this.is = is;
    }

    public ItemBuilder saved(boolean saved) {
        this.changeNbt("saved", saved);
        return this;
    }

    public ItemBuilder material(Material mat) {
        this.is = new ItemStack(mat);
        return this;
    }
    public ItemBuilder changeNbt(String key, long value) {

        net.minecraft.world.item.ItemStack nmsItem = PublicUtil.toNMStackQuick(is);

        CompoundTag tag = getNbtTagCompound(cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem));

        CompoundTag extra = getNbtTagCompound(tag.getCompoundOrEmpty("extra"));

        extra.putLong(key, value);

        tag.put("extra", extra);
        cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem, tag);

        this.is = CraftItemStack.asBukkitCopy(nmsItem);
        return this;
    }
    public static String formatExactTime(long timestamp) {
        if (timestamp <= 0) {
            return ChatColor.RED + "已过期";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日HH时mm分");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai")); // 使用北京时间

        Date expireDate = new Date(timestamp);
        return ChatColor.GREEN + sdf.format(expireDate);
    }

    public ItemBuilder amount(int amount) {
        this.is.setAmount(amount);
        return this;
    }

    public ItemBuilder name(String name) {
        ItemMeta meta = this.is.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        this.is.setItemMeta(meta);
        return this;
    }

    public ItemBuilder dontStack() {
        dontStack++;
        return changeNbt("uuid", dontStack);
    }

    public ItemBuilder setLetherColor(Color color) {
        LeatherArmorMeta im = (LeatherArmorMeta) is.getItemMeta();
        im.setColor(color);
        is.setItemMeta(im);
        return this;
    }

    public ItemBuilder setSkullOwner(String owner) {
        SkullMeta im = (SkullMeta) is.getItemMeta();
        im.setOwner(owner);
        is.setItemMeta(im);
        return this;
    }

    public ItemBuilder expireTime(long expireTime) {
        return this.changeNbt("expireTime", expireTime);
    }

    public static String formatTime(long ms) {
        if (ms <= 0) {
            return ChatColor.RED + "已过期";
        }
        long sec = ms / 1000;

        long days = ms / (24 * 60 * 60 * 1000);
        ms %= 24 * 60 * 60 * 1000;

        long hours = ms / (60 * 60 * 1000);
        ms %= 60 * 60 * 1000;

        long minutes = ms / (60 * 1000);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天");
        if (hours > 0) sb.append(hours).append("小时");
        if (minutes > 0) sb.append(minutes).append("分钟");
        if (sec > 0) sb.append(sec).append("秒");

        return ChatColor.GREEN + sb.toString();
    }

    public ItemBuilder setSkullProperty(String texture) {
        SkullMeta meta = (SkullMeta) is.getItemMeta();
        var profile = org.bukkit.Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", texture));
        meta.setPlayerProfile(profile);
        is.setItemMeta(meta);
        return this;
    }

    public ItemBuilder lore(String name) {
        ItemMeta meta = this.is.getItemMeta();
        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ObjectArrayList<>(2);
        }

        lore.add(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(lore);
        this.is.setItemMeta(meta);
        return this;
    }

    public ItemBuilder lore(String... lore) {
        List<String> toSet = new ObjectArrayList<>(lore.length);
        ItemMeta meta = this.is.getItemMeta();

        for (String string : lore) {
            toSet.add(ChatColor.translateAlternateColorCodes('&', string));
        }

        meta.setLore(toSet);
        this.is.setItemMeta(meta);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int i) {
        this.is.addUnsafeEnchantment(enchantment, i);
        return this;
    }

    public ItemBuilder customName(String customName) {
        return this.changeNbt("customName", customName);
    }

    public ItemBuilder prefix(String prefix) {
        return this.changeNbt("prefix", prefix);
    }

    public ItemBuilder lore(List<String> lore) {
        List<String> toSet = new ObjectArrayList<>(lore.size());
        ItemMeta meta = this.is.getItemMeta();

        for (String string : lore) {
            toSet.add(ChatColor.translateAlternateColorCodes('&', string));
        }

        meta.setLore(toSet);
        this.is.setItemMeta(meta);
        return this;
    }

    public ItemBuilder durability(int durability) {
        net.mizukilab.pit.platform.paper.LegacyItemData.apply(this.is, durability);
        return this;
    }

    public ItemBuilder jewelSwordKills(int kills) {
        this.changeNbt("killed", kills);
        return this;
    }

    public ItemBuilder makeBoostedByGem(boolean boosted) {
        this.changeNbt("boostedByGem", boosted);
        return this;
    }

    public ItemBuilder makeBoostedByGlobalGem(boolean boosted) {
        this.changeNbt("boostedByGlobalGem", boosted);
        return this;
    }

    public ItemBuilder makeBoostedByBook(boolean boosted) {
        this.changeNbt("boostedByBook", boosted);
        return this;
    }
    //                                nbtTagCompound.getStringOr("name", ""),
    //                                nbtTagCompound.getStringOr("reason", ""),
    //                                nbtTagCompound.getLongOr("timeStamp", 0L)
    public ItemBuilder recordEnchantments(List<EnchantmentRecord> records) {
        ListTag nbtTagList = new ListTag();

        if (records.size() > 5) {
            records = records.subList(records.size() - 5, records.size());
        }
        records.forEach(i -> {
            CompoundTag nbtTagCompound = new CompoundTag();
            nbtTagCompound.putString("name",i.getEnchanter());
            nbtTagCompound.putString("reason",i.getDescription());
            nbtTagCompound.putLong("timeStamp",i.getTimestamp());
            nbtTagList.add(nbtTagCompound);
        });

        this.changeNbtList("records", nbtTagList);

        return this;
    }

    public ItemBuilder enchantment(Enchantment enchantment, int level) {
        this.is.addUnsafeEnchantment(enchantment, level);
        return this;
    }

    public ItemBuilder canReplace(boolean allow) {
        this.changeNbt("canReplace", allow);
        return this;
    }

    public ItemBuilder enchantment(Enchantment enchantment) {
        this.is.addUnsafeEnchantment(enchantment, 1);
        return this;
    }

    public ItemBuilder shiny() {
        return this.enchant(Enchantment.LURE, 1).flags(ItemFlag.values());
    }

    public ItemBuilder flags(ItemFlag... flags) {
        ItemMeta itemMeta = this.is.getItemMeta();
        itemMeta.addItemFlags(flags);
        this.is.setItemMeta(itemMeta);
        return this;
    }

    public ItemBuilder type(Material material) {
        this.is.setType(material);
        return this;
    }

    public ItemBuilder clearLore() {
        ItemMeta meta = this.is.getItemMeta();
        meta.setLore(new ArrayList<>());
        this.is.setItemMeta(meta);
        return this;
    }

    public ItemBuilder clearEnchantments() {

        for (Enchantment e : this.is.getEnchantments().keySet()) {
            this.is.removeEnchantment(e);
        }

        return this;
    }

    public ItemBuilder canDrop(boolean allow) {
        this.changeNbt("tradeAllow", allow);
        return this;
    }

    public ItemBuilder isHealingItem(boolean isHealingItem) {
        this.changeNbt("isHealingItem", isHealingItem);
        return this;
    }

    public ItemBuilder canTrade(boolean allow) {
        this.changeNbt("canTrade", allow);
        return this;
    }

    public ItemBuilder forceCanTrade(boolean allow) {
        this.changeNbt("forceCanTrade", allow);
        return this;
    }

    public ItemBuilder unsetForceCanTrade() {
        this.changeNbt("forceCanTrade", null);
        return this;
    }

    public ItemBuilder defaultItem() {
        this.changeNbt("defaultItem", true);
        return this;
    }

    public ItemBuilder canSaveToEnderChest(boolean allow) {
        this.changeNbt("enderChest", allow);
        return this;
    }

    public ItemBuilder changeNbt(String key, String value) {
        net.minecraft.world.item.ItemStack nmsItem = PublicUtil.toNMStackQuick(is);
        CompoundTag tag = getNbtTagCompound(cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem));
        CompoundTag extra = getNbtTagCompound(tag.getCompoundOrEmpty("extra"));
        if (value == null) {
            extra.remove(key);
        } else {
            extra.putString(key, value);
        }
        tag.put("extra", extra);

        cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem, tag);

        this.is = CraftItemStack.asBukkitCopy(nmsItem);

        return this;
    }
    public ItemBuilder changeNbtList(String key, ListTag value) {
        net.minecraft.world.item.ItemStack nmsItem = PublicUtil.toNMStackQuick(is);
        CompoundTag tag = getNbtTagCompound(cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem));
        CompoundTag extra = getNbtTagCompound(tag.getCompoundOrEmpty("extra"));
        if ("ench".equals(key) && extra.contains(key) && !java.util.Objects.equals(extra.get(key), value)) {
            IMythicItem.clearCache(is);
        }
        if (value == null) {
            extra.remove(key);
        } else {
            extra.put(key,value);
        }
        tag.put("extra", extra);

        cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem, tag);

        this.is = CraftItemStack.asBukkitCopy(nmsItem);

        return this;
    }

    public ItemBuilder changeNbt(String key, boolean value) {
        net.minecraft.world.item.ItemStack nmsItem = PublicUtil.toNMStackQuick(is);
        CompoundTag tag = getNbtTagCompound(cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem));
        CompoundTag extra = getNbtTagCompound(tag.getCompoundOrEmpty("extra"));
        extra.putBoolean(key, value);
        tag.put("extra", extra);

        cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem, tag);

        this.is = CraftItemStack.asBukkitCopy(nmsItem);

        return this;
    }

    public ItemBuilder changeNbt(String key, int value) {
        net.minecraft.world.item.ItemStack nmsItem = PublicUtil.toNMStackQuick(is);
        CompoundTag tag = getNbtTagCompound(cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem));
        CompoundTag extra = getNbtTagCompound(tag.getCompoundOrEmpty("extra"));
        // Addon card redemption writes stats after caching the enchanted template.
        // Invalidate on edits so its inventory refresh cannot restore the old stats.
        // Fresh serialization has no previous field and must keep the live cache.
        if (extra.contains(key) && extra.getIntOr(key, 0) != value
                && ("tier".equals(key) || "live".equals(key) || "maxLive".equals(key))) {
            IMythicItem.clearCache(is);
        }
        extra.putInt(key, value);
        tag.put("extra", extra);

        cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem, tag);

        this.is = CraftItemStack.asBukkitCopy(nmsItem);

        return this;
    }

    public ItemBuilder changeNbt(String key, double value) {
        net.minecraft.world.item.ItemStack nmsItem = PublicUtil.toNMStackQuick(is);
        CompoundTag tag = getNbtTagCompound(cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem));
        CompoundTag extra = getNbtTagCompound(tag.getCompoundOrEmpty("extra"));
        extra.putDouble(key, value);
        tag.put("extra", extra);

        cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem, tag);

        this.is = CraftItemStack.asBukkitCopy(nmsItem);

        return this;
    }

    @NotNull
    private static CompoundTag getNbtTagCompound(CompoundTag tag) {
        CompoundTag extra = tag;
        if (extra == null) {
            extra = new CompoundTag();
        }
        return extra;
    }

    public ItemStack buildWithUnbreakable() {
        is.editMeta(meta -> meta.setUnbreakable(true));
        return is;
    }

    public ItemBuilder internalName(String name) {
        this.changeNbt("internal", name);
        return this;
    }

    public ItemBuilder removeOnJoin(boolean remove) {
        this.changeNbt("removeOnJoin", remove);
        return this;
    }

    public ItemBuilder uuid(UUID uuid) {
        //this.changeNbt("uuid", uuid.toString());
        ItemUtil.setUUIDObj(is,uuid);
        return this;
    }

    public ItemStack build() {
        return is;
    }

    public ItemBuilder deathDrop(boolean drop) {
        this.changeNbt("deathDrop", drop);
        return this;
    }

    public ItemBuilder version(String version) {
        this.changeNbt("version", version);
        return this;
    }

    public ItemBuilder enchant(Map<AbstractEnchantment, Integer> enchant) {
        ListTag nbtTagList = new ListTag();

        for (Map.Entry<AbstractEnchantment, Integer> entry : enchant.entrySet()) {
            StringTag nbtTagString = new StringTag(entry.getKey().getNbtName() + ":" + entry.getValue());
            nbtTagList.add(nbtTagString);
        }
        return changeNbtList("ench", nbtTagList);
    }

    public ItemBuilder maxLive(int live) {
        this.changeNbt("maxLive", live);
        return this;
    }

    public ItemBuilder dyeColor(String color) {
        this.changeNbt("dyeColor", color);
        return this;
    }

    public ItemBuilder tier(int tier) {
        this.changeNbt("tier", tier);
        return this;
    }

    public ItemBuilder live(int live) {
        this.changeNbt("live", live);
        return this;
    }

    public ItemBuilder itemDamage(double damageValue) {
        is.editMeta(meta -> {
            meta.removeAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
                    new org.bukkit.attribute.AttributeModifier(new org.bukkit.NamespacedKey("thepit", "weapon_damage"),
                            damageValue, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                            org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        });
        return this;
    }

    public ItemBuilder addPotionEffect(PotionEffect effect, boolean b) {
        if (this.is.getItemMeta() instanceof PotionMeta) {
            final PotionMeta meta = (PotionMeta) this.is.getItemMeta();
            meta.addCustomEffect(effect, b);
            this.is.setItemMeta(meta);
        }

        return this;
    }
}
