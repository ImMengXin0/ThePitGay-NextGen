package cn.mythicland.framework.bukkit.store;

import java.util.Collection;
import java.util.UUID;

public interface StoredPlayerRepository<P, I> {
    StoredPlayerData<P, I> load(UUID uniqueId) throws Exception;

    void flush(Collection<StoredPlayerFlush<P, I>> requests) throws Exception;
}
