package dev.rollczi.litecommands.bukkit;

/** Uses Bukkit's native completion path; mutable game registries must not be read by async packet listeners. */
public final class PitTabComplete extends TabComplete {
    /** Install after LiteBukkitFactory.builder(), which creates its default listener eagerly. */
    public static LiteBukkitSettings install(LiteBukkitSettings settings) {
        TabComplete previous = settings.tabCompleter();
        if (previous instanceof PitTabComplete) return settings;
        if (previous != null) previous.close();
        return settings.tabCompleter(new PitTabComplete());
    }

    // LiteCommands exposes these lifecycle hooks with package visibility.
    @Override void register(String prefix, BukkitCommand command) {
        super.register(prefix, command);
        command.setSyncTabComplete(true);
    }

    @Override void unregister(String name) {
        BukkitCommand command = listeners.get(name);
        if (command != null) command.setSyncTabComplete(false);
        super.unregister(name);
    }
}
