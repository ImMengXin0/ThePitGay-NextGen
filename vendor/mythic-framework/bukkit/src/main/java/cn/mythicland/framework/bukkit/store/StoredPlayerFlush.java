package cn.mythicland.framework.bukkit.store;

import java.util.UUID;

public record StoredPlayerFlush<P, I>(
        UUID uniqueId,
        P profileData,
        I inventoryData,
        long profileVersion,
        long inventoryVersion,
        long profileRevision,
        long inventoryRevision,
        boolean flushProfile,
        boolean flushInventory
) {
}
