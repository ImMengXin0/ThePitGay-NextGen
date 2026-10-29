package cn.mythicland.framework.command;

import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

public record CommandContext(CommandSender sender, String label, List<String> arguments) {
    public CommandContext(CommandSender sender, String label, List<String> arguments) {
        this.sender = sender;
        this.label = label;
        this.arguments = List.copyOf(arguments);
    }

    public static CommandContext of(CommandSender sender, String label, String[] arguments) {
        return new CommandContext(
                sender,
                label,
                arguments == null ? List.of() : Arrays.asList(arguments)
        );
    }

    public int argumentCount() {
        return this.arguments.size();
    }

    public String argument(int index) {
        return this.arguments.get(index);
    }

    public String optionalArgument(int index) {
        if (index < 0 || index >= this.arguments.size()) return null;
        return this.arguments.get(index);
    }
}
