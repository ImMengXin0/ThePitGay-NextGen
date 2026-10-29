package cn.mythicland.framework.bukkit.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public abstract class AbstractYamlConfig {
    private final JavaPlugin plugin;
    private final String fileName;
    private File file;
    private FileConfiguration configuration;
    private Set<String> configuredPaths = Set.of();
    private boolean changed;
    private Set<String> defaultPaths = Set.of();

    protected AbstractYamlConfig(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
    }

    public final void load() {
        this.file = new File(this.plugin.getDataFolder(), this.fileName);
        if (!this.file.exists()) {
            File parent = this.file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new ConfigException("Failed to create configuration directory: " + parent);
            }
            this.plugin.saveResource(this.fileName, false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(this.file);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new ConfigException("Failed to load configuration: " + this.fileName, exception);
        }
        this.configuration = loaded;
        this.configuredPaths = this.collectConfiguredPaths(this.configuration);
        this.changed = false;
        this.defaultPaths = Set.of();
        if (this.synchronizeDefaults()) this.backfillMissingDefaults();
        new ConfigBinder(this.fileName, this.configuration).bind(this);
        this.checkConfig();
        if (!this.defaultPaths.isEmpty()) {
            Set<String> paths = new HashSet<>(this.defaultPaths);
            paths.addAll(this.additionalConfigPaths());
            this.changed |= YamlConfigSynchronizer.removeUnknown(this.configuration, paths);
        }
        this.saveIfChanged();
    }

    protected abstract void checkConfig();

    protected boolean synchronizeDefaults() {
        return true;
    }

    protected Set<String> additionalConfigPaths() {
        return Set.of();
    }

    protected final FileConfiguration configuration() {
        if (this.configuration == null) throw new ConfigException("Configuration not loaded: " + this.fileName);
        return this.configuration;
    }

    protected final Logger logger() {
        return this.plugin.getLogger();
    }

    protected final boolean wasConfigured(String path) {
        return this.configuredPaths.contains(path);
    }

    protected final List<String> listOrDefault(String path, List<String> defaults) {
        if (!this.configuration().contains(path)) {
            this.configuration().set(path, defaults);
            this.changed = true;
            return defaults;
        }
        Object value = this.configuration().get(path);
        if (!(value instanceof List<?> rawList)) {
            throw new ConfigException(this.fileName + ": " + path + " must be a string list");
        }
        List<String> result = new ArrayList<>();
        for (Object entry : rawList) {
            if (!(entry instanceof String stringEntry)) {
                throw new ConfigException(this.fileName + ": " + path + " must contain only strings");
            }
            result.add(stringEntry);
        }
        return List.copyOf(result);
    }

    protected final String stringOrDefault(String path, String defaultValue) {
        if (!this.configuration().contains(path)) {
            this.configuration().set(path, defaultValue);
            this.changed = true;
            return defaultValue;
        }
        Object value = this.configuration().get(path);
        if (!(value instanceof String stringValue)) {
            throw new ConfigException(this.fileName + ": " + path + " must be a string");
        }
        return stringValue;
    }

    protected final int intOrDefault(String path, int defaultValue) {
        if (!this.configuration().contains(path)) {
            this.configuration().set(path, defaultValue);
            this.changed = true;
            return defaultValue;
        }
        Object value = this.configuration().get(path);
        if (!(value instanceof Number numberValue)) {
            throw new ConfigException(this.fileName + ": " + path + " must be an integer");
        }
        int intValue = numberValue.intValue();
        if (Double.compare(numberValue.doubleValue(), intValue) != 0) {
            throw new ConfigException(this.fileName + ": " + path + " must be an integer");
        }
        return intValue;
    }

    protected final long longOrDefault(String path, long defaultValue) {
        if (!this.configuration().contains(path)) {
            this.configuration().set(path, defaultValue);
            this.changed = true;
            return defaultValue;
        }
        Object value = this.configuration().get(path);
        if (!(value instanceof Number numberValue)) {
            throw new ConfigException(this.fileName + ": " + path + " must be a long");
        }
        long longValue = numberValue.longValue();
        if (Double.compare(numberValue.doubleValue(), longValue) != 0) {
            throw new ConfigException(this.fileName + ": " + path + " must be a long");
        }
        return longValue;
    }

    protected final double doubleOrDefault(String path, double defaultValue) {
        if (!this.configuration().contains(path)) {
            this.configuration().set(path, defaultValue);
            this.changed = true;
            return defaultValue;
        }
        Object value = this.configuration().get(path);
        if (!(value instanceof Number numberValue)) {
            throw new ConfigException(this.fileName + ": " + path + " must be a number");
        }
        return numberValue.doubleValue();
    }

    protected final boolean booleanOrDefault(String path, boolean defaultValue) {
        if (!this.configuration().contains(path)) {
            this.configuration().set(path, defaultValue);
            this.changed = true;
            return defaultValue;
        }
        Object value = this.configuration().get(path);
        if (!(value instanceof Boolean booleanValue)) {
            throw new ConfigException(this.fileName + ": " + path + " must be a boolean");
        }
        return booleanValue;
    }

    protected final void markChanged() {
        this.changed = true;
    }

    protected final void saveChanges() {
        this.saveIfChanged();
    }

    private void saveIfChanged() {
        if (!this.changed || this.file == null || this.configuration == null) return;
        try {
            this.configuration.save(this.file);
            this.changed = false;
        } catch (IOException exception) {
            throw new ConfigException("Failed to save configuration: " + this.fileName, exception);
        }
    }

    private void backfillMissingDefaults() {
        try (InputStream input = this.plugin.getResource(this.fileName)) {
            if (input == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            );
            this.defaultPaths = defaults.getKeys(true);
            this.backfillMissingDefaults("", defaults);
        } catch (IOException exception) {
            throw new ConfigException("Failed to load bundled configuration defaults: " + this.fileName, exception);
        }
    }

    private void backfillMissingDefaults(String prefix, ConfigurationSection defaults) {
        for (String key : defaults.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (defaults.isConfigurationSection(key)) {
                if (this.configuration().contains(path) && !this.configuration().isConfigurationSection(path)) {
                    throw new ConfigException(this.fileName + ": " + path + " must be a section");
                }
                this.backfillMissingDefaults(path, defaults.getConfigurationSection(key));
                continue;
            }
            if (this.configuration().contains(path)) continue;
            this.configuration().set(path, defaults.get(key));
            this.changed = true;
        }
    }

    private Set<String> collectConfiguredPaths(ConfigurationSection section) {
        Set<String> paths = new HashSet<>();
        this.collectConfiguredPaths("", section, paths);
        return Set.copyOf(paths);
    }

    private void collectConfiguredPaths(String prefix, ConfigurationSection section, Set<String> paths) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            paths.add(path);
            if (!section.isConfigurationSection(key)) continue;
            this.collectConfiguredPaths(path, section.getConfigurationSection(key), paths);
        }
    }
}
