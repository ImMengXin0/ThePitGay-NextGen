package cn.mythicland.framework.command;

import java.util.*;

public final class CommandNode {
    private final String name;
    private final String permission;
    private final List<String> usages;
    private final CommandExecution execution;
    private final int minArguments;
    private final int maxArguments;
    private final List<SuggestionProvider> argumentSuggestions;
    private final Map<String, CommandNode> childrenByName;

    private CommandNode(Builder builder) {
        this.name = builder.name;
        this.permission = builder.permission;
        this.usages = List.copyOf(builder.usages);
        this.execution = builder.execution;
        this.minArguments = builder.minArguments;
        this.maxArguments = builder.maxArguments;
        this.argumentSuggestions = List.copyOf(builder.argumentSuggestions);
        this.childrenByName = Collections.unmodifiableMap(new LinkedHashMap<>(builder.childrenByName));
    }

    public static Builder root(String name) {
        return new Builder(name);
    }

    public static Builder literal(String name) {
        return new Builder(name);
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public String name() {
        return this.name;
    }

    public String permission() {
        return this.permission;
    }

    public List<String> usages() {
        return this.usages;
    }

    public boolean canExecute() {
        return this.execution != null;
    }

    public int minArguments() {
        return this.minArguments;
    }

    public int maxArguments() {
        return this.maxArguments;
    }

    public CommandExecution execution() {
        return this.execution;
    }

    public List<SuggestionProvider> argumentSuggestions() {
        return this.argumentSuggestions;
    }

    public Collection<CommandNode> children() {
        return this.childrenByName.values();
    }

    public CommandNode child(String name) {
        if (name == null) return null;
        return this.childrenByName.get(normalize(name));
    }

    public CommandNode merge(CommandNode other) {
        if (!this.name.equalsIgnoreCase(other.name)) {
            throw new IllegalArgumentException(
                    "Cannot merge command nodes with different names: " + this.name + ", " + other.name
            );
        }
        Builder builder = literal(this.name);
        builder.permission(this.permission != null ? this.permission : other.permission);

        LinkedHashSet<String> mergedUsages = new LinkedHashSet<>(this.usages);
        mergedUsages.addAll(other.usages);
        for (String usage : mergedUsages) {
            builder.usage(usage);
        }

        if (this.execution != null && other.execution != null) {
            throw new IllegalStateException("Duplicate command execution for node: " + this.name);
        }
        CommandExecution mergedExecution = this.execution != null ? this.execution : other.execution;
        if (mergedExecution != null) {
            int min = this.execution != null ? this.minArguments : other.minArguments;
            int max = this.execution != null ? this.maxArguments : other.maxArguments;
            builder.arguments(min, max);
            builder.execute(mergedExecution);
            List<SuggestionProvider> suggestions = this.execution != null
                    ? this.argumentSuggestions
                    : other.argumentSuggestions;
            for (SuggestionProvider suggestion : suggestions) {
                builder.suggest(suggestion);
            }
        }

        Map<String, CommandNode> mergedChildren = new LinkedHashMap<>(this.childrenByName);
        for (CommandNode child : other.children()) {
            String normalized = normalize(child.name());
            mergedChildren.compute(normalized, (k, existing) -> existing == null ? child : existing.merge(child));
        }
        for (CommandNode child : mergedChildren.values()) {
            builder.child(child);
        }
        return builder.build();
    }

    public static final class Builder {
        private final String name;
        private final List<String> usages;
        private final List<SuggestionProvider> argumentSuggestions;
        private final Map<String, CommandNode> childrenByName;
        private String permission;
        private CommandExecution execution;
        private int minArguments;
        private int maxArguments;

        private Builder(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Command node name cannot be blank");
            }
            this.name = name;
            this.usages = new ArrayList<>();
            this.minArguments = 0;
            this.maxArguments = 0;
            this.argumentSuggestions = new ArrayList<>();
            this.childrenByName = new LinkedHashMap<>();
        }

        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public Builder usage(String usage) {
            if (usage == null || usage.isBlank()) return this;
            this.usages.add(usage);
            return this;
        }

        public Builder arguments(int exactArguments) {
            return this.arguments(exactArguments, exactArguments);
        }

        public Builder arguments(int minArguments, int maxArguments) {
            if (minArguments < 0 || maxArguments < minArguments) {
                throw new IllegalArgumentException("Invalid argument range: " + minArguments + ".." + maxArguments);
            }
            this.minArguments = minArguments;
            this.maxArguments = maxArguments;
            return this;
        }

        public Builder execute(CommandExecution execution) {
            this.execution = execution;
            return this;
        }

        public Builder suggest(SuggestionProvider suggestionProvider) {
            this.argumentSuggestions.add(suggestionProvider == null ? SuggestionProvider.empty() : suggestionProvider);
            return this;
        }

        public Builder child(CommandNode child) {
            if (child == null) return this;
            this.childrenByName.put(normalize(child.name()), child);
            return this;
        }

        public CommandNode build() {
            return new CommandNode(this);
        }
    }
}
