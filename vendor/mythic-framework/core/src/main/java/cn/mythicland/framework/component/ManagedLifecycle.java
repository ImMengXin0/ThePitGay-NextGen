package cn.mythicland.framework.component;

public interface ManagedLifecycle {
    default void load() {
    }

    default void reload() {
    }

    default void unload() {
    }
}
