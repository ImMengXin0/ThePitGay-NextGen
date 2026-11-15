package net.mizukilab.pit.listener;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.mizukilab.pit.PaperTestBootstrap;
import net.mizukilab.pit.util.PlayerUtil;
import net.mizukilab.pit.util.item.ItemBuilder;
import net.mizukilab.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftArrow;
import org.bukkit.craftbukkit.entity.CraftItem;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftInventoryPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.*;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Runs Paper 1.21.11's real arrow collision and inventory insertion without loading a world. */
public class ArrowPickupRegressionTest {
    private MockedStatic<Bukkit> bukkit;
    private final PlayerMarco listener = new PlayerMarco();
    private ServerLevel level;
    private ServerPlayer human;
    private CraftPlayer player;
    private net.minecraft.world.entity.player.Inventory inventory;
    private final AtomicInteger pickupEvents = new AtomicInteger();
    private boolean cancelPickup;

    @Before public void setup() throws Exception {
        bukkit = PaperTestBootstrap.mockBukkit();
        level = mock(ServerLevel.class);
        human = mock(ServerPlayer.class);
        when(human.getAbilities()).thenReturn(new Abilities());
        when(human.getOffhandItem()).thenReturn(net.minecraft.world.item.ItemStack.EMPTY);
        inventory = new net.minecraft.world.entity.player.Inventory(human, new EntityEquipment());
        when(human.getInventory()).thenReturn(inventory);
        player = mock(CraftPlayer.class);
        when(player.getHandle()).thenReturn(human);
        when(human.getBukkitEntity()).thenReturn(player);
        when(player.getInventory()).thenReturn(new CraftInventoryPlayer(inventory));
        var plugin = mock(org.bukkit.plugin.java.JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var annotation = PlayerMarco.class.getMethod("onPlayerPickupItem", PlayerPickupItemEvent.class)
                .getAnnotation(org.bukkit.event.EventHandler.class);
        PlayerPickupItemEvent.getHandlerList().register(new org.bukkit.plugin.RegisteredListener(listener,
                (target, event) -> {
                    if (event instanceof PlayerPickupItemEvent pickup) listener.onPlayerPickupItem(pickup);
                }, annotation.priority(), plugin, annotation.ignoreCancelled()));
        var plugins = mock(org.bukkit.plugin.PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        doAnswer(call -> {
            org.bukkit.event.Event event = call.getArgument(0);
            if (event instanceof PlayerPickupArrowEvent pickup) {
                pickupEvents.incrementAndGet();
                if (cancelPickup) pickup.setCancelled(true);
            }
            for (var registered : event.getHandlers().getRegisteredListeners()) registered.callEvent(event);
            return null;
        }).when(plugins).callEvent(any());
    }

    @After public void cleanup() {
        HandlerList.unregisterAll(listener);
        if (bukkit != null) bukkit.close();
    }

    private static ItemStack defaultArrows(int amount) {
        return new ItemBuilder(Material.ARROW).internalName("default_arrow").defaultItem()
                .canDrop(false).canSaveToEnderChest(false).amount(amount).build();
    }

    private final class Projectile {
        final AbstractArrow arrow = mock(AbstractArrow.class);
        final CraftArrow craft = mock(CraftArrow.class);
        final AtomicBoolean removed = new AtomicBoolean();
        Projectile(ItemStack item, AbstractArrow.Pickup status) {
            arrow.pickup = status;
            arrow.pickupItemStack = CraftItemStack.asNMSCopy(item);
            when(arrow.level()).thenReturn(level);
            when(arrow.isInGround()).thenReturn(true);
            when(arrow.getBukkitEntity()).thenReturn(craft);
            when(arrow.getPickupItem()).thenCallRealMethod();
            doCallRealMethod().when(arrow).playerTouch(any());
            doAnswer(call -> {
                removed.set(true);
                return null;
            }).when(arrow).discard(any(org.bukkit.event.entity.EntityRemoveEvent.Cause.class));
            when(craft.getItemStack()).thenAnswer(call -> CraftItemStack.asBukkitCopy(arrow.pickupItemStack));
            doAnswer(call -> {
                arrow.pickupItemStack = CraftItemStack.asNMSCopy((ItemStack) call.getArgument(0));
                return null;
            }).when(craft).setItemStack(any());
        }
        void collide(int times) {
            // Only the temporary item container and packet/world boundaries are simulated.
            // Collision, canHold(), add() and the pickup event are Paper's actual implementations.
            try (var items = mockConstruction(ItemEntity.class, (item, context) -> {
                var stack = new AtomicReference<>((net.minecraft.world.item.ItemStack) context.arguments().get(4));
                var craftItem = mock(CraftItem.class);
                when(item.getItem()).thenAnswer(call -> stack.get());
                when(item.getBukkitEntity()).thenReturn(craftItem);
                when(craftItem.getItemStack()).thenAnswer(call -> CraftItemStack.asBukkitCopy(stack.get()));
                doAnswer(call -> {
                    stack.set(CraftItemStack.asNMSCopy((ItemStack) call.getArgument(0)));
                    return null;
                }).when(craftItem).setItemStack(any());
            })) {
                for (int i = 0; i < times && !removed.get(); i++) arrow.playerTouch(human);
            }
        }
    }

    private int arrowCount() {
        return inventory.getNonEquipmentItems().stream()
                .filter(item -> item.is(net.minecraft.world.item.Items.ARROW))
                .mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
    }

    @Test public void repeatedCollisionsAwardOneArrowAndRemoveTheRealProjectile() {
        var projectile = new Projectile(new ItemStack(Material.ARROW), AbstractArrow.Pickup.ALLOWED);
        projectile.collide(20);
        assertEquals(1, arrowCount());
        assertTrue(projectile.removed.get());
        assertEquals(1, pickupEvents.get());
        assertEquals("default_arrow", ItemUtil.getInternalName(CraftItemStack.asBukkitCopy(inventory.getItem(0))));
        verify(human).take(projectile.arrow, 1);
    }

    @Test public void fullInventoryRetainsArrowThenPicksUpOnceAfterSpaceIsFreed() {
        for (int i = 0; i < 36; i++) inventory.setItem(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64));
        var projectile = new Projectile(defaultArrows(1), AbstractArrow.Pickup.ALLOWED);
        projectile.collide(20);
        assertEquals(0, arrowCount());
        assertEquals(0, pickupEvents.get());
        assertFalse(projectile.removed.get());
        inventory.setItem(2, net.minecraft.world.item.ItemStack.EMPTY);
        projectile.collide(20);
        assertEquals(1, arrowCount());
        assertTrue(projectile.removed.get());
    }

    @Test public void launchNormalizationAllowsPickupIntoTheOnlyPartiallyFullDefaultArrowStack() {
        for (int i = 0; i < 36; i++) inventory.setItem(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64));
        inventory.setItem(3, CraftItemStack.asNMSCopy(defaultArrows(63)));
        var projectile = new Projectile(new ItemStack(Material.ARROW), AbstractArrow.Pickup.ALLOWED);
        listener.onArrowLaunch(new org.bukkit.event.entity.ProjectileLaunchEvent(projectile.craft));
        projectile.collide(20);
        assertEquals(64, arrowCount());
        assertTrue(projectile.removed.get());
    }

    @Test public void cancelledPickupNeitherAwardsArrowsNorRemovesProjectile() {
        cancelPickup = true;
        var projectile = new Projectile(new ItemStack(Material.ARROW), AbstractArrow.Pickup.ALLOWED);
        projectile.collide(20);
        assertEquals(0, arrowCount());
        assertFalse(projectile.removed.get());
    }

    @Test public void staffSpectatorCannotCollectArrows() {
        try (var staff = mockStatic(PlayerUtil.class)) {
            staff.when(() -> PlayerUtil.isStaffSpectating(player)).thenReturn(true);
            var projectile = new Projectile(new ItemStack(Material.ARROW), AbstractArrow.Pickup.ALLOWED);
            projectile.collide(20);
            assertEquals(0, arrowCount());
            assertFalse(projectile.removed.get());
        }
    }

    @Test public void disallowedAndCreativeOnlyArrowsCannotGenerateSurvivalAmmunition() {
        for (var status : new AbstractArrow.Pickup[] {AbstractArrow.Pickup.DISALLOWED, AbstractArrow.Pickup.CREATIVE_ONLY}) {
            var projectile = new Projectile(new ItemStack(Material.ARROW), status);
            listener.onArrowLaunch(new org.bukkit.event.entity.ProjectileLaunchEvent(projectile.craft));
            assertEquals(status, projectile.arrow.pickup);
            projectile.collide(20);
            assertFalse(projectile.removed.get());
        }
        assertEquals(0, arrowCount());
        assertEquals(0, pickupEvents.get());
    }

    @Test public void creativeOnlyPickupRemovesArrowWithoutAddingAmmunition() {
        human.getAbilities().instabuild = true;
        var projectile = new Projectile(new ItemStack(Material.ARROW), AbstractArrow.Pickup.CREATIVE_ONLY);
        projectile.collide(20);
        assertTrue(projectile.removed.get());
        assertEquals(0, arrowCount());
        assertEquals(0, pickupEvents.get());
    }

    @Test public void tippedSpectralAndTridentPickupPreservesOriginalItems() {
        for (var material : new Material[] {Material.TIPPED_ARROW, Material.SPECTRAL_ARROW, Material.TRIDENT}) {
            var original = new ItemBuilder(material).name("special projectile").build();
            inventory.clearContent();
            var projectile = new Projectile(original, AbstractArrow.Pickup.ALLOWED);
            listener.onArrowLaunch(new org.bukkit.event.entity.ProjectileLaunchEvent(projectile.craft));
            projectile.collide(20);
            assertTrue(projectile.removed.get());
            assertEquals(original, CraftItemStack.asBukkitCopy(inventory.getItem(0)));
        }
    }

    @Test public void droppedArrowStacksRetainTheirQuantityIncludingTheUncollectedRemainder() {
        var item = mock(org.bukkit.entity.Item.class);
        var stack = new AtomicReference<>(new ItemStack(Material.ARROW, 4));
        when(item.getItemStack()).thenAnswer(call -> stack.get());
        doAnswer(call -> { stack.set(call.getArgument(0)); return null; }).when(item).setItemStack(any());
        var event = new PlayerPickupItemEvent(player, item, 16);
        listener.onPlayerPickupItem(event);
        assertFalse(event.isCancelled());
        assertEquals(20, stack.get().getAmount());
        assertEquals("default_arrow", ItemUtil.getInternalName(stack.get()));
        verify(item, never()).remove();
        assertEquals(0, arrowCount());
        stack.set(new ItemStack(Material.ARROW, 32));
        listener.onArrowItemSpawn(new org.bukkit.event.entity.ItemSpawnEvent(item, new org.bukkit.Location(null, 0, 0, 0)));
        assertEquals(32, stack.get().getAmount());
        assertEquals("default_arrow", ItemUtil.getInternalName(stack.get()));
    }
}
