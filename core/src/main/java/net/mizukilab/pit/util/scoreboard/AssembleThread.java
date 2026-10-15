package net.mizukilab.pit.util.scoreboard;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.event.AssemblePostUpdateEvent;
import cn.charlotte.pit.event.AssembleUpdateEvent;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.List;

public class AssembleThread implements Runnable {

    private final Assemble assemble;

    protected int taskId;
    private final Map<UUID, Long> lastFailure = new HashMap<>();

    /**
     * Assemble Thread.
     *
     * @param assemble instance.
     */
    AssembleThread(Assemble assemble) {
        this.assemble = assemble;
        taskId = Bukkit.getScheduler().runTaskTimer(assemble.getPlugin(), this, 0, assemble.getTicks()).getTaskId();
    }

    @Override
    @SneakyThrows
    public void run() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(assemble.getPlugin(), this);
            return;
        }
        AssembleAdapter adapter = this.assemble.getAdapter();
        if (adapter == null) return;
        try {
            adapter.beginUpdate();
            tick(adapter);
        } finally {
            adapter.endUpdate();
        }
    }

    /**
     * Tick logic for thread.
     */
    private void tick(AssembleAdapter adapter) {
        lastFailure.keySet().retainAll(assemble.getBoards().keySet());
        this.assemble.getBoards().forEach((uuid, board) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || board == null) {
                return;
            }

            try {
                // This shouldn't happen, but just in case.
                Scoreboard scoreboard = board.getScoreboard();
                Objective objective = board.getObjective();

                if (scoreboard == null || objective == null) {
                    return;
                }

                AssembleUpdateEvent assembleUpdateEvent = new AssembleUpdateEvent(player);
                assembleUpdateEvent.callEvent();
                if (assembleUpdateEvent.isCancelled()) {
                    return;
                }
                // Just make a variable so we don't have to
                // process the same thing twice.
                String title = ChatColor.translateAlternateColorCodes('&', adapter.getTitle(player));

                // Update the title if needed.
                if (!objective.getDisplayName().equals(title)) {
                    objective.setDisplayName(title);
                }
                List<String> provided = adapter.getLines(player);
                // Event listeners may add/remove lines even if an adapter returns List.of().
                List<String> newLines = provided == null ? new ArrayList<>() : new ArrayList<>(provided);

                AssemblePostUpdateEvent assemblePostUpdateEvent = new AssemblePostUpdateEvent(player, newLines);
                assemblePostUpdateEvent.callEvent();
                if (assemblePostUpdateEvent.isCancelled()) {
                    return;
                }
                if (newLines.size() > 15) newLines = newLines.subList(0, 15);
                int size1 = newLines.size();
                // Remove from the end: remove() detaches both the entry and its team.
                for (int i = board.getEntries().size() - 1; i >= size1; i--) {
                    board.getEntryAtPosition(i).remove();
                }
                if (size1 > 0) {
                    boolean descending = this.assemble.getAssembleStyle().isDescending();

                    // Update existing entries / add new entries.
                    int cache = descending ? this.assemble.getAssembleStyle().getStartNumber() : size1;
                    for (int i = 0; i < size1; i++) {
                        AssembleBoardEntry entry = board.getEntryAtPosition(i);

                        // Translate any colors.
                        String textToTranslate = newLines.get(i);
                        if (textToTranslate == null) textToTranslate = "";
                        String line = ChatColor.
                                translateAlternateColorCodes('&', textToTranslate);

                        // If the entry is null, just create a new one.
                        // Creating a new AssembleBoardEntry instance will add
                        // itself to the provided board's entries list.
                        if (entry == null) {
                            entry = new AssembleBoardEntry(board, line, i);
                        }

                        // Update text, setup the team, and update the display values.
                        entry.setText(line);
                        entry.setup();
                        entry.send(descending ? cache++ : cache--);
                    }
                }

                if (player.getScoreboard() != scoreboard && !assemble.isHook()) {
                    player.setScoreboard(scoreboard);
                }
            } catch (Exception e) {
                long now = System.currentTimeMillis();
                Long previous = lastFailure.get(uuid);
                if (previous == null || now - previous >= 30_000L) {
                    lastFailure.put(uuid, now);
                    assemble.getPlugin().getLogger().log(Level.WARNING,
                            "Could not update " + player.getName() + "'s scoreboard; other viewers will continue.", e);
                }
            }
        });

    }

}
