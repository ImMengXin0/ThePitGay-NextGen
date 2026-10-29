package cn.mythicland.framework.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SensitiveCommandLogFilter implements AutoCloseable {
    private final LoggerConfig loggerConfiguration;
    private final AbstractFilter filter;

    public SensitiveCommandLogFilter(
            ClassLoader loggingClassLoader,
            String loggerName,
            Set<String> commands
    ) {
        String alternatives = commands.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        Pattern sensitive = Pattern.compile("(?i).* -> executed command /(?:[^\\s:]+:)?(?:" + alternatives + ")(?:\\s.*)?");
        loggerConfiguration = ((LoggerContext) LogManager.getContext(loggingClassLoader, false)).getConfiguration().getLoggerConfig(loggerName);
        filter = new AbstractFilter() {
            @Override
            public Result filter(LogEvent event) {
                return loggerName.equals(event.getLoggerName()) && sensitive.matcher(event.getMessage().getFormattedMessage()).matches()
                        ? Result.DENY : Result.NEUTRAL;
            }
        };
        loggerConfiguration.addFilter(filter);
    }

    @Override
    public void close() {
        loggerConfiguration.removeFilter(filter);
    }
}
