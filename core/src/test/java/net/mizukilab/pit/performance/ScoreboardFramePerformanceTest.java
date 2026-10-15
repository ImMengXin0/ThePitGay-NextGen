package net.mizukilab.pit.performance;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import net.mizukilab.pit.config.NewConfiguration;
import net.mizukilab.pit.scoreboard.Scoreboard;
import org.bukkit.entity.Player;
import org.junit.Test;
import java.text.SimpleDateFormat;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class ScoreboardFramePerformanceTest extends net.mizukilab.pit.BukkitDataTestSupport {
    @Test public void oneHundredViewersShareDateAndTitleButPersonalLinesStayLive() {
        var config=NewConfiguration.INSTANCE;
        var oldTitles=config.getScoreBoardAnimation(); boolean oldShow=config.getScoreboardShowtime();
        try(var pit=mockStatic(ThePit.class); var profiles=mockStatic(PlayerProfile.class);
            var dates=mockConstruction(SimpleDateFormat.class,(format,context) -> when(format.format(anyLong())).thenReturn("date"))) {
            config.setScoreBoardAnimation(List.of("title")); config.setScoreboardShowtime(true);
            ThePit plugin=mock(ThePit.class,RETURNS_DEEP_STUBS); pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getEventFactory().getActiveEpicEvent()).thenReturn(null);
            when(plugin.getEventFactory().getActiveNormalEvent()).thenReturn(null);
            PlayerProfile profile=mock(PlayerProfile.class,RETURNS_DEEP_STUBS); when(profile.isLoaded()).thenReturn(true);
            when(profile.getActiveMegaStreakObj()).thenReturn(null);
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(any(UUID.class))).thenReturn(profile);
            Player player=mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            Scoreboard board=new Scoreboard(); board.beginUpdate();
            for(int i=0;i<100;i++) { assertEquals("title",board.getTitle(player)); board.getLines(player); }
            verify(dates.constructed().get(0),times(1)).format(anyLong());
            when(profile.getCoins()).thenReturn(123.0); assertTrue(board.getLines(player).stream().anyMatch(line -> line.contains("123")));
            board.endUpdate(); config.setScoreBoardAnimation(List.of("next")); board.beginUpdate();
            assertEquals("next",board.getTitle(player)); board.getLines(player);
            verify(dates.constructed().get(0),times(2)).format(anyLong());
            board.endUpdate(); board.getLines(player); verify(dates.constructed().get(0),times(3)).format(anyLong());
        } finally { config.setScoreBoardAnimation(oldTitles); config.setScoreboardShowtime(oldShow); }
    }
}
