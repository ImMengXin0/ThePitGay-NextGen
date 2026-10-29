package cn.mythicland.framework.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

@FunctionalInterface
public interface SuggestionProvider {
    static SuggestionProvider empty() {
        return (
                sender,
                context) -> List.of();
    }

    static SuggestionProvider onlinePlayers() {
        return (
                sender,
                context) -> {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        };
    }

    static SuggestionProvider staticValues(String... values) {
        return (
                sender,
                context) -> values == null ? List.of() : List.of(values);
    }

    static SuggestionProvider supplied(Supplier<? extends Collection<String>> supplier) {
        return (
                sender,
                context) -> {
            Collection<String> values = supplier.get();
            if (values == null || values.isEmpty()) return List.of();
            return List.copyOf(values);
        };
    }

    List<String> suggestions(CommandSender sender, CommandContext context);
}
