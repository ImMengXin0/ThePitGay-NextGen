package cn.mythicland.framework.command;

import java.lang.annotation.*;

/**
 * Declares one executable or structural command route.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CommandRoute {
    /**
     * Literal path segments from root to this route.
     */
    String[] path();

    /**
     * Permission required by this route, or blank when unrestricted.
     */
    String permission() default "";

    /**
     * Usage strings exposed by the route.
     */
    String[] usages() default {};

    /**
     * Minimum number of arguments accepted by the route.
     */
    int minArguments() default 0;

    /**
     * Maximum number of arguments accepted by the route.
     */
    int maxArguments() default 0;

    /**
     * Whether the route invokes its method when it is a leaf.
     */
    boolean executable() default true;

    /**
     * Completion declarations for argument positions.
     */
    CommandArgument[] arguments() default {};
}
