package net.mizukilab.pit.enchantment;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.EnchantmentRecord;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.mizukilab.pit.config.NewConfiguration;
import net.mizukilab.pit.data.operator.SuPromise;
import net.mizukilab.pit.enchantment.info.EnchantRequest;
import net.mizukilab.pit.enchantment.param.item.*;
import net.mizukilab.pit.enchantment.rarity.EnchantmentRarity;
import net.mizukilab.pit.enchantment.type.dark_normal.SomberEnchant;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.item.type.mythic.MagicFishingRod;
import net.mizukilab.pit.item.type.mythic.MythicBowItem;
import net.mizukilab.pit.item.type.mythic.MythicLeggingsItem;
import net.mizukilab.pit.item.type.mythic.MythicSwordItem;
import net.mizukilab.pit.util.Einstein;
import net.mizukilab.pit.util.LzScheduler;
import net.mizukilab.pit.util.Utils;
import net.mizukilab.pit.util.inventory.InventoryUtil;
import net.mizukilab.pit.util.item.ItemUtil;
import net.mizukilab.pit.util.exception.IllegalEnchantInputException;
import net.mizukilab.pit.util.functions.Func3;
import net.mizukilab.pit.util.random.RandomUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import static java.lang.Math.*;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class EnchantmentTable {
    EnchantmentFactor factor;
    private long loadedRevision = -1;
    Object2ObjectOpenHashMap<EnchantmentRarity, ObjectArrayList<AbstractEnchantment>> rodEnchant = new Object2ObjectOpenHashMap<>();
    Object2ObjectOpenHashMap<EnchantmentRarity, ObjectArrayList<AbstractEnchantment>> itemEnchant = new Object2ObjectOpenHashMap<>();
    Object2ObjectOpenHashMap<EnchantmentRarity, ObjectArrayList<AbstractEnchantment>> swordEnchant = new Object2ObjectOpenHashMap<>();
    Object2ObjectOpenHashMap<EnchantmentRarity, ObjectArrayList<AbstractEnchantment>> bowEnchant = new Object2ObjectOpenHashMap<>();
    Object2ObjectOpenHashMap<EnchantmentRarity, ObjectArrayList<AbstractEnchantment>> armorEnchant = new Object2ObjectOpenHashMap<>();
    Object2ObjectOpenHashMap<EnchantmentRarity, ObjectArrayList<AbstractEnchantment>> enchantments = new Object2ObjectOpenHashMap<>();

    public EnchantmentTable(EnchantmentFactor factor) {
        this.factor = factor;
        reload();
    }

    public void reload() {
        rodEnchant.clear();
        itemEnchant.clear();
        swordEnchant.clear();
        bowEnchant.clear();
        armorEnchant.clear();
        enchantments.clear();
        factor.getEnchantments().forEach(i -> {
            enchantments.computeIfAbsent(i.getRarity(), b -> new ObjectArrayList<>()).add(i);
            if (i.getClass().isAnnotationPresent(ArmorOnly.class)) {
                armorEnchant.computeIfAbsent(i.getRarity(), b -> new ObjectArrayList<>()).add(i);
            }
            if (i.getClass().isAnnotationPresent(WeaponOnly.class)) {
                swordEnchant.computeIfAbsent(i.getRarity(), b -> new ObjectArrayList<>()).add(i);
            }
            if (i.getClass().isAnnotationPresent(BowOnly.class)) {
                bowEnchant.computeIfAbsent(i.getRarity(), b -> new ObjectArrayList<>()).add(i);
            }
            if (i.getClass().isAnnotationPresent(RodOnly.class)) {
                rodEnchant.computeIfAbsent(i.getRarity(), b -> new ObjectArrayList<>()).add(i);
            }
            if (i.getClass().isAnnotationPresent(ItemOnly.class)) {
                itemEnchant.computeIfAbsent(i.getRarity(), b -> new ObjectArrayList<>()).add(i);
            }
        });
        loadedRevision = factor.getRevision();
    }

    /** Compatibility entry point: enchantments now complete on the server thread. */
    @Deprecated
    public void asyncTick() {
    }

    public SuPromise<EnchantRequest> enchantItem(Player player, PlayerProfile profile, IMythicItem item, boolean hasBook) {
        SuPromise<EnchantRequest> runnables = new SuPromise<>();
        LzScheduler.ensureMain(() -> {
            // Rebuild once after a batch of extension changes, including config reloads.
            if (loadedRevision != factor.getRevision()) reload();
            synchronized (profile) {
                EnchantRequest request = new EnchantRequest(player, profile, item, hasBook, runnables);
                if (!player.isOnline() || !profile.isLoaded() || profile.disallow() != profile) {
                    request.fail();
                    return;
                }
                String originalItem = profile.getEnchantingItem();
                String originalBook = profile.getEnchantingBook();
                try {
                    // Work on an independent object, never partially modify the item cache.
                    IMythicItem working = Utils.getMythicItem0(item.toItemStack().clone());
                    if (working == null) throw new IllegalEnchantInputException("Unsupported mythic item");
                    request = new EnchantRequest(player, profile, working, hasBook, runnables);
                    Tier.ofTierUnsigned(working).doEnchant(this, request);
                } catch (Throwable failure) {
                    profile.setEnchantingItem(originalItem);
                    profile.setEnchantingBook(originalBook);
                    ThePit.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Enchantment failed for " + player.getUniqueId(), failure);
                    request.fail();
                } finally {
                    profile.allow();
                }
            }
        });
        return runnables;
    }
    @Log4j2
    @Getter
    @AllArgsConstructor
    enum Tier {
        TIER_1(1,
                (chance, enchMap, mythicItem) -> {
                    if (mythicItem.isDark()) { // 黑裤 必出Somber
                        var enchByClass = ThePit.getInstance().getEnchantmentFactor().getEnchByClass(SomberEnchant.class);
                        mythicItem.getEnchantments().put(enchByClass, 1);
                        return false;
                    } else if (mythicItem.isRage()) {
                        var rareRage = enchMap.get(EnchantmentRarity.RAGE_RARE);
                        var rage = enchMap.get(EnchantmentRarity.RAGE);
                        AbstractEnchantment abstractEnchantment = RandomUtil.randEnch(chance, rareRage, rage);
                        int level = RandomUtil.rand(2,1);
                        mythicItem.getEnchantments().put(abstractEnchantment, level);
                        return abstractEnchantment.getRarity().getParentType() == EnchantmentRarity.RarityType.RARE;
                    } else {
                        var normal = enchMap.get(EnchantmentRarity.NORMAL);
                        var rare = enchMap.get(EnchantmentRarity.RARE);
                        return RandomUtil.randEnchMultipleApplyRNPreferStEEmp(2, chance, 1, 2, mythicItem.getEnchantments()
                                        , normal, rare, 0.9, (a, i) -> i.getMaxEnchantLevel() > a)
                                .stream().anyMatch(i -> i.getRarity().getParentType() == EnchantmentRarity.RarityType.RARE);
                    }
                },
                (chance, enchMap, mythicItem) -> {
                    if (mythicItem.isRage() || mythicItem.isDark()) {
                        throw new IllegalEnchantInputException("Can't enchant R/D mythicItem with Mythic Book");
                    }
                    var abstractEnchantments = enchMap.get(EnchantmentRarity.RARE);
                    int remaining = 8 - Einstein.sum(mythicItem.getEnchantments());
                    if (remaining <= 0) throw new IllegalEnchantInputException("Item has reached the enchantment level limit");
                    RandomUtil.chooseAndApplyChecked(true, abstractEnchantments, mythicItem.getEnchantments(), 3, 3,
                            enchant -> Math.min(enchant.getMaxEnchantLevel(), Math.max(0, mythicItem.getEnchantments().getInt(enchant)) + remaining));
                    return true;
                }
                , (item) -> {
            if (item.isDark()) {
                item.maxLive = RandomUtil.helpMeToChooseOneInt(20, 25, 30, 35);
            } else {
                item.maxLive = RandomUtil.rand(10, 3);
            }
        }),
        TIER_2(2,
                (chance, enchMap, mythicItem) -> {
                    if (mythicItem.isDark()) {
                        var enchantments1 = mythicItem.getEnchantments();
                        var rage = enchMap.get(EnchantmentRarity.DARK_RARE);
                        var normal = enchMap.get(EnchantmentRarity.DARK_NORMAL);
                        var abstractEnchantment = RandomUtil.chooseAndApplyMultipleTypeRN(chance, true, enchantments1, 1, normal, rage);
                        return abstractEnchantment.getRarity() == EnchantmentRarity.DARK_RARE;
                    }
                    var enchantments1 = mythicItem.getEnchantments();
                    var rare = enchMap.get(EnchantmentRarity.RARE);
                    var normal = enchMap.get(EnchantmentRarity.NORMAL);
                    int count = Einstein.sum(enchantments1);
                    if (mythicItem.isRage()) {
                        chance = 0D;
                    }
                    int newEnchants = 0;
                    int remain = 8 - count;
                    if (remain <= 0) throw new IllegalEnchantInputException("Item has reached the enchantment level limit");
                    int ops = min(2, remain);
                    int upBound = ops;
                        /*
                          Warning, these enchant logics are obeying rules as follows
                          1. T1 -> 1
                          { T2 -> 21 or 3 }
                          2. T1 -> 2
                          { T2 -> 3 or 21 }
                          3. T1 -> 11
                          { T2 -> 21 or 111 }
                          4. T1 -> ANY-ANY-ANY (ADMIN ENCHANT)
                          { T2 -> ANY+1-ANY+1-ANY+1 }
                          5. T1 -> 4
                          { T2 -> 41 }
                         */
                    int enchants = enchantments1.size();
                    if (enchants == 1) {
                        newEnchants = 1;
                        if (count >= 3) { // == 2, 直接升一级
                            newEnchants = -1;
                            ops = min(1, remain);
                        } else if(count == 2){
                            newEnchants =  0;
                            ops = min(1, remain);
                        }
                    } else if (enchants == 2) {
                        ops = min(1, remain);
                        // 11 check
                        if (count >= 6) {
                            newEnchants = -RandomUtil.rand(2, 0);
                        } else if (count >= 2) {
                            newEnchants = RandomUtil.rand(2, 0); //111 12
                        }
                    } else { //111 check;
                        newEnchants = 2;
                    }
                    int clampi = Einstein.clampi(ops, 0, upBound);

                    var result = RandomUtil.randEnchMultipleApplySofRNPreferStE(3, newEnchants, chance, clampi, clampi, enchantments1, normal, rare, 0.5
                            , (a, i) -> i.getMaxEnchantLevel() > a);
                    return result.stream().anyMatch(i -> i.getRarity().getParentType() == EnchantmentRarity.RarityType.RARE);
                },
                (chance, enchMap, mythicItem) -> TIER_1.useBook.invoke(chance, enchMap, mythicItem),
                (item) -> {
                    if (item.isDark()) {
                        if (RandomUtil.hasSuccessfullyByChance(0.01)) {
                            item.maxLive = 135;
                        } else {
                            item.maxLive = RandomUtil.helpMeToChooseOneInt(40, 45, 50, 55, 60);
                        }
                    } else  {
                        item.maxLive = RandomUtil.rand(16, 11);
                    }
                }),
        TIER_3(3,
                (chance, enchMap, mythicItem) -> {
                    if (mythicItem.isDark()) {
                        var enchantments1 = mythicItem.getEnchantments();
                        var rage = enchMap.get(EnchantmentRarity.DARK_RARE);
                        var normal = enchMap.get(EnchantmentRarity.DARK_NORMAL);
                        return RandomUtil.chooseAndApplyMultipleTypeRN(chance, true, enchantments1, 1, normal, rage).getRarity() == EnchantmentRarity.DARK_RARE;
                    }
                    var enchantments1 = mythicItem.getEnchantments();
                    var rare = enchMap.get(EnchantmentRarity.RARE);
                    var normal = enchMap.get(EnchantmentRarity.NORMAL);
                    int count = Einstein.sum(enchantments1);
                    //8最大属性
                    int size = enchantments1.size();
                    if (count >= 8) throw new IllegalEnchantInputException("Item has reached the enchantment level limit");
                    int clampi = 8 - count;
                    int min = RandomUtil.rand(clampi,Math.min(3,8 - count));
                    if(mythicItem.isRage()){
                        chance = 0D;
                    }
                    return RandomUtil.randEnchMultipleApplySofRNPreferStE(3,(size <= 2) ? -6 : clampi + 1,chance, min, clampi, enchantments1,normal,rare,0.5,(a, i) -> i.getMaxEnchantLevel() > a)
                            .stream().anyMatch(i -> i.getRarity().getParentType() == EnchantmentRarity.RarityType.RARE);
                },
                (chance, enchMap, mythicItem) -> TIER_1.useBook.invoke(chance, enchMap, mythicItem),
                (item) -> {
                    if (!item.isDark()) {
                        if (RandomUtil.hasSuccessfullyByChance(0.01)) { //Artifact Prefix -> 100 Lives
                            item.setMaxLive(100);
                        } else {
                            item.setMaxLive(RandomUtil.rand(23, 21)); //16-23
                        }
                    }
                });
        final int level;
        @Setter
        Func3<Double, Object2ObjectOpenHashMap<EnchantmentRarity, ObjectArrayList<AbstractEnchantment>>, IMythicItem, Boolean> normal;
        @Setter
        Func3<Double, Object2ObjectOpenHashMap<EnchantmentRarity, ObjectArrayList<AbstractEnchantment>>, IMythicItem, Boolean> useBook;
        @Setter
        Consumer<IMythicItem> post;

        public static Tier ofTier(IMythicItem item) {
            int tier = item.getTier();
            Tier[] values = values();
            return values[tier - 1];
        }
        public static Tier ofTierUnsigned(IMythicItem item) {
            int tier = item.getTier();
            Tier[] values = values();
            return values[tier];
        }


        public void doEnchant(EnchantmentTable enchTable, EnchantRequest enq) {
            PlayerProfile profile = enq.getProfile();
            IMythicItem item = enq.getMythic();
            String enchantingBook = profile.getEnchantingBook();
            var level = getEnchantMap(enchTable, item);
            if (level == null) {
                throw new IllegalEnchantInputException("Unsupported item type");
            }
            item.tier++;
            double chance = NewConfiguration.INSTANCE.getChance(enq.getPlayer(), item.getColor(), item.getTier());
            boolean usingBook = enq.isUseBook() && item.canUseBook() && enchantingBook != null;
            String remainingBook = enchantingBook;
            if (usingBook) {
                ItemStack books = InventoryUtil.deserializeItemStack(enchantingBook);
                if (books == null || books.getAmount() <= 0 || !"mythic_reel".equals(ItemUtil.getInternalName(books))) {
                    throw new IllegalEnchantInputException("Invalid enchantment scroll");
                }
                if (books.getAmount() == 1) {
                    remainingBook = null;
                } else {
                    books.setAmount(books.getAmount() - 1);
                    remainingBook = InventoryUtil.serializeItemStack(books);
                    if (remainingBook == null) throw new IllegalEnchantInputException("Failed to serialize remaining scrolls");
                }
            }
            boolean announce = usingBook ? useBook.invoke(chance, level, item) : normal.invoke(chance, level, item);
            if (usingBook) item.boostedByBook = true;
            int lastMaxLive = item.maxLive;
            post.accept(item);
            item.live = item.tier > 1
                    ? Einstein.clampi(item.live + item.maxLive - lastMaxLive, 0, item.maxLive)
                    : item.maxLive;
            item.getEnchantmentRecords().add(new EnchantmentRecord(enq.getPlayer().getName(),
                    String.format("EnchantmentTable UseBook %s Tier %s Live %s", usingBook, item.tier, item.live), System.currentTimeMillis()));
            ItemStack result = item.toItemStack();
            String encoded = InventoryUtil.serializeItemStack(result);
            if (encoded == null) throw new IllegalEnchantInputException("Failed to serialize enchanted item");
            profile.setEnchantingItem(encoded);
            if (usingBook) profile.setEnchantingBook(remainingBook);
            // The transaction uses an independent item with the original UUID.
            // Extensions must not rebuild the committed item from its pre-enchant cache.
            IMythicItem.clearCache(result);
            enq.complete(item, usingBook, announce);
        }

        @Nullable
        private Object2ObjectOpenHashMap<EnchantmentRarity, ObjectArrayList<AbstractEnchantment>> getEnchantMap(EnchantmentTable enchTable, IMythicItem item) {
            Object2ObjectOpenHashMap<EnchantmentRarity, ObjectArrayList<AbstractEnchantment>> level = null;
            if (item instanceof MythicBowItem) {
                level = enchTable.bowEnchant;
            }
            if (item instanceof MythicSwordItem) {
                level = enchTable.swordEnchant;
            }
            if (item instanceof MythicLeggingsItem) {
                level = enchTable.armorEnchant;
            }
            if (item instanceof MagicFishingRod) {
                level = enchTable.rodEnchant;
            }
            return level;
        }
    }
}
