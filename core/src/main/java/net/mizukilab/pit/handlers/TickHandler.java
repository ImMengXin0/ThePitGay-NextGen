package net.mizukilab.pit.handlers;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.perk.AbstractPerk;
import net.mizukilab.pit.enchantment.AbstractEnchantment;
import net.mizukilab.pit.item.IMythicItem;
import net.mizukilab.pit.park.Parker;
import net.mizukilab.pit.parm.listener.ITickTask;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.Utils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.SneakyThrows;
import net.mizukilab.pit.util.menu.Menu;
import net.mizukilab.pit.util.item.ItemUtil;
import nya.Skip;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;

import static net.mizukilab.pit.util.Utils.shouldTick;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/5 0:30
 */
@Skip
public class TickHandler extends BukkitRunnable {
    //@Getter
    //private final static ObjectArrayList<TradeRequest> tradeRequests = new ObjectArrayList<>();

    Map<AbstractEnchantment, ITickTask> enchantTicks = ThePit.getInstance().getEnchantmentFactor().getTickTasks();

    Map<AbstractPerk, ITickTask> ticksPerk = ThePit.getInstance().getPerkFactory().getTickTasks();
    private final Map<String, Long> lastFailures = new HashMap<>();

    private void reportFailure(String context, Player player, Throwable failure) {
        long now = System.currentTimeMillis();
        Long previous = lastFailures.get(context);
        if (previous != null && now - previous < 30000) return;
        lastFailures.entrySet().removeIf(entry -> now - entry.getValue() >= 30000);
        lastFailures.put(context, now);
        ThePit.getInstance().getLogger().log(Level.WARNING, "Tick failure in " + context
                + (player == null ? "" : " for " + player.getUniqueId()), failure);
    }

    private int tick = 0;
    private long enchantmentRevision = Long.MIN_VALUE;
    private static final class EquipmentSnapshots {
        ItemStack hand;
        ItemStack leggings;
    }
    // Main-thread owned; values do not retain profiles after the operator unloads them.
    private final Map<PlayerProfile, EquipmentSnapshots> equipmentSnapshots = new java.util.WeakHashMap<>();
    @SneakyThrows
    @Override
    public void run() {
        long currentTickTime = Utils.toUnsignedInt(tick);
        long revision = ThePit.getInstance().getEnchantmentFactor().getRevision();
        enchantTicks = ThePit.getInstance().getEnchantmentFactor().getTickTasks();
        ticksPerk = ThePit.getInstance().getPerkFactory().getTickTasks();
        boolean enchantmentsChanged = revision != enchantmentRevision;
        enchantmentRevision = revision;
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        for (Player player : onlinePlayers) {
            try {
                PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
                if (profile == null || !profile.isLoaded()) {
                    continue;
                }
                profile.onTick(player);
                if (enchantmentsChanged) {
                    profile.heldItemStack = null;
                    profile.heldItem = null;
                    profile.leggingItemStack = null;
                    profile.leggings = null;
                }
                tickPerks(player, profile, currentTickTime);
                tickItemInHand(player, tickLeggings(player, profile, currentTickTime), profile, currentTickTime);
            } catch (RuntimeException | LinkageError failure) {
                reportFailure("player", player, failure);
            }
        }
        try { ThePit.getInstance().getMapSelector().tick(); }
        catch (RuntimeException | LinkageError failure) { reportFailure("map", null, failure); }
        try { ((Parker)ThePit.getInstance().getParker()).tick(); }
        catch (RuntimeException | LinkageError failure) { reportFailure("parker", null, failure); }
        try {
            if(tick % 20 == 0) tickMenus();
        } finally {
            incTickTime();
        }
    }
    private void incTickTime(){
        tick++;
    }
    private void tickItemInHand(Player player, PlayerInventory inventory, PlayerProfile profile,long tick) {
        ItemStack itemInHand = inventory.getItemInHand();
        if (itemInHand != null) {
            Material type = itemInHand.getType();
            if (type != Material.AIR && type != Material.LEATHER_LEGGINGS && type != Material.PAPER) {
                ItemStack heldItemStack = profile.heldItemStack;
                if (heldItemStack != null) {
                    if (equipmentSnapshots.containsKey(profile) && equipmentSnapshots.get(profile).hand == heldItemStack
                            && (heldItemStack == itemInHand || heldItemStack.isSimilar(itemInHand))) { // Compare detached contents; CraftBukkit returns fresh wrappers.
                        if (profile.heldItem instanceof IMythicItem ii && ii.matchesEnchantmentSource(itemInHand) && !ItemUtil.shouldUpdateItem(itemInHand)) {
                            tickIMythicItem(player, ii,tick);
                        } else {
                            tickItemStackHand(player, profile, itemInHand, tick);
                        }
                    } else {
                        tickItemStackHand(player, profile, itemInHand,tick);
                    }
                } else {
                    tickItemStackHand(player, profile, itemInHand,tick);
                }
            } else {
                profile.heldItem = null;
                profile.heldItemStack = null;
            }
        } else {
            profile.heldItem = null;
            profile.heldItemStack = null;
        }
    }
    private void tickMenus(){
        Menu.currentlyOpenedMenus.forEach((key, value) -> {
            final Player player = Bukkit.getPlayer(key);

            if (player != null) {
                try {
                    if (value.isAutoUpdate() && Menu.find(player, player.getOpenInventory().getTopInventory()) == value) {
                        value.openMenu(player);
                    }
                } catch (RuntimeException | LinkageError failure) {
                    reportFailure("menu " + value.getClass().getName(), player, failure);
                }
            }
        });
    }
    @NotNull
    private PlayerInventory tickLeggings(Player player, PlayerProfile profile,long tick) {
        //裤子
        PlayerInventory inventory = player.getInventory();
        final ItemStack leggings = inventory.getLeggings();
        if (leggings != null) {
            ItemStack leggingItemStack = profile.leggingItemStack;
            if (leggingItemStack != null) {
                if (equipmentSnapshots.containsKey(profile) && equipmentSnapshots.get(profile).leggings == leggingItemStack
                        && (leggingItemStack == leggings || leggingItemStack.isSimilar(leggings))) { // Compare detached contents; CraftBukkit returns fresh wrappers.
                    if (profile.leggings instanceof IMythicItem ii && ii.matchesEnchantmentSource(leggings) && !ItemUtil.shouldUpdateItem(leggings)) {
                        tickIMythicItem(player, ii,tick);
                    } else {
                        tickItemStack(player, profile, leggings, tick);
                    }
                } else {
                    tickItemStack(player, profile, leggings,tick);
                }
            } else {
                tickItemStack(player, profile, leggings,tick);
            }

        } else {
            profile.leggingItemStack = null;
            profile.leggings = null;
        }
        return inventory;
    }

    private void tickItemStack(Player player, PlayerProfile profile, ItemStack leggings,long tick) {
        profile.leggings = handleIMythicItemTickTasks(leggings, player,tick);
        profile.leggingItemStack = leggings.clone();
        equipmentSnapshots.computeIfAbsent(profile, ignored -> new EquipmentSnapshots()).leggings = profile.leggingItemStack;
    }

    private void tickItemStackHand(Player player, PlayerProfile profile, ItemStack leggings,long tick) {
        profile.heldItem = handleIMythicItemTickTasks(leggings, player,tick);
        profile.heldItemStack = leggings.clone();
        equipmentSnapshots.computeIfAbsent(profile, ignored -> new EquipmentSnapshots()).hand = profile.heldItemStack;
    }

    private void tickPerks(Player player, PlayerProfile profile,long tick) {
        Map<String, AbstractPerk> perks = ThePit.getInstance().getPerkFactory().getPerkMap();
        for (PerkData data : profile.getChosePerk().values()) {
            AbstractPerk perk = data.getHandle(perks);
            // Normal purchases equip active perks at level 0; only negative levels are inactive.
            if (perk != null && !perk.isPassive() && data.getLevel() >= 0) {
                tick(ticksPerk.get(perk), data, tick, player);
            }
        }

        for (PerkData data : profile.getUnlockedPerkMap().values()) {
            AbstractPerk perk = data.getHandle(perks);
            if (perk != null && perk.isPassive() && data.getLevel() > 0) {
                tick(ticksPerk.get(perk), data, tick, player);
            }
        }
    }

    private boolean tick(ITickTask task, PerkData entry, long tick, Player player) {
        if (task != null) {
            try {
                int b = task.loopTick(entry.getLevel());
                if (b == PublicUtil.TICK_OFF_MAGIC_CODE) {
                    return false;
                }
                if (shouldTick(tick, b)) {
                    task.handle(entry.getLevel(), player);
                }
            } catch (RuntimeException | LinkageError failure) {
                reportFailure("perk " + task.getClass().getName(), player, failure);
            }
        }
        return false;
    }

    public IMythicItem handleIMythicItemTickTasks(ItemStack stack, Player player,long tick) {

        final IMythicItem imythicItem = Utils.getMythicItem(stack);

        //U can set it on your profile
        tickIMythicItem(player, imythicItem,tick);
        return imythicItem;
    }

    private void tickIMythicItem(Player player, IMythicItem imythicItem,long tick) {
        if (imythicItem != null) {
            for (Object2IntMap.Entry<AbstractEnchantment> entry : imythicItem.getEnchantments().object2IntEntrySet()) {
                final ITickTask task = enchantTicks.get(entry.getKey());
                if (task == null) {
                    continue;
                }

                try {
                    final int level = entry.getIntValue();
                    int b = task.loopTick(level);
                    if(b == PublicUtil.TICK_OFF_MAGIC_CODE){
                        continue;
                    }
                    if (shouldTick(tick,b)) {
                        task.handle(level, player);
                    }
                } catch (RuntimeException | LinkageError failure) {
                    reportFailure("enchantment " + entry.getKey().getNbtName(), player, failure);
                }
            }
        }
    }

}
