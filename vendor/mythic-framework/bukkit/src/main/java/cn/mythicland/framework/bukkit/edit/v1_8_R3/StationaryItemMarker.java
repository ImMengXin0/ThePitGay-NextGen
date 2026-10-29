package cn.mythicland.framework.bukkit.edit.v1_8_R3;

import net.minecraft.server.v1_8_R3.DamageSource;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityItem;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.World;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;

public final class StationaryItemMarker extends EntityItem {
    private StationaryItemMarker(World world, Location position, ItemStack stack) {
        super(world, position.getX(), position.getY(), position.getZ(), CraftItemStack.asNMSCopy(stack));
        this.motX = this.motY = this.motZ = 0;
        this.pickupDelay = Integer.MAX_VALUE;
    }

    public static Item spawn(Location position, ItemStack stack) {
        World world = ((CraftWorld) position.getWorld()).getHandle();
        StationaryItemMarker marker = new StationaryItemMarker(world, position, stack);
        if (!world.addEntity(marker, SpawnReason.CUSTOM)) throw new IllegalStateException("Item marker spawn was rejected");
        return (Item) marker.getBukkitEntity();
    }

    @Override
    public void t_() {
        this.lastX = this.locX;
        this.lastY = this.locY;
        this.lastZ = this.locZ;
    }

    @Override
    public void inactiveTick() {
    }

    @Override
    public void d(EntityHuman player) {
    }

    @Override
    public boolean damageEntity(DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean c(NBTTagCompound compound) {
        return false;
    }

    @Override
    public boolean d(NBTTagCompound compound) {
        return false;
    }
}
