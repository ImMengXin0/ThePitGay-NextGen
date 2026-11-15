package net.mizukilab.pit.database;

import cn.charlotte.pit.ThePit;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import net.mizukilab.pit.config.PitGlobalConfig;
import org.junit.Test;

import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MongoLifecycleTest {
    @Test public void closeBeforeConnectionAndRepeatedCloseAreSafe() throws Exception {
        try (var pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            pit.when(ThePit::getInstance).thenReturn(plugin);
            MongoDB db = new MongoDB();
            db.close();
            MongoClient client = mock(MongoClient.class);
            var field = MongoDB.class.getDeclaredField("mongoClient");
            field.setAccessible(true);
            field.set(db, client);
            db.close();
            db.close();
            verify(client, times(1)).close();
            assertNull(db.getMongoClient());
        }
    }

    @Test public void failedCollectionInitializationClosesClientAndPreservesOriginalFailure() {
        try (var pit = mockStatic(ThePit.class); var clients = mockStatic(MongoClients.class)) {
            ThePit plugin = mock(ThePit.class);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            pit.when(ThePit::getInstance).thenReturn(plugin);
            PitGlobalConfig config = mock(PitGlobalConfig.class);
            when(plugin.getGlobalConfig()).thenReturn(config);
            when(config.getMongoDBAddress()).thenReturn("127.0.0.1");
            when(config.getMongoDBPort()).thenReturn(27017);
            MongoClient client = mock(MongoClient.class);
            clients.when(() -> MongoClients.create(any(MongoClientSettings.class))).thenReturn(client);
            IllegalStateException failure = new IllegalStateException("collection initialization failed");
            when(client.getDatabase("thePit")).thenThrow(failure);
            RuntimeException closeFailure = new RuntimeException("close failed");
            doThrow(closeFailure).when(client).close();
            MongoDB db = new MongoDB();

            assertSame(failure, assertThrows(IllegalStateException.class, db::connect));
            assertArrayEquals(new Throwable[]{closeFailure}, failure.getSuppressed());
            db.close();
            verify(client, times(1)).close();
            assertNull(db.getMongoClient());
        }
    }
}
