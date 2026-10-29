package cn.mythicland.framework.component;

import java.util.Collection;
import java.util.List;

public final class ComponentSet<T> {
    private final List<T> values;

    private ComponentSet(Collection<? extends T> values) {
        this.values = List.copyOf(values);
    }

    public static <T> ComponentSet<T> of(Collection<? extends T> values) {
        return new ComponentSet<>(values);
    }

    public static <T> ComponentSet<T> empty() {
        return new ComponentSet<>(List.of());
    }

    public List<T> values() {
        return this.values;
    }
}
