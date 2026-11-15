package cn.charlotte.pit;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.operator.IProfilerOperator;
import cn.charlotte.pit.events.EventWorldEdits;
import cn.charlotte.pit.util.hologram.HologramAPI;
import net.mizukilab.pit.PaperTestBootstrap;
import net.mizukilab.pit.database.MongoDB;
import net.mizukilab.pit.menu.trade.TradeManager;
import net.mizukilab.pit.util.nametag.NametagHandler;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

public class PluginShutdownTest extends net.mizukilab.pit.BukkitDataTestSupport {
    @Test public void cleanupFailuresStillDrainProfilesThenCloseDatabaseOnlyOnce() throws Exception {
        try (var pit = mockStatic(ThePit.class);
             var bukkit = PaperTestBootstrap.mockBukkit();
             var worlds = mockStatic(EventWorldEdits.class);
             var trades = mockStatic(TradeManager.class);
             var holograms = mockStatic(HologramAPI.class);
             var profiles = mockStatic(PlayerProfile.class)) {
            ThePit plugin = mock(ThePit.class, CALLS_REAL_METHODS);
            doReturn(Logger.getAnonymousLogger()).when(plugin).getLogger();
            pit.when(ThePit::getInstance).thenReturn(plugin);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            NametagHandler nametags = mock(NametagHandler.class);
            doThrow(new IllegalStateException("nametag cleanup failed")).when(nametags).cleanup();
            plugin.setNametagHandler(nametags);
            IProfilerOperator operator = mock(IProfilerOperator.class);
            doAnswer(call -> {
                profiles.verify(() -> PlayerProfile.saveAllSync(false));
                throw new IOException("profile drain failed");
            }).when(operator).close();
            plugin.setProfileOperator(operator);
            MongoDB db = mock(MongoDB.class);
            var field = ThePit.class.getDeclaredField("mongoDB");
            field.setAccessible(true);
            field.set(plugin, db);

            plugin.onDisable();
            plugin.onDisable();

            var order = inOrder(scheduler, nametags, operator, db);
            order.verify(scheduler).cancelTasks(plugin);
            order.verify(nametags).cleanup();
            profiles.verify(() -> PlayerProfile.saveAllSync(false), times(1));
            order.verify(operator).close();
            order.verify(db).close();
            verify(db, times(1)).close();
        }
    }

    @Test public void disablingPartiallyInitializedPluginStillClosesDatabase() throws Exception {
        try (var pit = mockStatic(ThePit.class);
             var bukkit = PaperTestBootstrap.mockBukkit();
             var worlds = mockStatic(EventWorldEdits.class);
             var trades = mockStatic(TradeManager.class);
             var holograms = mockStatic(HologramAPI.class)) {
            ThePit plugin = mock(ThePit.class, CALLS_REAL_METHODS);
            doReturn(Logger.getAnonymousLogger()).when(plugin).getLogger();
            pit.when(ThePit::getInstance).thenReturn(plugin);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            MongoDB db = mock(MongoDB.class);
            var field = ThePit.class.getDeclaredField("mongoDB");
            field.setAccessible(true);
            field.set(plugin, db);

            plugin.onDisable();

            verify(db).close();
        }
    }
}
