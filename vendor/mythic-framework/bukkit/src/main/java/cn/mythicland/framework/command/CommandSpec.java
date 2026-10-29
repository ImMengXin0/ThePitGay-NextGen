package cn.mythicland.framework.command;

public final class CommandSpec {
    private final CommandNode root;

    private CommandSpec(CommandNode root) {
        this.root = root;
    }

    public static CommandSpec of(CommandNode root) {
        return new CommandSpec(root);
    }

    public String rootName() {
        return this.root.name();
    }

    public CommandNode root() {
        return this.root;
    }

    public CommandSpec merge(CommandSpec other) {
        if (other == null) return this;
        return new CommandSpec(this.root.merge(other.root));
    }
}
