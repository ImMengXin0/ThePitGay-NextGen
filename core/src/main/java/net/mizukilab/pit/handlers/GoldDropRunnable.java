package net.mizukilab.pit.handlers;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.config.PitWorldConfig;
import net.mizukilab.pit.util.random.RandomUtil;
import nya.Skip;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * @Author: EmptyIrony
 * @Date: 2021/2/2 13:21
 */
@Skip
public class GoldDropRunnable extends BukkitRunnable {

    private record Coin(Item item, long expiresAt) { }
    private final LinkedList<Coin> itemGarbageList = new LinkedList<>();
    private PitWorldConfig previousMap;
    private long tick;
    private long lastWarning = Long.MIN_VALUE;

    @Override
    public void run() {
        tick++;
        ThePit plugin = ThePit.getInstance();
        PitWorldConfig config = plugin.getPitConfig();
        if (config != previousMap) {
            clear();
            previousMap = config;
        }
        Iterator<Coin> iterator = itemGarbageList.iterator();
        while (iterator.hasNext()) {
            Coin coin = iterator.next();
            if (!coin.item().isValid() || tick >= coin.expiresAt()) {
                remove(coin.item());
                iterator.remove();
            }
        }
        for (int i = 0; i < 2; i++) {
            Location location = GoldDropLocationSelector.find(config, RandomUtil.random());
            if (location == null) {
                if (lastWarning == Long.MIN_VALUE || tick - lastWarning >= 60) {
                    plugin.getLogger().warning("金锭未生成：当前地图没有可用落点。请检查 worldName、gold-drop 范围、arenaHighestY 和已加载区块。");
                    lastWarning = tick;
                }
                break;
            }
            Item item = location.getWorld().dropItem(location, new ItemStack(Material.GOLD_INGOT), dropped -> {
                dropped.setVelocity(new Vector());
                dropped.setPickupDelay(10);
                dropped.setMetadata("gold", new FixedMetadataValue(plugin, RandomUtil.random.nextInt(3) + 3));
            });
            if (item.isValid()) itemGarbageList.add(new Coin(item, tick + 20));
        }
    }

    public void clear() {
        itemGarbageList.forEach(coin -> remove(coin.item()));
        itemGarbageList.clear();
    }

    private void remove(Item item) {
        item.removeMetadata("gold", ThePit.getInstance());
        if (item.isValid()) item.remove();
    }
}
