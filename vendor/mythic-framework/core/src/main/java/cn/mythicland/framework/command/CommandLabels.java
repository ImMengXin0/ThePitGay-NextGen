package cn.mythicland.framework.command;

public final class CommandLabels {
    private CommandLabels() {
    }

    public static boolean matches(String input, String expected) {
        if (input == null || input.isBlank()) return false;
        String label = input.trim().split("\\s+", 2)[0];
        if (label.startsWith("/")) label = label.substring(1);
        int namespace = label.indexOf(':');
        if (namespace >= 0) label = label.substring(namespace + 1);
        return label.equalsIgnoreCase(expected);
    }
}
