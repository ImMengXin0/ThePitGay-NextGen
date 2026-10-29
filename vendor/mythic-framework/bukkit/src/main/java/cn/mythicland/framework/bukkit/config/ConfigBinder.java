package cn.mythicland.framework.bukkit.config;

import cn.mythicland.framework.bukkit.config.annotation.ConfigProperty;
import org.bukkit.configuration.ConfigurationSection;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ConfigBinder {
    private final String fileName;
    private final ConfigurationSection configuration;

    public ConfigBinder(String fileName, ConfigurationSection configuration) {
        this.fileName = fileName;
        this.configuration = configuration;
    }

    public void bind(Object target) {
        if (target == null) throw new IllegalArgumentException("Config binding target cannot be null");
        Set<String> paths = new HashSet<>();
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                ConfigProperty property = field.getAnnotation(ConfigProperty.class);
                if (property == null) continue;
                String path = property.path();
                if (path == null || path.isBlank()) {
                    throw this.failure(field, path, "path must not be blank");
                }
                if (!paths.add(path)) {
                    throw this.failure(field, path, "duplicate configuration path");
                }
                this.validateField(field, path);
                if (!this.configuration.contains(path)) {
                    throw this.failure(field, path, "configuration value is missing");
                }
                Object rawValue = this.configuration.get(path);
                Object value = this.convert(field, path, rawValue);
                try {
                    if (!field.trySetAccessible()) {
                        throw this.failure(field, path, "field is not accessible");
                    }
                    field.set(target, value);
                } catch (IllegalAccessException exception) {
                    throw this.failure(field, path, "failed to assign value", exception);
                }
            }
        }
    }

    private void validateField(Field field, String path) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
            throw this.failure(field, path, "field must not be static or final");
        }
    }

    private Object convert(Field field, String path, Object rawValue) {
        Class<?> targetType = field.getType();
        if (targetType == String.class) return this.requireType(field, path, rawValue, String.class);
        if (targetType == boolean.class || targetType == Boolean.class) {
            return this.requireType(field, path, rawValue, Boolean.class);
        }
        if (targetType == int.class || targetType == Integer.class) {
            return this.integerValue(field, path, rawValue, Integer.MIN_VALUE, Integer.MAX_VALUE).intValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return this.integerValue(field, path, rawValue, Long.MIN_VALUE, Long.MAX_VALUE);
        }
        if (targetType == double.class || targetType == Double.class) {
            if (!(rawValue instanceof Number numberValue)) {
                throw this.failure(field, path, "value must be a number");
            }
            return numberValue.doubleValue();
        }
        if (targetType.isEnum()) return this.enumValue(field, path, rawValue, targetType);
        if (List.class.isAssignableFrom(targetType)) return this.stringList(field, path, rawValue);
        throw this.failure(field, path, "unsupported field type: " + targetType.getName());
    }

    private <T> T requireType(Field field, String path, Object rawValue, Class<T> expectedType) {
        if (!expectedType.isInstance(rawValue)) {
            throw this.failure(field, path, "value must be " + expectedType.getSimpleName());
        }
        return expectedType.cast(rawValue);
    }

    private Number integerValue(Field field, String path, Object rawValue, long minimum, long maximum) {
        if (!(rawValue instanceof Number numberValue)) {
            throw this.failure(field, path, "value must be an integer");
        }
        double doubleValue = numberValue.doubleValue();
        long longValue = numberValue.longValue();
        if (!Double.isFinite(doubleValue) || doubleValue != longValue
                || longValue < minimum || longValue > maximum) {
            throw this.failure(field, path, "value must be an integer in range");
        }
        return longValue;
    }

    private Object enumValue(Field field, String path, Object rawValue, Class<?> targetType) {
        if (!(rawValue instanceof String stringValue)) {
            throw this.failure(field, path, "value must be a string");
        }
        for (Object constant : targetType.getEnumConstants()) {
            Enum<?> enumValue = (Enum<?>) constant;
            if (enumValue.name().equalsIgnoreCase(stringValue.trim())) return enumValue;
        }
        throw this.failure(field, path, "unsupported enum value: " + stringValue);
    }

    private List<String> stringList(Field field, String path, Object rawValue) {
        if (!(rawValue instanceof List<?> rawList)) {
            throw this.failure(field, path, "value must be a string list");
        }
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments().length == 1
                && parameterizedType.getActualTypeArguments()[0] != String.class) {
            throw this.failure(field, path, "only List<String> is supported");
        }
        List<String> values = new ArrayList<>(rawList.size());
        for (Object entry : rawList) {
            if (!(entry instanceof String stringEntry)) {
                throw this.failure(field, path, "value must contain only strings");
            }
            values.add(stringEntry);
        }
        return List.copyOf(values);
    }

    private ConfigException failure(Field field, String path, String detail) {
        return new ConfigException(this.fileName + ": " + path + " (" + field + "): " + detail);
    }

    private ConfigException failure(Field field, String path, String detail, Throwable cause) {
        return new ConfigException(this.fileName + ": " + path + " (" + field + "): " + detail, cause);
    }
}
