package net.mizukilab.pit.item.factory;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.enchantment.EnchantmentFactor;
import com.google.common.annotations.Beta;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.mizukilab.pit.item.AbstractPitItem;
import net.mizukilab.pit.item.IItemFactory;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.util.ItemGlobalReference;
import net.mizukilab.pit.util.Log;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.item.ItemUtil;
import nya.Skip;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
@Skip
public class ItemFactory implements IItemFactory {

    public boolean clientSide = false;
    //这里是存玩家的
    ItemGlobalReference theReference = new ItemGlobalReference(() -> Bukkit.getOnlinePlayers().size() * 120L);
    private volatile EnchantmentFactor cachedRegistry;
    private volatile long cachedRevision = Long.MIN_VALUE;

    private void ensureCurrentEnchantments() {
        ThePit plugin = ThePit.getInstance();
        EnchantmentFactor registry = plugin == null ? null : plugin.getEnchantmentFactor();
        if (registry == null) return;
        long revision = registry.getRevision();
        if (cachedRegistry != registry || cachedRevision != revision) {
            synchronized (this) {
                if (cachedRegistry != registry || cachedRevision != revision) {
                    theReference.clear();
                    cachedRegistry = registry;
                    cachedRevision = revision;
                }
            }
        }
    }

    //简易LRU
    public boolean hasItem(UUID uuid) {
        return getItem(uuid) != null;
    }

    @Override
    public void setClientSide(boolean clientSide) {
        this.clientSide = clientSide;
    }

    @Override
    public boolean getClientSide() {
        return clientSide;
    }

    @Override
    public AbstractPitItem getAbstractPitItem(UUID uuid) {
        return getItem(uuid);
    }

    @Override
    public AbstractPitItem getItemFromStack(ItemStack stack) {
        return getIMythicItem(stack);
    }

    public IMythicItem getItem(UUID uuid) {
        ensureCurrentEnchantments();
        IMythicItem cached = theReference.getValue(uuid);
        // The legacy map stores only a 32-bit hash. A collision is a cache miss,
        // never permission to use another item's enchantments or lives.
        return cached != null && uuid.equals(cached.uuid) ? cached : null;
    }

    public void invalidate(ItemStack stack) {
        if (stack == null) return;
        UUID uuid;
        try {
            uuid = ItemUtil.getUUIDObj(stack);
        } catch (IllegalArgumentException malformedUuid) {
            return;
        }
        if (uuid == null) return;
        theReference.remove(uuid);
    }

    Runnable EMPTY_RUNNABLE = () -> {
    };

    public void lru() {
        theReference.executeLRU();
    }

    @Beta
    public IMythicItem getIMythicItem(ItemStack stack) {
        return getIMythicItem(stack, EMPTY_RUNNABLE);
    }

    @Beta
    public IMythicItem getIMythicItem(ItemStack stack, Runnable runnable) {
        return getIMythicItem(stack, runnable,clientSide);
    }
    @Beta
    public IMythicItem getIMythicItem(ItemStack stack,boolean clientSide) {
        return getIMythicItem(stack,EMPTY_RUNNABLE,clientSide);
    }
    @Beta
    public IMythicItem getIMythicItem(ItemStack stack, Runnable runnable,boolean clientSide) {
        NBTTagCompound extra = ItemUtil.getExtra(stack);
        String internalName = ItemUtil.getInternalName0(extra);
        if (internalName == null) {
            return null;
        }
        if(!Utils.needToCheck(internalName)) {
            return null;
        }
        boolean shouldUpdate = false;

        // Read the authoritative UUID, not the derived up/do tags: legacy items
        // can have stale/truncated tags, and migration can change this identity.
        UUID uuid = ItemUtil.getUUIDObj(extra);
        boolean shouldUpdateVer = ItemUtil.shouldUpdateItem(stack);
        if (uuid == null || IMythicItem.getDefUUID().equals(uuid)
                || (shouldUpdateVer && ItemUtil.shouldUpdateUUID())) {
            if (!clientSide) {
                shouldUpdate = true;
                uuid = ItemUtil.randomUUIDItem(stack);
            }
        }
        if(!clientSide && shouldUpdateVer){
            ItemUtil.signVer(stack);
        }
        IMythicItem iMythicItem = uuid == null ? null : getIMythicItem(uuid.hashCode());
        // Copied items and external NBT edits can keep a UUID while changing enchants.
        // A cache hit must describe the actual stack, including an empty enchant list.
        if (iMythicItem != null && (!uuid.equals(iMythicItem.uuid)
                || !internalName.equals(iMythicItem.getInternalName())
                || !iMythicItem.hasSameEnchantments(extra))) iMythicItem = null;


        if (iMythicItem == null || clientSide) { //会导致不掉命bug, 有点厉害
            runnable.run();
            return getIMythicItem0(shouldUpdate,stack, internalName);
        } else {
            runnable.run();
          //  int i = System.identityHashCode(Utils.toNMStackQuick(stack));
            return iMythicItem;
        }

    }

    public Object[] readIMythicItemUUIDAndInternalName(ItemStack stack) {
        Object[] objects = ItemUtil.getInternalNameAndUUID(stack); //提前判断节约0.6%
        if (objects[0] == null || objects[1] == null) {
            return null;
        }
        return objects;
    }

    public IMythicItem getIMythicItemFromUUIDString(String uuidString) {
        return getItem(UUID.fromString(uuidString));
    }

    private IMythicItem getIMythicItem(int hashcode) {
        ensureCurrentEnchantments();
        IMythicItem iMythicItem;
        if (!Bukkit.isPrimaryThread()) { //async get sucks
            iMythicItem = theReference.get(hashcode);
        } else {
            iMythicItem = theReference.getValue(hashcode);
        }
        return iMythicItem;
    }

    public IMythicItem getIMythicItemSync(ItemStack stack) {
        return getIMythicItem(stack);
    }

    /** Legacy uncached rebuild used when an extension replaces enchantment instances.
     * clientSide keeps the source NBT intact, but refreshes the parsed item cache.
     */
    public IMythicItem getIMythicItem0(ItemStack stack, String internalName, boolean clientSide) {
        ensureCurrentEnchantments();
        if (stack == null || internalName == null) return null;
        if (!clientSide && ItemUtil.shouldUpdateItem(stack)) {
            if (ItemUtil.shouldUpdateUUID()) ItemUtil.randomUUIDItem(stack);
            ItemUtil.signVer(stack);
        }
        IMythicItem item = Utils.getMythicItem0(stack, internalName);
        if (item != null && item.uuid != null && !item.uuid.equals(IMythicItem.getDefUUID())) {
            theReference.putValue(item.uuid, item);
        }
        return item;
    }

    public IMythicItem getIMythicItem0(boolean shouldUpdateItem,ItemStack stack, String internalName) {
        ensureCurrentEnchantments();
        IMythicItem mythicItem = Utils.getMythicItem0(stack, internalName);
        if (mythicItem == null) {
            return mythicItem;
        }
        if (mythicItem.uuid == null) {
            return mythicItem;
        }
        boolean sameAsDefault = mythicItem.uuid.equals(IMythicItem.getDefUUID());
        // A UUID format migration must not downgrade or remove legacy enchantments.
        if (sameAsDefault) {
            return mythicItem;
        }
        ItemUtil.checkAndUpdateMagic(stack,mythicItem.uuid);
        theReference.putValue(mythicItem.uuid, mythicItem);
        return mythicItem;
    }

}
