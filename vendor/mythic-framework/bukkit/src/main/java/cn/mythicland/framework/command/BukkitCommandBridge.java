package cn.mythicland.framework.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public final class BukkitCommandBridge implements CommandExecutor, TabCompleter {
    private static final String NO_PERMISSION = "§c你没有权限使用这个命令。";

    private final JavaPlugin plugin;
    private final CommandSpec spec;

    public BukkitCommandBridge(JavaPlugin plugin, CommandSpec spec) {
        this.plugin = plugin;
        this.spec = spec;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        try {
            this.execute(sender, label, arguments == null ? new String[0] : arguments);
        } catch (Exception exception) {
            this.plugin.getLogger().log(
                    Level.SEVERE,
                    "Unhandled command exception: /" + label + " " + String.join(" ", arguments),
                    exception
            );
            sender.sendMessage("§c命令执行失败, 异常已写入后台日志。");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        return this.suggest(sender, alias, arguments == null ? new String[0] : arguments);
    }

    private void execute(CommandSender sender, String label, String[] arguments) {
        CommandNode current = this.spec.root();
        int index = 0;
        while (index < arguments.length) {
            CommandNode child = current.child(arguments[index]);
            if (child == null) break;
            if (!this.hasPermission(sender, child)) {
                sender.sendMessage(NO_PERMISSION);
                return;
            }
            current = child;
            index++;
        }
        if (!this.hasPermission(sender, current)) {
            sender.sendMessage(NO_PERMISSION);
            return;
        }
        List<String> remaining = Arrays.asList(arguments).subList(index, arguments.length);
        if (!current.canExecute()
                || remaining.size() < current.minArguments()
                || remaining.size() > current.maxArguments()) {
            this.sendUsage(sender, current);
            return;
        }
        current.execution().execute(new CommandContext(sender, label, remaining));
    }

    private List<String> suggest(CommandSender sender, String label, String[] arguments) {
        CommandNode current = this.spec.root();
        int consumed = 0;
        int pathTokens = Math.max(0, arguments.length - 1);
        for (int index = 0; index < pathTokens; index++) {
            CommandNode child = current.child(arguments[index]);
            if (child == null) {
                if (!current.canExecute()) return List.of();
                break;
            }
            if (!this.hasPermission(sender, child)) return List.of();
            current = child;
            consumed++;
        }
        if (!this.hasPermission(sender, current)) return List.of();
        String prefix = arguments.length == 0 ? "" : arguments[arguments.length - 1];
        int argumentIndex = Math.max(0, arguments.length - consumed - 1);
        CommandContext context = new CommandContext(sender, label,
                arguments.length == 0 ? List.of() : Arrays.asList(arguments).subList(consumed, arguments.length));
        Set<String> values = new LinkedHashSet<>();
        for (CommandNode child : current.children()) {
            if (this.hasPermission(sender, child)) values.add(child.name());
        }
        if (current.canExecute() && argumentIndex < current.maxArguments()) {
            List<SuggestionProvider> suggestions = current.argumentSuggestions();
            if (argumentIndex < suggestions.size()) {
                values.addAll(suggestions.get(argumentIndex).suggestions(sender, context));
            }
        }
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) result.add(value);
        }
        return result;
    }

    private void sendUsage(CommandSender sender, CommandNode node) {
        Set<String> usages = new LinkedHashSet<>();
        this.collectUsages(sender, node, usages);
        if (usages.isEmpty() && this.hasDeclaredUsage(node)) {
            sender.sendMessage(NO_PERMISSION);
            return;
        }
        if (usages.isEmpty()) sender.sendMessage("§c用法: /" + node.name());
        for (String usage : usages) sender.sendMessage("§c用法: " + usage);
    }

    private void collectUsages(CommandSender sender, CommandNode node, Set<String> usages) {
        if (!this.hasPermission(sender, node)) return;
        usages.addAll(node.usages());
        for (CommandNode child : node.children()) this.collectUsages(sender, child, usages);
    }

    private boolean hasDeclaredUsage(CommandNode node) {
        if (!node.usages().isEmpty()) return true;
        for (CommandNode child : node.children()) {
            if (this.hasDeclaredUsage(child)) return true;
        }
        return false;
    }

    private boolean hasPermission(CommandSender sender, CommandNode node) {
        return node.permission() == null || node.permission().isBlank() || sender.hasPermission(node.permission());
    }
}
