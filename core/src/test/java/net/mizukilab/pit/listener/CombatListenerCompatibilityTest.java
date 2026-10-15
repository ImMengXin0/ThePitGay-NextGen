package net.mizukilab.pit.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CombatListenerCompatibilityTest {
    @Test
    public void duplicateBukkitDeathDoesNotSettleOrClearRespawnProtection() {
        CombatListener listener = mock(CombatListener.class);
        doCallRealMethod().when(listener).onKilled(any());
        Player player = mock(Player.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        for (String marker : new String[]{"mx_suppressed_duplicate_death", "pit_death_handled"}) {
            reset(player);
            when(player.hasMetadata(marker)).thenReturn(true);
            listener.onKilled(event);
            verify(listener, never()).handlePlayerDeath(any(), any(), any(), anyBoolean());
            verify(player, never()).setNoDamageTicks(anyInt());
        }
        reset(player);
        listener.onKilled(event);
        verify(listener).handlePlayerDeath(event, player, null, true);
    }

    @Test
    public void eventEntryAlsoHonorsSuppressionBeforeAccessingProfileOrRewardState() {
        CombatListener listener = mock(CombatListener.class);
        doCallRealMethod().when(listener).handlePlayerDeath(any(), any(), any(), anyBoolean());
        Player player = mock(Player.class);
        when(player.hasMetadata("mx_suppressed_duplicate_death")).thenReturn(true);
        listener.handlePlayerDeath(mock(PlayerDeathEvent.class), player, null, true);
        verify(player, never()).getUniqueId();
        verify(player, never()).setMetadata(anyString(), any());
    }
    // Matches invokevirtual in the existing InstantRespawnListener binary.
    private MethodHandle legacyEntry() throws ReflectiveOperationException {
        return MethodHandles.publicLookup().findVirtual(CombatListener.class, "handlePlayerDeath",
                MethodType.methodType(void.class, Player.class, Player.class, boolean.class));
    }

    private CombatListener bridge() {
        CombatListener listener = mock(CombatListener.class);
        doCallRealMethod().when(listener).handlePlayerDeath(any(Player.class), nullable(Player.class), anyBoolean());
        return listener;
    }

    @Test
    public void oldDescriptorForwardsInstantRespawnToCurrentDeathFlow() throws Throwable {
        CombatListener listener = bridge();
        Player victim = mock(Player.class);
        Player killer = mock(Player.class);
        legacyEntry().invokeExact(listener, victim, killer, true);
        verify(listener, times(1)).handlePlayerDeath(null, victim, killer, true);
        // Keep the current event-based descriptor linkable as well.
        MethodHandles.publicLookup().findVirtual(CombatListener.class, "handlePlayerDeath",
                MethodType.methodType(void.class, PlayerDeathEvent.class, Player.class, Player.class, boolean.class));
    }

    @Test
    public void oldDescriptorPreservesNoKillerAndNoRespawn() throws Throwable {
        CombatListener listener = bridge();
        Player victim = mock(Player.class);
        Player killer = null;
        legacyEntry().invokeExact(listener, victim, killer, false);
        verify(listener, times(1)).handlePlayerDeath(null, victim, null, false);
    }

    @Test
    public void bridgePropagatesDeathFailureWithoutRetryingSettlement() throws Throwable {
        CombatListener listener = bridge();
        Player victim = mock(Player.class);
        Player killer = null;
        IllegalStateException failure = new IllegalStateException("death processing failed");
        doThrow(failure).when(listener).handlePlayerDeath(null, victim, null, true);
        try {
            legacyEntry().invokeExact(listener, victim, killer, true);
            fail("The caller must receive the original failure");
        } catch (IllegalStateException actual) {
            assertSame(failure, actual);
        }
        verify(listener, times(1)).handlePlayerDeath(null, victim, null, true);
    }
}
