package cn.mythicland.framework.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class YamlConfigFile {
    private final Path file;
    private final Map<?, ?> values;

    private YamlConfigFile(Path file, Map<?, ?> values) {
        this.file = file;
        this.values = values;
    }

    public static YamlConfigFile load(Path file, ClassLoader classLoader, String resource) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            if (!Files.exists(file)) {
                try (InputStream input = classLoader.getResourceAsStream(resource)) {
                    if (input == null) throw new IllegalStateException("Missing bundled resource: " + resource);
                    Files.copy(input, file);
                }
            }
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Object loaded = new Yaml(new SafeConstructor(options)).load(reader);
                if (!(loaded instanceof Map<?, ?> values)) {
                    throw new IllegalArgumentException(file + " must contain a YAML mapping");
                }
                return new YamlConfigFile(file, values);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load configuration: " + file, exception);
        }
    }

    public List<String> stringList(String key) {
        if (!(this.value(key) instanceof List<?> entries)) {
            throw new IllegalArgumentException(this.file + ": " + key + " must be a list of strings");
        }
        List<String> result = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (!(entry instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(this.file + ": " + key + " must contain non-blank strings");
            }
            result.add(text.trim());
        }
        return List.copyOf(result);
    }

    public boolean booleanValue(String key, boolean defaultValue) {
        Object value = this.value(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean result) return result;
        throw new IllegalArgumentException(this.file + ": " + key + " must be a boolean");
    }

    public String stringValue(String key) {
        Object value = this.value(key);
        if (value instanceof String result) return result;
        throw new IllegalArgumentException(this.file + ": " + key + " must be a string");
    }

    public int intValue(String key, int defaultValue) {
        Object value = this.value(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer result) return result;
        throw new IllegalArgumentException(this.file + ": " + key + " must be an integer");
    }

    private Object value(String key) {
        Object value = this.values;
        for (String part : key.split("\\.")) {
            if (!(value instanceof Map<?, ?> section)) {
                throw new IllegalArgumentException(this.file + ": " + key + " parent must be a mapping");
            }
            if (!section.containsKey(part)) return null;
            value = section.get(part);
            if (value == null) throw new IllegalArgumentException(this.file + ": " + key + " must not be null");
        }
        return value;
    }
}
