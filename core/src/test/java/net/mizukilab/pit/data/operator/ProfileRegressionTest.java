package net.mizukilab.pit.data.operator;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.KingsQuestsData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProfileRegressionTest extends net.mizukilab.pit.BukkitDataTestSupport {
    @Test
    public void newProfileCanLockUnlockAndRejectDuplicateLock() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Tester");
        assertEquals(ProfileOperator.OPCODE_FREE, profile.code);
        assertSame(profile, profile.disallow());
        assertSame(PlayerProfile.NONE_PROFILE, profile.disallow());
        assertSame(profile, profile.allow());
        assertSame(profile, profile.disallow());
        profile.allow();
    }

    @Test
    public void supporterChecksStoredStateOfflineAndPermissionsOnline() {
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PitInternalHook api = mock(PitInternalHook.class);
            pit.when(ThePit::getApi).thenReturn(api);
            when(api.getPitSupportPermission()).thenReturn("pit.supporter");
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Tester");
            Player player = mock(Player.class);
            bukkit.when(() -> Bukkit.getPlayer(profile.getPlayerUuid())).thenReturn(player);
            when(player.isOnline()).thenReturn(true);
            assertFalse(profile.isSupporter());
            profile.setSupporter(true);
            assertTrue(profile.isSupporter());
            when(api.getRemoveSupportWhenNoPermission()).thenReturn(true);
            assertFalse(profile.isSupporter());
            when(player.hasPermission("pit.supporter")).thenReturn(true);
            assertTrue(profile.isSupporter());
            bukkit.when(() -> Bukkit.getPlayer(profile.getPlayerUuid())).thenReturn(null);
            assertTrue(profile.isSupporter());
            profile.setSupporter(false);
            assertFalse(profile.isSupporter());
        }
    }

    @Test
    public void kingQuestProgressResetsOnlyWhenCycleChanges() {
        PitInternalHook previous = ThePit.api;
        PitInternalHook api = mock(PitInternalHook.class);
        ThePit.api = api;
        try {
            UUID first = UUID.randomUUID();
            when(api.getRunningKingsQuestsUuid()).thenReturn(first);
            KingsQuestsData data = new KingsQuestsData();
            data.checkUpdate();
            data.setAccepted(true);
            data.setCompleted(true);
            data.setKilledPlayer(99);
            data.setCollectedRenown(10);
            data.checkUpdate();
            assertEquals(99, data.getKilledPlayer());
            when(api.getRunningKingsQuestsUuid()).thenReturn(UUID.randomUUID());
            data.checkUpdate();
            assertFalse(data.getAccepted());
            assertFalse(data.getCompleted());
            assertEquals(0, data.getKilledPlayer());
            assertEquals(0, data.getCollectedRenown());
        } finally { ThePit.api = previous; }
    }

    @Test
    public void pendingSaveIsVisibleAndReleasesBusyStateAfterFailure() {
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.disallow()).thenReturn(profile);
        when(profile.save(null)).thenThrow(new IllegalStateException("expected save failure"));
        PackedOperator operator = new PackedOperator(null);
        operator.profile = profile;
        operator.policy = mock(ExecutionPolicy.class);
        operator.save(true, true);
        assertTrue(operator.hasAnyOperation());
        operator.drainTasksOnCurrentThread();
        verify(profile).allow();
        assertFalse(operator.hasAnyExecutingOp());
        assertTrue(operator.hasAnyOperation());
        doReturn(profile).when(profile).save(null);
        operator.nextSaveAttemptAt = 0;
        operator.drainTasksOnCurrentThread();
        assertFalse(operator.hasAnyOperation());
        assertNull(operator.throwable);
        verify(profile, times(2)).allow();
    }

    @Test
    public void periodicHeartbeatDoesNotScheduleAnEntityTaskButLegacyHeartbeatStillUpdates() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            Player player = mock(Player.class);
            UUID uuid = UUID.randomUUID();
            when(player.isOnline()).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            PlayerProfile profile = mock(PlayerProfile.class);
            when(profile.getPlayerUuid()).thenReturn(uuid);
            when(profile.isLoaded()).thenReturn(true);
            PackedOperator operator = new PackedOperator(null);
            operator.profile = profile;
            operator.fireExit = true;
            operator.quitFlag = true;
            operator.heartBeat(false);
            assertFalse(operator.fireExit);
            assertFalse(operator.quitFlag);
            assertTrue(operator.lastHeartBeat > 0);
            verify(profile, never()).onTick(any());
            operator.heartBeat();
            verify(profile).onTick(player);
        }
    }

    @Test
    public void nameLookupUsesOneQueryForZeroOneAndDuplicateMatches() {
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            net.mizukilab.pit.database.MongoDB mongo = mock(net.mizukilab.pit.database.MongoDB.class);
            when(plugin.getMongoDB()).thenReturn(mongo);
            org.mongojack.JacksonMongoCollection<PlayerProfile> collection = mock(org.mongojack.JacksonMongoCollection.class);
            when(mongo.getProfileCollection()).thenReturn(collection);
            java.util.List<PlayerProfile> matches = new java.util.ArrayList<>();
            com.mongodb.client.FindIterable<PlayerProfile> iterable = mock(com.mongodb.client.FindIterable.class);
            when(collection.find(any(org.bson.conversions.Bson.class))).thenReturn(iterable);
            doAnswer(call -> { java.util.Collection target = call.getArgument(0); target.addAll(matches); return target; })
                    .when(iterable).into(any(java.util.Collection.class));
            clearInvocations(collection);
            assertNull(PlayerProfile.loadPlayerProfileByName("Tester"));
            PlayerProfile first = mock(PlayerProfile.class), second = mock(PlayerProfile.class);
            when(first.getLastLogoutTime()).thenReturn(10L);
            when(second.getLastLogoutTime()).thenReturn(20L);
            matches.add(first);
            assertSame(first, PlayerProfile.loadPlayerProfileByName("Tester"));
            matches.add(second);
            assertSame(second, PlayerProfile.loadPlayerProfileByName("Tester"));
            when(first.getLastLogoutTime()).thenReturn(20L);
            assertSame(first, PlayerProfile.loadPlayerProfileByName("Tester"));
            verify(collection, times(4)).find(any(org.bson.conversions.Bson.class));
            verify(collection, never()).findOne(any(org.bson.conversions.Bson.class));
        }
    }
}
