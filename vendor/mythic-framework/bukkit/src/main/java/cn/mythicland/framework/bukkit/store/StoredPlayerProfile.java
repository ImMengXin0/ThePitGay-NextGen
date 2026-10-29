package cn.mythicland.framework.bukkit.store;

public interface StoredPlayerProfile<P> {
    long version(P profileData);

    P nextVersion(P profileData);
}
