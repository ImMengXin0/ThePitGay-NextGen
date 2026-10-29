package cn.mythicland.framework.menu;

public enum ClickType {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT;

    public static ClickType fromClickType(org.bukkit.event.inventory.ClickType clickType) {
        if (clickType == null) return null;
        return switch (clickType) {
            case LEFT -> LEFT;
            case RIGHT -> RIGHT;
            case SHIFT_LEFT -> SHIFT_LEFT;
            case SHIFT_RIGHT -> SHIFT_RIGHT;
            default -> null;
        };
    }

    public boolean isLeft() {
        return this == LEFT || this == SHIFT_LEFT;
    }

    public boolean isRight() {
        return this == RIGHT || this == SHIFT_RIGHT;
    }
}
