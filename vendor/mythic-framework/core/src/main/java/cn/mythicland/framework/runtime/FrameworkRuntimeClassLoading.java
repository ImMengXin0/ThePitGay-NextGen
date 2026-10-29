package cn.mythicland.framework.runtime;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

public final class FrameworkRuntimeClassLoading {
    private static final String FRAMEWORK_PACKAGE = "cn.mythicland.framework.";
    private static final String COMPONENT_ROLE = "cn.mythicland.framework.component.ComponentRole";
    private static final List<String> BUKKIT_PARENT_FIRST_PACKAGES = List.of(
            "org.bukkit.",
            "org.spigotmc.",
            "net.md_5.bungee.api."
    );
    private static final List<String> VELOCITY_PARENT_FIRST_PACKAGES = List.of(
            "com.velocitypowered.api."
    );

    private FrameworkRuntimeClassLoading() {
    }

    public static List<String> childFirstPackages(Collection<String> applicationPackages) {
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        packages.add(FRAMEWORK_PACKAGE);
        packages.addAll(applicationPackages);
        return List.copyOf(packages);
    }

    public static List<String> parentFirstClasses(Collection<String> contractClasses) {
        LinkedHashSet<String> classes = new LinkedHashSet<>();
        classes.add(COMPONENT_ROLE);
        classes.addAll(contractClasses);
        return List.copyOf(classes);
    }

    public static ClassLoadingPreset bukkit(
            Collection<String> applicationPackages,
            Collection<String> contractClasses,
            Collection<String> sharedParentFirstPackages
    ) {
        return preset(
                applicationPackages,
                contractClasses,
                merge(BUKKIT_PARENT_FIRST_PACKAGES, sharedParentFirstPackages),
                ignored -> false
        );
    }

    public static ClassLoadingPreset bukkit(
            Collection<String> applicationPackages,
            Collection<String> contractClasses
    ) {
        return bukkit(applicationPackages, contractClasses, List.of());
    }

    public static ClassLoadingPreset velocity(
            Collection<String> applicationPackages,
            Collection<String> contractClasses,
            Collection<String> sharedParentFirstPackages
    ) {
        return preset(
                applicationPackages,
                contractClasses,
                merge(VELOCITY_PARENT_FIRST_PACKAGES, sharedParentFirstPackages),
                ignored -> false
        );
    }

    public static ClassLoadingPreset velocity(
            Collection<String> applicationPackages,
            Collection<String> contractClasses
    ) {
        return velocity(applicationPackages, contractClasses, List.of());
    }

    public static ClassLoadingPreset preset(
            Collection<String> applicationPackages,
            Collection<String> contractClasses,
            Collection<String> parentFirstPackages,
            Predicate<String> parentFirstPredicate
    ) {
        return new ClassLoadingPreset(
                childFirstPackages(applicationPackages),
                parentFirstClasses(contractClasses),
                List.copyOf(parentFirstPackages),
                parentFirstPredicate
        );
    }

    private static List<String> merge(
            Collection<String> first,
            Collection<String> second
    ) {
        LinkedHashSet<String> values = new LinkedHashSet<>(first);
        values.addAll(second);
        return List.copyOf(values);
    }

    public record ClassLoadingPreset(
            List<String> childFirstPackages,
            List<String> parentFirstClasses,
            List<String> parentFirstPackages,
            Predicate<String> parentFirstPredicate
    ) {
        public ClassLoadingPreset {
            childFirstPackages = List.copyOf(childFirstPackages);
            parentFirstClasses = List.copyOf(parentFirstClasses);
            parentFirstPackages = List.copyOf(parentFirstPackages);
        }
    }
}
