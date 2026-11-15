package net.mizukilab.pit.util.item;

import cn.charlotte.pit.ThePit;
import net.minecraft.nbt.*;
import net.mizukilab.pit.item.AbstractPitItem;
import net.mizukilab.pit.item.IItemFactory;
import net.mizukilab.pit.util.Log;
import net.mizukilab.pit.util.PublicUtil;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;


/**
 * @Author: EmptyIrony
 * @Date: 2021/1/1 1:37
 */
public class ItemUtil {


    public static String getUUID(ItemStack item) {
        return getItemStringData(item, "uuid");
    }
    public static String getUUID(CompoundTag extra) {
        return getItemStringData0(extra, "uuid");
    }

    public static int getHashCodeForUUID(ItemStack item) {
        CompoundTag extra = getExtra(item);
        return getHashCodeForUUID0(item, extra);

    }

    public static int getHashCodeForUUID0(ItemStack item, CompoundTag extra) {
        if(extra == null) {
            return -1;
        }
        //        tag.putLong("up",PublicUtil.getMostSignificantBits(uuidseq));
        //        tag.putLong("do",PublicUtil.getLeastSignificantBits(uuidseq));
        long up = getLong(extra, "up");
        long down = getLong(extra, "do");
        if(up == 0 || down == 0) {
            UUID uuidObj = getUUIDObj(extra);
            if (uuidObj != null) {
                updateMagic(extra, uuidObj);
                setExtra(item, extra);
                return uuidObj.hashCode();
            } else {
                return -1;
            }
        }
        return PublicUtil.hashTwoLong(up, down);
    }
    public static UUID getUUIDObj(CompoundTag stack) {
        String uuid = getUUID(stack);
        if (uuid == null) return null;
        return UUID.fromString(uuid);
    }
    public static UUID getUUIDObj(ItemStack stack) {
        String uuid = getUUID(stack);
        if (uuid == null) return null;
        return UUID.fromString(uuid);
    }

    public static void setUUIDObj(CompoundTag extra, UUID uuid) {
        setUUID(extra, uuid.toString());
    }
    public static void setUUIDObj(ItemStack stack, UUID uuid) {
        setUUID(stack, uuid.toString());
    }

    public static void setUUID(ItemStack stack, String uuid) {
        CompoundTag extra = getExtra(stack);
        if (extra == null) extra = new CompoundTag();
        setUUID(extra,uuid);
        if (extra != null) setExtra(stack, extra);
    }
    public static void setUUID(CompoundTag compound, String uuid) {
        if (compound != null) {
            compound.putString("uuid", uuid);
            updateMagic(compound,uuid);
        }
    }
    public static void checkAndUpdateMagic(ItemStack stack,UUID uuid){
        CompoundTag extra = getExtra(stack);
        if(extra != null){
            long up = getLong(extra, "up");
            long d0 = getLong(extra, "do");
            //        tag.putLong("up",PublicUtil.getMostSignificantBits(uuidseq));
            //        tag.putLong("do",PublicUtil.getLeastSignificantBits(uuidseq));
            if(up != uuid.getMostSignificantBits() || d0 != uuid.getLeastSignificantBits()) {
                updateMagic(extra,uuid);
                setExtra(stack, extra);
            }
        }
    }
    public static long getLong(CompoundTag tag,String what) {
        return tag.getLongOr(what, 0L);
    }

    public static void updateMagic(CompoundTag tag,String uuidseq) {
        tag.put("up",new LongTag(PublicUtil.getMostSignificantBits(uuidseq)));
        tag.put("do",new LongTag(PublicUtil.getLeastSignificantBits(uuidseq)));

    }
    public static void updateMagic(CompoundTag tag,UUID uuidseq) {
        tag.put("up",new LongTag(uuidseq.getMostSignificantBits()));
        tag.put("do",new LongTag(uuidseq.getLeastSignificantBits()));
    }
    public static void setVer(ItemStack stack, String ver) {
        CompoundTag extra = getExtra(stack);
        if (extra != null) {
            extra.putString("version", ver);
            setExtra(stack, extra);
        }
    }

    public static String getVer(ItemStack stack) {
        CompoundTag extra = getExtra(stack);
        if (extra != null) {
            Tag nbtBase = extra.get("version");
            return nbtBase instanceof StringTag ? ((StringTag) nbtBase).value() : null;
        }
        return null;
    }

    public static boolean shouldUpdateItem(ItemStack stack) {
        String ver = getVer(stack);
        return ver == null || ver.equals("NULL") || !PublicUtil.itemVersion.equals(ver);
    }

    public static UUID randomUUIDItem(ItemStack stack) {
        UUID uuid;

        IItemFactory itemFactory = ThePit.getInstance().getItemFactory();
        if (itemFactory != null) {
            while (true) {
                uuid = UUID.randomUUID();
                AbstractPitItem item = itemFactory.getAbstractPitItem(uuid);
                if (item == null) {
                    break;
                }
            }
        } else {
            uuid = UUID.randomUUID();
        }
        setUUIDObj(stack, uuid); //async? I think never will have bugs on here
        return uuid;
    }

    public static void signVer(ItemStack stack) {
        setVer(stack, PublicUtil.itemVersion);
    }

    public static boolean shouldUpdateUUIDAndItem(ItemStack stack) {
        return shouldUpdateItem(stack) && shouldUpdateUUID();
    }

    public static boolean shouldUpdateUUID() {
        return PublicUtil.itemVersion.endsWith("uuid");
    }

    public static boolean isIllegalItem(ItemStack item) {
        CompoundTag extra = getExtra(item);
        if (extra == null) {
            return true;
        }
        if (extra.contains("expireTime")) {
            return false;
        }
        return !extra.contains("internal") || getInternalName(item).endsWith("_reward");
    }

    public static boolean canDrop(ItemStack item) {
        CompoundTag extra = getExtra(item);
        // Existing scrolls may still carry the old tradeAllow=true flag.
        if (extra == null || "mythic_reel".equals(getInternalName0(extra))) return false;
        return extra.get("tradeAllow") instanceof ByteTag value && value.byteValue() != 0;
    }

    public static boolean isHealingItem(ItemStack item) {
        return getItemBoolData(item, "isHealingItem");
    }

    public static boolean canTrade(ItemStack item) {
        CompoundTag extra = getExtra(item);
        if (extra == null) {

            return false;
        }

        if (forceCanTrade(item)) {
            return true;
        }
        String internalName = getInternalName(item);
        boolean b = extra.contains("canTrade") && extra.getBooleanOr("canTrade", false);
        boolean b1 = (internalName != null && internalName.startsWith("mythic_"));
        return b1 || b;
    }

    public static boolean forceCanTrade(ItemStack item) {
        return getItemBoolData(item, "forceCanTrade");
    }

    public static boolean canSaveEnderChest(ItemStack item) {
        return getItemBoolData(item, "enderChest");
    }

    public static boolean isDefaultItem(ItemStack item) {
        return getItemBoolData(item, "defaultItem");
    }

    public static boolean canReplace(ItemStack item) {
        CompoundTag extra = getExtra(item);
        if (extra == null || !extra.contains("canReplace")) return true;
        Tag value = extra.get("canReplace");
        return !(value instanceof ByteTag) || ((ByteTag) value).byteValue() != 0;
    }

    @Nullable
    public static CompoundTag getExtra(ItemStack item) {
        if (item == null || item.getType().isAir()) { //Faster
            return null;
        }

        net.minecraft.world.item.ItemStack nmsItem = PublicUtil.toNMStackQuick(item);
        CompoundTag tag = cn.mythicland.framework.bukkit.item.PaperItemData.custom(nmsItem);
        if (tag == null) {
            return null;
        }
        if (tag.get("extra") instanceof CompoundTag extra) {
            return extra;
        }
        return null;
    }

    public static void setExtra(ItemStack item, CompoundTag extra) {
        var handle = PublicUtil.toNMStackQuick(item);
        CompoundTag custom = cn.mythicland.framework.bukkit.item.PaperItemData.custom(handle);
        custom.put("extra", extra.copy());
        cn.mythicland.framework.bukkit.item.PaperItemData.custom(handle, custom);
    }

    public static boolean isDeathDrop(ItemStack item) {
        return getItemBoolData(item, "deathDrop");
    }

    public static boolean isRemovedOnJoin(ItemStack item) {
        return getItemBoolData(item, "removeOnJoin");
    }
    public static boolean isMythicItem(ItemStack stack){
        return getVer(stack) != null;
    }
    public static String getInternalName(ItemStack item) {
        return getItemStringData(item, "internal");
    }
    public static boolean isTheItemType(ItemStack type,String hel){
        if(type == null){
            return false;
        }
        String internalName = getInternalName(type);
        if(internalName == null){
            return false;
        }
        return hel.equals(internalName);
    }

    public static Object[] getInternalNameAndUUID(ItemStack stack) {
        CompoundTag extra = getExtra(stack);
        Object[] objects = new Object[2];
        String internal = getInternalName0(extra);
        String uuid = getItemStringData0(extra, "uuid");
        objects[0] = internal;
        objects[1] = uuid;
        return objects;
    }

    public static Integer getItemIntData(ItemStack item, String key) {
        CompoundTag extra = getExtra(item);
        if (extra != null) {
            Tag nbtBase = extra.get(key);
            if (nbtBase instanceof IntTag intData) {
                return intData.intValue();
            }
        }
        return null;
    }

    public static boolean getItemBoolData(ItemStack item, String key) {
        CompoundTag extra = getExtra(item);
        if (extra != null) {
            Tag nbtBase = extra.get(key);
            if (nbtBase instanceof ByteTag byted) {
                return byted.byteValue() != 0;
            }
        }

        return false;
    }

    public static String getItemStringData0(CompoundTag extra, String key) {
        if (extra != null) {
            Tag nbtBase = extra.get(key);
            if (nbtBase instanceof StringTag string) {
                return string.value();
            }
        }
        return null;
    }
    public static String getItemStringData(ItemStack item, String key) {
        return getItemStringData0(getExtra(item), key);
    }

    public static String getInternalName0(CompoundTag extra) {
        return getItemStringData0(extra,"internal");
    }
}
