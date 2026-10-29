package cn.mythicland.framework.menu;

import java.lang.annotation.*;

/**
 * Binds one fixed menu slot to a button-producing method.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MenuSlot {
    /**
     * Zero-based Bukkit inventory slot in the range 0..53.
     */
    int slot();
}
