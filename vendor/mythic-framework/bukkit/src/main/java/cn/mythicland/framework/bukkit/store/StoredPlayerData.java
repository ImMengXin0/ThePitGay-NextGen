package cn.mythicland.framework.bukkit.store;

import java.util.UUID;

public record StoredPlayerData<P, I>(
        UUID uniqueId,
        P profileData,
        I inventoryData,
        long inventoryVersion,
        boolean profileExists
) {
    public StoredPlayerData(UUID uniqueId, P profileData, I inventoryData, long inventoryVersion) {
        this(uniqueId, profileData, inventoryData, inventoryVersion, true);
    }
}
