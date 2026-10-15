package net.mizukilab.pit.performance;

import cn.charlotte.pit.ThePit;
import net.mizukilab.pit.util.nametag.*;
import net.mizukilab.pit.nametag.NameTagImpl;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.*;
import org.junit.Test;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Opcodes;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NametagPerformanceTest {
    @Test
    public void sharedAdapterBuildsOneListForOneHundredViewersAndRefreshesNextRound() throws Exception {
        runBatch(true);
    }

    @Test
    public void viewerSpecificAdaptersAreStillCalledForEachViewer() throws Exception {
        runBatch(false);
    }

    private void runBatch(boolean shared) throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.isEnabled()).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
            List<Player> players = new ArrayList<>();
            Map<UUID, NametagBoard> boards = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                Player player = mock(Player.class);
                UUID uuid = UUID.randomUUID();
                when(player.getUniqueId()).thenReturn(uuid);
                players.add(player);
                boards.put(uuid, mock(NametagBoard.class));
            }
            Server server = onlinePlayerServer(players);
            when(plugin.getServer()).thenReturn(server);
            NametagHandler handler = mock(NametagHandler.class);
            when(handler.getPlugin()).thenReturn(plugin);
            when(handler.getBoards()).thenReturn(boards);
            NametagAdapter adapter = mock(NametagAdapter.class);
            when(handler.getAdapter()).thenReturn(adapter);
            when(adapter.isViewerIndependent()).thenReturn(shared);
            List<BufferedNametag> first = List.of(new BufferedNametag("1", "a", "", false, players.get(0)));
            List<BufferedNametag> second = List.of(new BufferedNametag("1", "b", "", false, players.get(0)));
            when(adapter.getPlate(any())).thenReturn(first, second);
            NametagThread thread = new NametagThread(handler);
            thread.run();
            thread.run();
            if (shared) {
                verify(adapter, times(2)).getPlate(any());
                boards.values().forEach(board -> {
                    verify(board).update(first);
                    verify(board).update(second);
                });
            } else {
                // Normal update delegates to each board's adapter with its viewer.
                boards.values().forEach(board -> verify(board, times(2)).update());
                verify(adapter, never()).getPlate(any());
            }
        }
    }

    @Test
    public void unchangedTeamsSendNoPropertyUpdatesButChangesAndRemovalAreApplied() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            Player player = mock(Player.class);
            UUID uuid = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(uuid);
            when(player.isOnline()).thenReturn(true);
            when(player.getName()).thenReturn("Tester");
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            NametagHandler handler = mock(NametagHandler.class);
            NametagAdapter adapter = mock(NametagAdapter.class);
            when(handler.getAdapter()).thenReturn(adapter);
            when(handler.isHook()).thenReturn(true);
            Scoreboard scoreboard = mock(Scoreboard.class);
            when(player.getScoreboard()).thenReturn(scoreboard);
            Team team = mock(Team.class);
            when(scoreboard.getTeam("200")).thenReturn(team);
            when(team.getName()).thenReturn("200");
            when(team.getPrefix()).thenReturn("level");
            when(team.getSuffix()).thenReturn("bounty");
            when(team.hasEntry("Tester")).thenReturn(true);
            when(team.getEntries()).thenReturn(new HashSet<>(List.of("Tester", "Offline")));
            BufferedNametag tag = new BufferedNametag("200", "level", "bounty", false, player);
            when(adapter.getPlate(player)).thenReturn(List.of(tag));
            NametagBoard board = new NametagBoard(player, handler);
            board.update();
            verify(adapter).getPlate(player);
            verify(team, never()).setPrefix(anyString());
            verify(team, never()).setSuffix(anyString());
            verify(team, never()).setCanSeeFriendlyInvisibles(anyBoolean());
            verify(team).removeEntry("Offline");
            tag.setPrefix("new level");
            tag.setSuffix("new bounty");
            tag.setFriendlyInvis(true);
            board.update(List.of(tag));
            verify(team).setPrefix("new level");
            verify(team).setSuffix("new bounty");
            verify(team).setCanSeeFriendlyInvisibles(true);
            board.update(List.of());
            verify(team).unregister();
        }
    }

    @Test
    public void legacyAdaptersAndCoreSubclassesKeepViewerSpecificDefault() {
        NametagAdapter legacy = new NametagAdapter() {
            public List<BufferedNametag> getPlate(Player p) { return List.of(); }
            public boolean showHealthBelowName(Player p) { return false; }
        };
        assertFalse(legacy.isViewerIndependent());
        assertTrue(new NameTagImpl().isViewerIndependent());
        assertFalse(new NameTagImpl() {}.isViewerIndependent());
    }
    private static Server onlinePlayerServer(List<Player> players) throws Exception {
        String name = "net/mizukilab/pit/performance/OnlinePlayerServerStub";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null,
                "java/lang/Object", new String[]{"org/bukkit/Server"});
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        for (var field : Map.of("players", "Ljava/util/Collection;",
                "legacyPlayers", "[Lorg/bukkit/entity/Player;").entrySet()) {
            writer.visitField(Opcodes.ACC_PUBLIC, field.getKey(), field.getValue(), null, null).visitEnd();
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getOnlinePlayers", "()" + field.getValue(), null, null);
            method.visitCode();
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitFieldInsn(Opcodes.GETFIELD, name, field.getKey(), field.getValue());
            method.visitInsn(Opcodes.ARETURN);
            method.visitMaxs(1, 1);
            method.visitEnd();
        }
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(Server.class.getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        Object server = type.getConstructor().newInstance();
        type.getField("players").set(server, players);
        type.getField("legacyPlayers").set(server, players.toArray(new Player[0]));
        return (Server) server;
    }

}
