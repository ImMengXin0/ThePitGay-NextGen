package net.mizukilab.pit.data.operator;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.event.PitGainRenownEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClientSettings;
import net.mizukilab.pit.BukkitDataTestSupport;
import net.mizukilab.pit.PaperTestBootstrap;
import org.bson.*;
import org.bson.codecs.DecoderContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mongojack.JacksonCodecRegistry;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RenownDecodeTest extends BukkitDataTestSupport {
    @Test public void mongoJackRestoresNonzeroRenownWithoutCallingBukkitInEitherFieldOrder() {
        try (var bukkit = PaperTestBootstrap.mockBukkit()) {
            UUID uuid = UUID.randomUUID();
            var codec = new JacksonCodecRegistry(new ObjectMapper(), MongoClientSettings.getDefaultCodecRegistry(),
                    PlayerProfile.class, UuidRepresentation.JAVA_LEGACY).get(PlayerProfile.class);
            bukkit.clearInvocations();
            for (String json : List.of("{\"renown\":123,\"uuid\":\"" + uuid + "\"}",
                    "{\"uuid\":\"" + uuid + "\",\"renown\":123}")) {
                PlayerProfile profile = codec.decode(new BsonDocumentReader(BsonDocument.parse(json)),
                        DecoderContext.builder().build());
                assertEquals(123, profile.getRenown());
                assertEquals(uuid, profile.getPlayerUuid());
                assertFalse(profile.isLoaded());
            }
            bukkit.verify(() -> Bukkit.getPlayer(any(UUID.class)), never());
            bukkit.verify(Bukkit::getPluginManager, never());
            bukkit.verify(Bukkit::getScheduler, never());
        }
    }

    @Test public void loadedRewardsFireOnceOnMainThreadWithExactDeltaAndFinalBalance() {
        try (var bukkit = PaperTestBootstrap.mockBukkit()) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Tester");
            profile.setRenown(50);
            profile.setLoaded(true);
            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(profile.getPlayerUuid())).thenReturn(player);
            PluginManager plugins = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            doAnswer(call -> { assertEquals(53, profile.getRenown()); return null; }).when(plugins).callEvent(any());
            profile.setRenown(53);
            profile.setRenown(53);
            profile.setRenown(51);
            ArgumentCaptor<PitGainRenownEvent> event = ArgumentCaptor.forClass(PitGainRenownEvent.class);
            verify(plugins).callEvent(event.capture());
            assertEquals(3, event.getValue().getRenown());
            assertSame(player, event.getValue().getPlayer());
        }
    }

    @Test public void asyncRewardsKeepTheirDeltaAndNotifyOnlyOnScheduledMainThread() {
        try (var bukkit = PaperTestBootstrap.mockBukkit(); var pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Tester");
            profile.setRenown(20);
            profile.setLoaded(true);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            List<Runnable> queue = new ArrayList<>();
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
                queue.add(call.getArgument(1)); return mock(BukkitTask.class);
            });
            profile.setRenown(22);
            profile.setRenown(25);
            assertEquals(25, profile.getRenown());
            bukkit.verify(() -> Bukkit.getPlayer(any(UUID.class)), never());
            PluginManager plugins = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(profile.getPlayerUuid())).thenReturn(player);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            queue.forEach(Runnable::run);
            ArgumentCaptor<PitGainRenownEvent> event = ArgumentCaptor.forClass(PitGainRenownEvent.class);
            verify(plugins, times(2)).callEvent(event.capture());
            assertEquals(List.of(2, 3), event.getAllValues().stream().map(PitGainRenownEvent::getRenown).toList());
        }
    }
}
