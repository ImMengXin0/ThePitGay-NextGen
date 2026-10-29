package cn.mythicland.framework.bukkit.visibility;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerVisibilityService {
    private final Map<VisibilityPair, Set<String>> hiddenSources = new Object2ObjectOpenHashMap<>();

    public boolean hide(Player viewer, Player target, String source) {
        if (!this.valid(viewer, target, source)) return false;
        VisibilityPair pair = VisibilityPair.of(viewer, target);
        Set<String> sources = this.hiddenSources.computeIfAbsent(
                pair,
                ignored -> new HashSet<>()
        );
        boolean firstHide = sources.isEmpty();
        sources.add(source);
        if (firstHide && viewer.canSee(target)) {
            viewer.hidePlayer(target);
        }
        return true;
    }

    public boolean show(Player viewer, Player target, String source) {
        if (!this.valid(viewer, target, source)) return false;
        VisibilityPair pair = VisibilityPair.of(viewer, target);
        Set<String> sources = this.hiddenSources.get(pair);
        if (sources == null) return false;
        sources.remove(source);
        if (!sources.isEmpty()) return true;
        this.hiddenSources.remove(pair);
        if (!viewer.canSee(target)) {
            viewer.showPlayer(target);
        }
        return true;
    }

    public boolean canSee(Player viewer, Player target) {
        if (viewer == null || target == null) return false;
        return viewer.canSee(target);
    }

    public void showTargetForAll(Player target, String source) {
        if (target == null || source == null || source.isBlank()) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            this.show(viewer, target, source);
        }
    }

    public void clearSource(String source) {
        if (source == null || source.isBlank()) return;
        for (Map.Entry<VisibilityPair, Set<String>> entry : Set.copyOf(this.hiddenSources.entrySet())) {
            VisibilityPair pair = entry.getKey();
            Player viewer = Bukkit.getPlayer(pair.viewerUniqueId());
            Player target = Bukkit.getPlayer(pair.targetUniqueId());
            if (viewer == null || target == null) {
                this.hiddenSources.remove(pair);
                continue;
            }
            this.show(viewer, target, source);
        }
    }

    public void clearTarget(Player target) {
        if (target != null) {
            this.clearTarget(target.getUniqueId());
        }
    }

    public void clearTarget(UUID targetUniqueId) {
        if (targetUniqueId == null) return;
        for (VisibilityPair pair : Set.copyOf(this.hiddenSources.keySet())) {
            if (!targetUniqueId.equals(pair.targetUniqueId())) continue;
            this.restoreAndRemove(pair);
        }
    }

    public void clearViewer(Player viewer) {
        if (viewer != null) {
            this.clearViewer(viewer.getUniqueId());
        }
    }

    public void clearViewer(UUID viewerUniqueId) {
        if (viewerUniqueId == null) return;
        for (VisibilityPair pair : Set.copyOf(this.hiddenSources.keySet())) {
            if (!viewerUniqueId.equals(pair.viewerUniqueId())) continue;
            this.restoreAndRemove(pair);
        }
    }

    private void restoreAndRemove(VisibilityPair pair) {
        this.hiddenSources.remove(pair);
        Player viewer = Bukkit.getPlayer(pair.viewerUniqueId());
        Player target = Bukkit.getPlayer(pair.targetUniqueId());
        if (viewer != null && target != null && !viewer.canSee(target)) {
            viewer.showPlayer(target);
        }
    }

    private boolean valid(Player viewer, Player target, String source) {
        return viewer != null && target != null && source != null && !source.isBlank();
    }

    private record VisibilityPair(UUID viewerUniqueId, UUID targetUniqueId) {
        private static VisibilityPair of(Player viewer, Player target) {
            return new VisibilityPair(viewer.getUniqueId(), target.getUniqueId());
        }
    }
}
