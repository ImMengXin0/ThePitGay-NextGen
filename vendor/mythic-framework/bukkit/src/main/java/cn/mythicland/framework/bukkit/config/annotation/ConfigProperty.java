package cn.mythicland.framework.bukkit.config.annotation;

import java.lang.annotation.*;

/**
 * Binds one configuration field to a stable YAML path.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigProperty {
    /**
     * Absolute path relative to the configuration file root.
     */
    String path();
}
