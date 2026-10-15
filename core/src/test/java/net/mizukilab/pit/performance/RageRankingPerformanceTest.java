package net.mizukilab.pit.performance;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.events.impl.major.RagePitEvent;
import net.mizukilab.pit.scoreboard.Scoreboard;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RageRankingPerformanceTest extends BukkitDataTestSupport {
    @Test
    public void snapshotsPreserveTiesAndRefreshAfterExternalDamageChangesAndClear() {
        RagePitEvent event = new RagePitEvent();
        Player first = player(), second = player(), third = player();
        event.getDamageMap().put(first.getUniqueId(), new RagePitEvent.DamageData(first.getUniqueId(), 30));
        event.getDamageMap().put(second.getUniqueId(), new RagePitEvent.DamageData(second.getUniqueId(), 30));
        event.getDamageMap().put(third.getUniqueId(), new RagePitEvent.DamageData(third.getUniqueId(), 10));
        var snapshot = event.getDamageStandings();
        assertEquals(2, snapshot.get(first.getUniqueId()).rank());
        assertEquals(2, snapshot.get(second.getUniqueId()).rank());
        assertEquals(3, snapshot.get(third.getUniqueId()).rank());
        assertEquals(-1, event.getDamageRank(player()));
        event.getDamageMap().get(third.getUniqueId()).setDamage(50);
        assertEquals(1, event.getDamageRank(third));
        assertEquals(3, snapshot.get(third.getUniqueId()).rank());
        assertEquals(1, event.getDamageStandings().get(third.getUniqueId()).rank());
        event.getDamageMap().clear();
        assertTrue(event.getDamageStandings().isEmpty());
        assertEquals(-1, event.getDamageRank(first));
    }

    @Test
    public void oneHundredScoreboardsShareOneRankingAndNextRefreshSeesChanges() {
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class)) {
            ThePit plugin = mock(ThePit.class, RETURNS_DEEP_STUBS);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            RagePitEvent event = spy(new RagePitEvent());
            event.setActive(true);
            when(plugin.getEventFactory().getActiveEpicEvent()).thenReturn(event);
            PlayerProfile profile = mock(PlayerProfile.class, RETURNS_DEEP_STUBS);
            when(profile.isLoaded()).thenReturn(true);
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(any(UUID.class))).thenReturn(profile);
            List<Player> players = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Player player = player();
                players.add(player);
                event.getDamageMap().put(player.getUniqueId(), new RagePitEvent.DamageData(player.getUniqueId(), i * 2));
            }
            Scoreboard board = new Scoreboard();
            board.beginUpdate();
            for (Player player : players) assertTrue(board.getLines(player).stream().anyMatch(s -> s.contains("(#")));
            verify(event, times(1)).getDamageStandings();
            verify(event, never()).getDamageRank(any());
            event.getDamageMap().get(players.get(0).getUniqueId()).setDamage(1000);
            assertTrue(board.getLines(players.get(0)).stream().anyMatch(s -> s.contains("(#100)")));
            board.endUpdate();
            board.beginUpdate();
            assertTrue(board.getLines(players.get(0)).stream().anyMatch(s -> s.contains("(#1)")));
            verify(event, times(2)).getDamageStandings();
            board.endUpdate();
            // Direct API calls outside the batch must never see a retained snapshot.
            board.getLines(players.get(0));
            verify(event, times(3)).getDamageStandings();
        }
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
