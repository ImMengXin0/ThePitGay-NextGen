package cn.mythicland.framework.component;

import java.util.Objects;

public final class ComponentArguments<T> {
    private static final ComponentArguments<Void> EMPTY = new ComponentArguments<>(null, false);

    private final T value;
    private final boolean present;

    private ComponentArguments(T value, boolean present) {
        this.value = value;
        this.present = present;
    }

    public static ComponentArguments<Void> empty() {
        return EMPTY;
    }

    public static <T> ComponentArguments<T> of(T value) {
        return new ComponentArguments<>(Objects.requireNonNull(value, "value"), true);
    }

    public boolean isPresent() {
        return this.present;
    }

    public T value() {
        if (!this.present) throw new IllegalStateException("Component arguments are empty");
        return this.value;
    }
}
