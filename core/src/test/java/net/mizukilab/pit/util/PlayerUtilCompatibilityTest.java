package net.mizukilab.pit.util;

import net.mizukilab.pit.event.CanImmuneEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PlayerUtilCompatibilityTest {
    // The 1.8 server JAR also contains legacy int-returning health methods.
    // Redeclare the current API so Mockito selects the double-returning methods.
    private interface HealthPlayer extends Player {
        @Override double getHealth();
        @Override double getMaxHealth();
    }

    private Player victim() {
        Player player = mock(HealthPlayer.class);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.getHealth()).thenReturn(20D);
        when(player.getMaxHealth()).thenReturn(20D);
        return player;
    }

    // Resolve the exact descriptor recorded in old addon bytecode, including its return type.
    private boolean legacyDamage(Player player, PlayerUtil.DamageType type, Double amount, boolean immune) throws Throwable {
        MethodHandle method = MethodHandles.publicLookup().findStatic(PlayerUtil.class, "damage",
                MethodType.methodType(boolean.class, Player.class, PlayerUtil.DamageType.class, Double.class, boolean.class));
        return (boolean) method.invokeExact(player, type, amount, immune);
    }

    @Test
    public void oldDescriptorAppliesNormalDamage() throws Throwable {
        Player player = victim();
        assertFalse(legacyDamage(player, PlayerUtil.DamageType.NORMAL, 3D, false));
        verify(player).damage(3D);
        verify(player, never()).setHealth(anyDouble());
    }

    @Test
    public void oldDescriptorAppliesTrueDamageAndLethalFallback() throws Throwable {
        Player player = victim();
        assertFalse(legacyDamage(player, PlayerUtil.DamageType.TRUE, 2D, false));
        verify(player).setHealth(18D);
        when(player.getHealth()).thenReturn(2D);
        assertFalse(legacyDamage(player, PlayerUtil.DamageType.TRUE, 10D, false));
        verify(player).setNoDamageTicks(0);
        verify(player).damage(2000D);
    }

    @Test
    public void oldDescriptorPreservesImmunityEventAndReturnValue() throws Throwable {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PluginManager manager = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            doAnswer(invocation -> {
                ((CanImmuneEvent) invocation.getArgument(0)).setCanImmune(true);
                return null;
            }).when(manager).callEvent(any(CanImmuneEvent.class));
            Player player = victim();
            assertTrue(legacyDamage(player, PlayerUtil.DamageType.TRUE, 3D, true));
            verify(player, never()).damage(anyDouble());
            verify(player, never()).setHealth(anyDouble());
        }
    }

    @Test
    public void primitiveAndAttackerDescriptorsRemainAvailable() throws Throwable {
        MethodHandles.publicLookup().findStatic(PlayerUtil.class, "damage",
                MethodType.methodType(void.class, Player.class, Player.class, PlayerUtil.DamageType.class, double.class, boolean.class));
        MethodHandle primitive = MethodHandles.publicLookup().findStatic(PlayerUtil.class, "damage",
                MethodType.methodType(boolean.class, Player.class, PlayerUtil.DamageType.class, double.class, boolean.class));
        Player player = victim();
        boolean result = (boolean) primitive.invokeExact(player, PlayerUtil.DamageType.NORMAL, 4D, false);
        assertFalse(result);
        verify(player).damage(4D);
    }

    @Test
    public void oldCriticalNameForwardsBothResultsToCurrentImplementation() throws Throwable {
        MethodHandle legacy = MethodHandles.publicLookup().findStatic(PlayerUtil.class, "isCritical",
                MethodType.methodType(boolean.class, Player.class));
        Player player = mock(Player.class);
        try (MockedStatic<PlayerUtil> util = mockStatic(PlayerUtil.class, CALLS_REAL_METHODS)) {
            // Resolve the NMS-dependent check separately; the alias must preserve its result.
            util.when(() -> PlayerUtil.critical(player)).thenReturn(true, false);
            assertTrue((boolean) legacy.invokeExact(player));
            assertFalse((boolean) legacy.invokeExact(player));
            util.verify(() -> PlayerUtil.critical(player), times(2));
        }
    }
}
