package cn.mythicland.framework.menu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PaginatedMenuSessionRegistry {
    private static final Map<SessionKey, Integer> PAGES = new ConcurrentHashMap<>();

    private PaginatedMenuSessionRegistry() {
    }

    public static void open(UUID playerUniqueId, AbstractPaginatedMenu<?> menu) {
        PAGES.putIfAbsent(new SessionKey(playerUniqueId, menu), 0);
    }

    public static int currentPage(UUID playerUniqueId, AbstractPaginatedMenu<?> menu) {
        return PAGES.getOrDefault(new SessionKey(playerUniqueId, menu), 0);
    }

    public static void setPage(UUID playerUniqueId, AbstractPaginatedMenu<?> menu, int page) {
        PAGES.put(new SessionKey(playerUniqueId, menu), page);
    }

    public static void reset(UUID playerUniqueId, AbstractPaginatedMenu<?> menu) {
        PAGES.put(new SessionKey(playerUniqueId, menu), 0);
    }

    public static void clear(UUID playerUniqueId, AbstractPaginatedMenu<?> menu) {
        PAGES.remove(new SessionKey(playerUniqueId, menu));
    }

    public static void clear(UUID playerUniqueId) {
        PAGES.keySet().removeIf(key -> key.playerUniqueId().equals(playerUniqueId));
    }

    public static void clearAll() {
        PAGES.clear();
    }

    private record SessionKey(UUID playerUniqueId, AbstractPaginatedMenu<?> menu) {
    }
}
