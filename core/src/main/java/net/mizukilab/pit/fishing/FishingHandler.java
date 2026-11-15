package net.mizukilab.pit.fishing;

import cn.charlotte.pit.ThePit;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.mizukilab.pit.parm.AutoRegister;
import net.mizukilab.pit.util.PublicUtil;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.random.RandomUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * @Author: EmptyIrony
 * @Date: 2021/2/6 20:12
 */
@AutoRegister
public class FishingHandler implements Listener {

    @EventHandler
    public void onFishing(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            event.setExpToDrop(0);
            event.getCaught().remove();

            Location location = event.getCaught().getLocation();

            ItemStack itemStack = this.generateItem(event.getPlayer(), location);
            ItemEntity entityItem = this.generateEntity(event.getPlayer(), location, itemStack);
            CraftEntity item = entityItem.getBukkitEntity();
            item.setMetadata("gold", new FixedMetadataValue(ThePit.getInstance(), RandomUtil.random.nextInt(5) + 5));

        } else if (event.getState() == PlayerFishEvent.State.FISHING) {
            ItemStack item = event.getPlayer().getItemInHand();

        }
    }


    private ItemEntity generateEntity(Player player, Location dropLocation, ItemStack item) {
        ServerLevel worldServer = ((CraftWorld) dropLocation.getWorld()).getHandle();
        ItemEntity entityItem = new ItemEntity(worldServer, dropLocation.getX(), dropLocation.getY(), dropLocation.getZ(), PublicUtil.toNMStackQuick(item));
        worldServer.addFreshEntity(entityItem);

        double d5 = player.getLocation().getX() - dropLocation.getX();
        double d6 = player.getLocation().getY() - dropLocation.getY();
        double d7 = player.getLocation().getZ() - dropLocation.getZ();
        double d8 = Math.sqrt(d5 * d5 + d6 * d6 + d7 * d7);
        double d9 = 0.1D;

        entityItem.setDeltaMovement(d5 * d9, d6 * d9 + Math.sqrt(d8) * .08D, d7 * d9);

        return entityItem;
    }

    ItemBuilder build = new ItemBuilder(Material.GOLD_INGOT);

    private ItemStack generateItem(Player player, Location location) {

        return build
                .build();
    }

}
