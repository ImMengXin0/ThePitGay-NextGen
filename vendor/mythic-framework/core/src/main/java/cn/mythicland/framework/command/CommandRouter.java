package cn.mythicland.framework.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class CommandRouter<S> {
    private final Map<String, Route<S>> routes = new LinkedHashMap<>();

    public CommandRouter<S> route(String name, int arguments, Predicate<S> allowed, BiConsumer<S, String[]> handler) {
        String key = name.toLowerCase(Locale.ROOT);
        if (this.routes.putIfAbsent(key, new Route<>(arguments, allowed, handler)) != null) {
            throw new IllegalArgumentException("Duplicate command route: " + name);
        }
        return this;
    }

    public boolean execute(S source, String[] arguments) {
        if (arguments.length == 0) return false;
        Route<S> route = this.routes.get(arguments[0].toLowerCase(Locale.ROOT));
        if (route == null || arguments.length != route.arguments + 1 || !route.allowed.test(source)) return false;
        route.handler.accept(source, arguments);
        return true;
    }

    public List<String> suggest(S source, String prefix) {
        String input = prefix.toLowerCase(Locale.ROOT);
        return this.routes.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(input) && entry.getValue().allowed.test(source))
                .map(Map.Entry::getKey)
                .toList();
    }

    private record Route<S>(int arguments, Predicate<S> allowed, BiConsumer<S, String[]> handler) {}
}
