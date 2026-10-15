package net.mizukilab.pit.actionbar;
import net.mizukilab.pit.util.chat.ActionBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.*;
import static org.mockito.Mockito.*;

public class ActionBarRegressionTest {
    @Test public void fivePlayersAllReceiveThreeRepeatsWithoutStarvingFourOfThem() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<ActionBarUtil> bars = mockStatic(ActionBarUtil.class)) {
            ActionBarManager manager = new ActionBarManager(); List<Player> players = new ArrayList<>();
            for (int i=0; i<5; i++) {
                Player p=mock(Player.class); UUID uuid=UUID.randomUUID(); when(p.getUniqueId()).thenReturn(uuid); when(p.isOnline()).thenReturn(true);
                bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(p); players.add(p); manager.addActionBarOnQueue(p,"status","ready",3);
            }
            for (int i=0; i<15; i++) manager.tick();
            for (Player player:players) bars.verify(() -> ActionBarUtil.sendActionBar0(player,"ready"),times(3));
        }
    }
}
