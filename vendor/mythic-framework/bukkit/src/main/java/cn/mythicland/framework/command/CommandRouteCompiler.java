package cn.mythicland.framework.command;

import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.ComponentResolver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

@InjectComponent
public final class CommandRouteCompiler {
    private final ComponentResolver componentResolver;

    public CommandRouteCompiler(ComponentResolver componentResolver) {
        this.componentResolver = componentResolver;
    }

    public CommandSpec compile(AbstractCommand command) {
        List<CommandSpec> compiled = this.compileAll(command);
        if (compiled.size() != 1) {
            throw new IllegalStateException(
                    "Command component declares multiple command roots: " + command.getClass().getName()
            );
        }
        return compiled.get(0);
    }

    public List<CommandSpec> compileAll(AbstractCommand command) {
        if (command == null) throw new IllegalArgumentException("Command component cannot be null");

        List<Method> routeMethods = this.routeMethods(command.getClass());
        if (routeMethods.isEmpty()) {
            throw new IllegalStateException(
                    "Command component has no @CommandRoute methods: " + command.getClass().getName()
            );
        }

        Map<String, CommandSpec> compiledByRoot = new LinkedHashMap<>();
        for (Method method : routeMethods) {
            CommandRoute route = method.getAnnotation(CommandRoute.class);
            this.validateMethod(method, route);
            CommandSpec routeSpec = CommandSpec.of(this.compileRoute(command, method, route));
            compiledByRoot.merge(routeSpec.rootName().toLowerCase(Locale.ROOT), routeSpec, CommandSpec::merge);
        }
        return List.copyOf(compiledByRoot.values());
    }

    private List<Method> routeMethods(Class<?> commandType) {
        Map<String, Method> methodsBySignature = new LinkedHashMap<>();
        for (Class<?> type = commandType; type != null && type != AbstractCommand.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(CommandRoute.class)) continue;
                methodsBySignature.putIfAbsent(this.methodSignature(method), method);
            }
        }
        List<Method> methods = new ArrayList<>(methodsBySignature.values());
        methods.sort(Comparator.comparing(this::methodSignature));
        return methods;
    }

    private String methodSignature(Method method) {
        return method.getName() + Arrays.toString(method.getParameterTypes());
    }

    private void validateMethod(Method method, CommandRoute route) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException("@CommandRoute method must not be static: " + method);
        }
        if (method.getReturnType() != void.class
                || method.getParameterCount() != 1
                || method.getParameterTypes()[0] != CommandContext.class) {
            throw new IllegalStateException(
                    "@CommandRoute method must have signature void(CommandContext): " + method
            );
        }
        if (route.path().length == 0) {
            throw new IllegalStateException("@CommandRoute path cannot be empty: " + method);
        }
        for (String pathPart : route.path()) {
            if (pathPart == null || pathPart.isBlank()) {
                throw new IllegalStateException("@CommandRoute path contains a blank segment: " + method);
            }
        }
        if (route.minArguments() < 0 || route.maxArguments() < route.minArguments()) {
            throw new IllegalStateException(
                    "Invalid @CommandRoute argument range "
                            + route.minArguments() + ".." + route.maxArguments() + ": " + method
            );
        }
        if (route.executable() && route.usages().length == 0) {
            throw new IllegalStateException("Executable @CommandRoute must declare at least one usage: " + method);
        }

        Map<Integer, CommandArgument> argumentsByIndex = this.argumentsByIndex(method, route);
        for (CommandArgument argument : argumentsByIndex.values()) {
            if (argument.index() >= route.maxArguments() && route.maxArguments() != Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        "@CommandArgument index exceeds route argument range: " + method
                );
            }
            this.validateArgument(argument, method);
        }
        if (route.maxArguments() == Integer.MAX_VALUE && !argumentsByIndex.isEmpty()) {
            throw new IllegalStateException(
                    "Unbounded command routes cannot declare argument suggestions: " + method
            );
        }
    }

    private Map<Integer, CommandArgument> argumentsByIndex(Method method, CommandRoute route) {
        Map<Integer, CommandArgument> argumentsByIndex = new LinkedHashMap<>();
        for (CommandArgument argument : route.arguments()) {
            if (argument.index() < 0) {
                throw new IllegalStateException("@CommandArgument index cannot be negative: " + method);
            }
            if (argumentsByIndex.put(argument.index(), argument) != null) {
                throw new IllegalStateException(
                        "Duplicate @CommandArgument index " + argument.index() + ": " + method
                );
            }
        }
        return argumentsByIndex;
    }

    private void validateArgument(CommandArgument argument, Method method) {
        boolean hasValues = argument.values().length > 0;
        boolean hasProvider = argument.provider() != SuggestionProvider.class;
        switch (argument.source()) {
            case NONE -> {
                if (hasValues || hasProvider) {
                    throw new IllegalStateException(
                            "NONE suggestion source cannot define values or provider: " + method
                    );
                }
            }
            case ONLINE_PLAYERS -> {
                if (hasValues || hasProvider) {
                    throw new IllegalStateException(
                            "ONLINE_PLAYERS suggestion source cannot define values or provider: " + method
                    );
                }
            }
            case STATIC_VALUES -> {
                if (!hasValues || hasProvider) {
                    throw new IllegalStateException(
                            "STATIC_VALUES suggestion source requires values only: " + method
                    );
                }
            }
            case COMPONENT -> {
                if (!hasProvider || hasValues || argument.provider().isInterface()
                        || Modifier.isAbstract(argument.provider().getModifiers())) {
                    throw new IllegalStateException(
                            "COMPONENT suggestion source requires a concrete provider: " + method
                    );
                }
            }
        }
    }

    private CommandNode compileRoute(AbstractCommand command, Method method, CommandRoute route) {
        int leafIndex = route.path().length - 1;
        CommandNode.Builder leaf = CommandNode.literal(route.path()[leafIndex]);
        this.applyMetadata(leaf, route);
        if (route.executable()) {
            leaf.execute(context -> this.invoke(command, method, context));
        }

        CommandNode current = leaf.build();
        for (int index = leafIndex - 1; index >= 0; index--) {
            CommandNode.Builder parent = index == 0
                    ? CommandNode.root(route.path()[index])
                    : CommandNode.literal(route.path()[index]);
            parent.child(current);
            current = parent.build();
        }
        return current;
    }

    private void applyMetadata(CommandNode.Builder builder, CommandRoute route) {
        if (!route.permission().isBlank()) builder.permission(route.permission());
        for (String usage : route.usages()) {
            if (usage == null || usage.isBlank()) {
                throw new IllegalStateException("@CommandRoute usage cannot be blank");
            }
            builder.usage(usage);
        }
        builder.arguments(route.minArguments(), route.maxArguments());
        if (route.maxArguments() == Integer.MAX_VALUE) return;

        Map<Integer, CommandArgument> argumentsByIndex = new LinkedHashMap<>();
        for (CommandArgument argument : route.arguments()) argumentsByIndex.put(argument.index(), argument);
        for (int index = 0; index < route.maxArguments(); index++) {
            CommandArgument argument = argumentsByIndex.get(index);
            builder.suggest(argument == null ? SuggestionProvider.empty() : this.createProvider(argument));
        }
    }

    private SuggestionProvider createProvider(CommandArgument argument) {
        return switch (argument.source()) {
            case NONE -> SuggestionProvider.empty();
            case ONLINE_PLAYERS -> SuggestionProvider.onlinePlayers();
            case STATIC_VALUES -> SuggestionProvider.staticValues(argument.values());
            case COMPONENT -> {
                if (this.componentResolver == null) {
                    throw new IllegalStateException(
                            "Component suggestion provider requires a component resolver: " + argument.provider()
                    );
                }
                yield this.componentResolver.resolve(argument.provider());
            }
        };
    }

    private void invoke(AbstractCommand command, Method method, CommandContext context) {
        try {
            if (!method.trySetAccessible()) {
                throw new IllegalStateException("Cannot access @CommandRoute method: " + method);
            }
            method.invoke(command, context);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot invoke @CommandRoute method: " + method, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("@CommandRoute method failed: " + method, cause);
        }
    }
}
