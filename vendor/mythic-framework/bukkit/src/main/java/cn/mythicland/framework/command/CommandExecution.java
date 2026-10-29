package cn.mythicland.framework.command;

@FunctionalInterface
public interface CommandExecution {
    void execute(CommandContext context);
}
