package net.mizukilab.pit.util.scoreboard;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.event.AssemblePostUpdateEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ScoreboardCompatibilityTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private ThePit plugin;
    private Assemble assemble;
    private AssembleAdapter adapter;
    private AssembleThread ticker;
    private BukkitScheduler scheduler;
    private PluginManager manager;
    private final Map<UUID, AssembleBoard> boards = new LinkedHashMap<>();
    private final AtomicReference<List<String>> lines = new AtomicReference<>(List.of("one", "two", "three"));

    @Before public void setup() {
        bukkit = mockStatic(Bukkit.class); pit = mockStatic(ThePit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        plugin = mock(ThePit.class); pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        manager = mock(PluginManager.class); bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        scheduler = mock(BukkitScheduler.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        when(scheduler.runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        assemble = mock(Assemble.class); adapter = mock(AssembleAdapter.class);
        when(assemble.getPlugin()).thenReturn(plugin); when(assemble.getAdapter()).thenReturn(adapter);
        when(assemble.getBoards()).thenReturn(boards); when(assemble.getTicks()).thenReturn(1L);
        when(assemble.isHook()).thenReturn(true); when(assemble.getAssembleStyle()).thenReturn(AssembleStyle.MODERN);
        when(adapter.getTitle(any())).thenReturn("Title");
        when(adapter.getLines(any())).thenAnswer(call -> lines.get());
        ticker = new AssembleThread(assemble);
    }
    @After public void cleanup() { pit.close(); bukkit.close(); }

    private class Surface {
        final Player player = mock(Player.class);
        final Scoreboard scoreboard = mock(Scoreboard.class);
        final Objective objective = mock(Objective.class);
        final Map<String, Team> teams = new HashMap<>();
        final Map<String, Integer> scores = new HashMap<>();
        final AssembleBoard board;
        Surface() {
            UUID uuid = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(uuid);
            when(player.getName()).thenReturn("Viewer"); when(player.isOnline()).thenReturn(true);
            when(player.getScoreboard()).thenReturn(scoreboard);
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            when(scoreboard.getObjective("kqc")).thenReturn(objective);
            when(objective.getDisplayName()).thenReturn("Title");
            when(scoreboard.getTeam(anyString())).thenAnswer(call -> teams.get(call.getArgument(0)));
            when(scoreboard.registerNewTeam(anyString())).thenAnswer(call -> {
                String name = call.getArgument(0); Team team = mock(Team.class);
                when(team.getName()).thenReturn(name); when(team.getPrefix()).thenReturn(""); when(team.getSuffix()).thenReturn("");
                Set<String> entries = new HashSet<>(); when(team.getEntries()).thenReturn(entries);
                doAnswer(i -> entries.add(i.getArgument(0))).when(team).addEntry(anyString());
                doAnswer(i -> { teams.remove(name); return null; }).when(team).unregister();
                teams.put(name, team); return team;
            });
            when(objective.getScore(anyString())).thenAnswer(call -> {
                String id = call.getArgument(0); Score score = mock(Score.class);
                when(score.getScore()).thenAnswer(i -> scores.getOrDefault(id, 0));
                doAnswer(i -> { scores.put(id, i.getArgument(0)); return null; }).when(score).setScore(anyInt());
                return score;
            });
            doAnswer(call -> { scores.remove(call.getArgument(0)); return null; }).when(scoreboard).resetScores(anyString());
            board = new AssembleBoard(player, assemble); boards.put(uuid, board);
        }
    }

    @Test public void usesServerThreadForBukkitScoreboardsAndSynchronousEvents() {
        verify(scheduler).runTaskTimer(eq(plugin), eq(ticker), eq(0L), eq(1L));
        verify(scheduler, never()).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
    }
    @Test public void shrinkingAndGrowingLinesDoesNotLeaveEntriesTeamsOrDuplicateIdentifiers() {
        Surface s = new Surface(); ticker.run(); assertEquals(3, s.board.getEntries().size());
        lines.set(List.of("short")); ticker.run();
        assertEquals(1, s.board.getEntries().size()); assertEquals(1, s.teams.size());
        lines.set(List.of("one", "two", "three")); ticker.run();
        assertEquals(3, s.scores.size()); assertEquals(3, s.board.getIdentifiers().size());
        lines.set(List.of()); ticker.run();
        assertTrue(s.board.getEntries().isEmpty()); assertTrue(s.board.getIdentifiers().isEmpty()); assertTrue(s.teams.isEmpty());
        lines.set(List.of("returned")); ticker.run(); assertEquals(1, s.scores.size());
    }
    @Test public void sixteenLineExtensionIsCappedAtFifteenVisibleLines() {
        Surface s = new Surface(); lines.set(java.util.stream.IntStream.range(0,16).mapToObj(String::valueOf).toList());
        ticker.run(); assertEquals(15, s.board.getEntries().size());
    }
    @Test public void oneBrokenExtensionViewerDoesNotStopLaterPlayersAndFrameCleanup() {
        Surface first = new Surface(), second = new Surface();
        when(adapter.getLines(first.player)).thenThrow(new IllegalStateException("expected extension error"));
        ticker.run(); assertEquals(3, second.board.getEntries().size()); verify(adapter).endUpdate();
    }
    @Test public void extensionCanModifyImmutableCoreLinesWithoutCrashingAndNullLinesBecomeBlank() {
        Surface s = new Surface();
        doAnswer(call -> {
            if (call.getArgument(0) instanceof AssemblePostUpdateEvent event) event.getTexts().add(null);
            return null;
        }).when(manager).callEvent(any());
        ticker.run(); assertEquals(4, s.board.getEntries().size());
        lines.set(Arrays.asList("one", null)); ticker.run();
        assertEquals(3, s.board.getEntries().size());
    }

    @Test public void realCraftScoreboardRemovesTeamsEvenThoughGetTeamReturnsNewWrappers() throws Exception {
        Player player = mock(Player.class); UUID uuid=UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid); when(player.isOnline()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
        var ctor=org.bukkit.craftbukkit.v1_8_R3.scoreboard.CraftScoreboard.class
                .getDeclaredConstructor(net.minecraft.server.v1_8_R3.Scoreboard.class);
        ctor.setAccessible(true);
        Scoreboard real=(Scoreboard)ctor.newInstance(new net.minecraft.server.v1_8_R3.Scoreboard());
        when(player.getScoreboard()).thenReturn(real);
        AssembleBoard board=new AssembleBoard(player,assemble); boards.put(uuid,board);
        ticker.run(); assertEquals(3,real.getTeams().size());
        lines.set(List.of()); ticker.run();
        assertTrue(real.getTeams().isEmpty()); assertTrue(board.getEntries().isEmpty());
    }
}
