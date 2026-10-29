package cn.mythicland.framework.runtime;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public final class SelectiveChildFirstClassLoader extends URLClassLoader {
    private final List<String> childFirstPackages;
    private final List<String> parentFirstClasses;
    private final List<String> parentFirstPackages;
    private final Predicate<String> parentFirstPredicate;

    static {
        registerAsParallelCapable();
    }

    public SelectiveChildFirstClassLoader(
            URL[] urls,
            ClassLoader parent,
            Collection<String> childFirstPackages,
            Collection<String> parentFirstClasses,
            Collection<String> parentFirstPackages
    ) {
        this(
                urls,
                parent,
                childFirstPackages,
                parentFirstClasses,
                parentFirstPackages,
                ignored -> false
        );
    }

    public SelectiveChildFirstClassLoader(
            URL[] urls,
            ClassLoader parent,
            Collection<String> childFirstPackages,
            Collection<String> parentFirstClasses,
            Collection<String> parentFirstPackages,
            Predicate<String> parentFirstPredicate
    ) {
        super(urls, parent);
        this.childFirstPackages = List.copyOf(childFirstPackages);
        this.parentFirstClasses = List.copyOf(parentFirstClasses);
        this.parentFirstPackages = List.copyOf(parentFirstPackages);
        this.parentFirstPredicate = parentFirstPredicate;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (this.getClassLoadingLock(name)) {
            Class<?> loaded = this.findLoadedClass(name);
            if (loaded == null && this.shouldUseChildFirst(name)) {
                try {
                    loaded = this.findClass(name);
                } catch (ClassNotFoundException ignored) {
                }
            }
            if (loaded == null) loaded = super.loadClass(name, false);
            if (resolve) this.resolveClass(loaded);
            return loaded;
        }
    }

    boolean shouldUseChildFirst(String className) {
        if (this.parentFirstPredicate.test(className)) return false;
        if (this.parentFirstClasses.contains(className)) return false;
        for (String packageName : this.parentFirstPackages) {
            if (className.startsWith(packageName)) return false;
        }
        for (String packageName : this.childFirstPackages) {
            if (className.startsWith(packageName)) return true;
        }
        return false;
    }
}
