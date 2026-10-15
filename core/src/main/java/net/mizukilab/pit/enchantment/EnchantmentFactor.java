package net.mizukilab.pit.enchantment;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.mizukilab.pit.compat.enchant.AddonEnchantReplacements;
import net.mizukilab.pit.compat.enchant.EnderPearlEnchant;
import net.mizukilab.pit.parm.listener.*;
import net.mizukilab.pit.util.PublicUtil;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @Author: EmptyIrony
 * @Date: 2020/12/30 21:49
 */
@Getter
@Slf4j
public class EnchantmentFactor {


    private final Collection<AbstractEnchantment> enchantments;
    private final Map<String, AbstractEnchantment> enchantmentMap;

    private final Map<Class<?>, AbstractEnchantment> class2EnchMap;
    private final List<IPlayerDamaged> playerDamageds;
    private final List<IAttackEntity> attackEntities;
    private final List<IItemDamage> iItemDamages;
    @Getter
    private final List<IPlayerBeKilledByEntity> playerBeKilledByEntities;
    @Getter
    private final List<IPlayerKilledEntity> playerKilledEntities;
    private final List<IPlayerRespawn> playerRespawns;
    private final List<IPlayerShootEntity> playerShootEntities;
    private final Map<AbstractEnchantment, ITickTask> tickTasks;
    private final List<IPlayerAssist> playerAssists;
    private final Map<AbstractEnchantment, IActionDisplayEnchant> actionDisplayEnchants;
    private volatile long revision;
    private long classIndexRevision = Long.MIN_VALUE;
    private final EnderPearlEnchant.SharedState addonPearls = new EnderPearlEnchant.SharedState();
    private final HitTickGuard hitTickGuard = new HitTickGuard();

    public EnchantmentFactor() {
        this.enchantmentMap = new EnchantmentRegistryMap(() -> revision++);
        this.class2EnchMap = new Object2ObjectOpenHashMap<>();
        this.enchantments = enchantmentMap.values();
        this.playerDamageds = new ObjectArrayList<>();
        this.iItemDamages = new ObjectArrayList<>();
        this.attackEntities = new ObjectArrayList<>();
        this.playerBeKilledByEntities = new ObjectArrayList<>();
        this.playerKilledEntities = new ObjectArrayList<>();
        this.playerRespawns = new ObjectArrayList<>();
        this.tickTasks = new Object2ObjectOpenHashMap<>();
        this.playerShootEntities = new ObjectArrayList<>();
        this.playerAssists = new ObjectArrayList<>();
        this.actionDisplayEnchants = new Object2ObjectOpenHashMap<>(); //keep sync!!!
    }

    /**
     * 初始化附魔Entry
     * @param classes
     */
    public void init(Collection<Class<? extends AbstractEnchantment>> classes) {
        log.info("Loading enchantments...");
        for (Class<?> clazz : classes) {
            if (AbstractEnchantment.class.isAssignableFrom(clazz)) {
                try {
                    AbstractEnchantment enchantment = (AbstractEnchantment) clazz.getConstructor().newInstance();
                    registerEnchantment(enchantment);
                } catch (Exception e) {
                    log.error("{} exception on install enchantments.", String.valueOf(e));
                }
            }
        }
        log.info("{} enchantments loaded!", enchantmentMap.size());
    }

    public void registerEnchantment(AbstractEnchantment enchantment) {
        AbstractEnchantment previous = enchantmentMap.get(enchantment.getNbtName());
        if (previous == enchantment || AddonEnchantReplacements.replaces(previous, enchantment)) return;
        enchantment = AddonEnchantReplacements.adapt(enchantment, addonPearls);
        if (previous != null) removeEnchantment(previous);
        this.enchantmentMap.put(enchantment.getNbtName(), enchantment);

        class2EnchMap.put(enchantment.getClass(),enchantment);
        PublicUtil.register(enchantment.getClass(), enchantment, playerDamageds, attackEntities, iItemDamages, playerBeKilledByEntities, playerKilledEntities, playerRespawns, playerShootEntities);
        registerTickTask(enchantment.getClass(), enchantment);
    }


    /**
     * 移除附魔
     * @param nbtName
     * @param enchantName
     */
    public void unregister(String nbtName, String enchantName) {
        if (nbtName == null) {
            nbtName = "NULL";
        }
        if (enchantName == null) {
            enchantName = "NULL";
        }
        String finalNbtName = nbtName;
        String finalEnchantName = enchantName;
        int size = this.enchantmentMap.size();
        Iterator<AbstractEnchantment> iterator = this.enchantmentMap
                .values().iterator();
        while (iterator.hasNext()) {
            AbstractEnchantment enchObj = iterator.next();
            String nbt = enchObj.getNbtName();
            boolean b = nbt.equalsIgnoreCase(finalNbtName) || enchObj.getEnchantName().equalsIgnoreCase(finalEnchantName);
            if (b) {
                removeEnchantment(enchObj, iterator);
            }
        }
        log.info("Enchantments {} -> {}", size, enchantmentMap.size());
    }

    public void removeEnchantment(AbstractEnchantment enchObj, Iterator<AbstractEnchantment> iterator) {
        log.info("Removing {}", enchObj.getNbtName());
        removeCallbacks(enchObj);
        iterator.remove();
    }

    public void removeEnchantment(AbstractEnchantment enchObj) {
        AbstractEnchantment current = enchantmentMap.get(enchObj.getNbtName());
        if (AddonEnchantReplacements.replaces(current, enchObj)) enchObj = current;
        log.info("Removing {}", enchObj.getNbtName());
        removeCallbacks(enchObj);
        // An extension shutting down with an old instance must not remove its replacement.
        if (this.enchantmentMap.get(enchObj.getNbtName()) == enchObj) {
            this.enchantmentMap.remove(enchObj.getNbtName());
        }
    }

    private void removeCallbacks(AbstractEnchantment enchObj) {
        if (enchObj instanceof AddonEnchantReplacements.Replacement replacement) replacement.close();
        // Extensions may wrap callbacks while leaving the registered enchantment instance intact.
        // Only an active owner can remove same-name wrappers; a late old-instance removal cannot.
        if (enchantmentMap.get(enchObj.getNbtName()) == enchObj) {
            for (List<?> callbacks : List.of(playerDamageds, attackEntities, iItemDamages,
                    playerBeKilledByEntities, playerKilledEntities, playerRespawns, playerShootEntities, playerAssists)) {
                callbacks.removeIf(callback -> callback instanceof AbstractEnchantment enchantment
                        && enchObj.getNbtName().equals(enchantment.getNbtName()));
            }
        }
        PublicUtil.unregister(enchObj.getClass(), enchObj, playerDamageds, attackEntities, iItemDamages, playerBeKilledByEntities, playerKilledEntities, playerRespawns, playerShootEntities);
        playerAssists.removeIf(callback -> callback == enchObj);
        tickTasks.entrySet().removeIf(entry -> entry.getKey() == enchObj || entry.getValue() == enchObj);
        actionDisplayEnchants.entrySet().removeIf(entry -> entry.getKey() == enchObj || entry.getValue() == enchObj);
        class2EnchMap.values().removeIf(value -> value == enchObj);
    }


    private void registerTickTask(Class<?> clazz, AbstractEnchantment enchantment) {
        if (ITickTask.class.isAssignableFrom(clazz)) {
            tickTasks.put(enchantment, (ITickTask) enchantment);
        }
        if (IPlayerAssist.class.isAssignableFrom(clazz)) {
            playerAssists.add((IPlayerAssist) enchantment);
        }
        if (IActionDisplayEnchant.class.isAssignableFrom(clazz)) {
            actionDisplayEnchants.put(enchantment, (IActionDisplayEnchant) enchantment);
        }
    }
    public AbstractEnchantment getEnchByClass(Class<?> claz){
        return getClass2EnchMap().get(claz);
    }

    public Map<Class<?>, AbstractEnchantment> getClass2EnchMap() {
        if (classIndexRevision != revision) {
            class2EnchMap.clear();
            for (AbstractEnchantment enchantment : enchantments) {
                if (enchantment != null) class2EnchMap.put(enchantment.getClass(), enchantment);
            }
            classIndexRevision = revision;
        }
        return class2EnchMap;
    }

    public Collection<AbstractEnchantment> getEnchantments() {
        return enchantments;
    }

    public Map<String, AbstractEnchantment> getEnchantmentMap() {
        return enchantmentMap;
    }

    public List<IPlayerDamaged> getPlayerDamageds() {
        return playerDamageds;
    }

    public List<IAttackEntity> getAttackEntities() {
        return attackEntities;
    }

    public List<IItemDamage> getIItemDamages() {
        return iItemDamages;
    }

    /** Legacy getter spelling used by compiled extensions. */
    public List<IItemDamage> getiItemDamages() {
        return getIItemDamages();
    }

    public List<IPlayerRespawn> getPlayerRespawns() {
        return playerRespawns;
    }

    public List<IPlayerShootEntity> getPlayerShootEntities() {
        return playerShootEntities;
    }

    public Map<AbstractEnchantment, ITickTask> getTickTasks() {
        return tickTasks;
    }

    public List<IPlayerAssist> getPlayerAssists() {
        return playerAssists;
    }

    public Map<AbstractEnchantment, IActionDisplayEnchant> getActionDisplayEnchants() {
        return actionDisplayEnchants;
    }
}
