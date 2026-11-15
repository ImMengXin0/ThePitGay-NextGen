package net.mizukilab.pit.util.configuration;

import cn.mythicland.framework.bukkit.config.YamlConfigSynchronizer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Strict loading and additive upgrades. Invalid files must never be replaced with defaults. */
public final class ConfigFiles {
    private ConfigFiles() { }

    public static YamlConfiguration load(JavaPlugin plugin, File file, String resource) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            if (file.exists()) config.load(file);
            if (resource != null) {
                try (InputStream input = plugin.getResource(resource)) {
                    if (input != null) {
                        YamlConfiguration defaults = new YamlConfiguration();
                        defaults.load(new InputStreamReader(input, StandardCharsets.UTF_8));
                        if (YamlConfigSynchronizer.backfill(config, defaults)) config.save(file);
                    }
                }
            }
            return config;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load or update configuration: " + file, exception);
        }
    }
}
