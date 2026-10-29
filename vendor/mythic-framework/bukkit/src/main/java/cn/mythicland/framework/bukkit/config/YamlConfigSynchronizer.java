package cn.mythicland.framework.bukkit.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class YamlConfigSynchronizer {
    private YamlConfigSynchronizer() {
    }

    public static boolean synchronize(ConfigurationSection configuration, ConfigurationSection defaults) {
        boolean changed = removeUnknown(configuration, defaults.getKeys(true));
        return backfill(configuration, defaults) || changed;
    }

    public static boolean backfill(ConfigurationSection configuration, ConfigurationSection defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            if (defaults.isConfigurationSection(key)) {
                if (configuration.contains(key) && !configuration.isConfigurationSection(key)) {
                    throw new ConfigException(configuration.getCurrentPath() + "." + key + " must be a section");
                }
                ConfigurationSection section = configuration.getConfigurationSection(key);
                if (section == null) {
                    section = configuration.createSection(key);
                    changed = true;
                }
                changed |= backfill(section, defaults.getConfigurationSection(key));
            } else if (!configuration.contains(key)) {
                configuration.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
    }

    public static boolean removeUnknown(ConfigurationSection configuration, Set<String> supportedPaths) {
        boolean changed = false;
        for (String path : List.copyOf(configuration.getKeys(true))) {
            if (!configuration.contains(path)) continue;
            if (supportedPaths.stream().anyMatch(pattern -> supports(pattern, path))) continue;
            configuration.set(path, null);
            changed = true;
        }
        return changed;
    }

    public static Set<String> paths(ConfigurationSection defaults, Set<String> additionalPaths) {
        Set<String> paths = new HashSet<>(defaults.getKeys(true));
        paths.addAll(additionalPaths);
        return paths;
    }

    private static boolean supports(String pattern, String path) {
        String[] expected = pattern.split("\\."), actual = path.split("\\.");
        if (actual.length > expected.length) return false;
        for (int index = 0; index < actual.length; index++) {
            if (!expected[index].equals("*") && !expected[index].equals(actual[index])) return false;
        }
        return true;
    }
}
