package cn.mythicland.framework.command;

import java.lang.annotation.*;

/**
 * Declares completion metadata for one command argument index.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface CommandArgument {
    /**
     * Zero-based command argument index.
     */
    int index();

    /**
     * Completion source used for this argument.
     */
    SuggestionSource source() default SuggestionSource.NONE;

    /**
     * Static values when {@link SuggestionSource#STATIC_VALUES} is selected.
     */
    String[] values() default {};

    /**
     * Component provider when {@link SuggestionSource#COMPONENT} is selected.
     */
    Class<? extends SuggestionProvider> provider() default SuggestionProvider.class;
}
