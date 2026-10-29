package cn.mythicland.framework.runtime;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class RuntimeBootstrap {
    private final Configuration configuration;

    private SelectiveChildFirstClassLoader classLoader;
    private Object runtime;

    private RuntimeBootstrap(Configuration configuration) {
        this.configuration = configuration;
    }

    public static Builder builder(Object plugin, String runtimeClassName) {
        return new Builder(plugin, runtimeClassName);
    }

    public void load() {
        try {
            this.prepareRuntime();
            this.invoke(this.configuration.loadMethod(), Void.class);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            this.closeAfterFailure(exception);
            throw new IllegalStateException(
                    "Failed to load " + this.configuration.runtimeName() + " runtime",
                    exception
            );
        } catch (Error error) {
            this.closeAfterFailure(error);
            throw error;
        }
    }

    public void enable() {
        this.invoke(this.configuration.enableMethod(), Void.class);
    }

    public void reload() {
        this.invoke(this.configuration.reloadMethod(), Void.class);
    }

    public void disable() {
        try {
            if (this.runtime != null) this.invoke(this.configuration.unloadMethod(), Void.class);
        } finally {
            this.runtime = null;
            if (this.classLoader == null) return;
            try {
                this.classLoader.close();
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Failed to close " + this.configuration.runtimeName() + " runtime classloader",
                        exception
                );
            } finally {
                this.classLoader = null;
            }
        }
    }

    public <T> T invoke(String methodName, Class<T> returnType) {
        if (this.runtime == null) {
            throw new IllegalStateException(this.configuration.runtimeName() + " runtime is not loaded");
        }
        try {
            Method method = this.runtime.getClass().getMethod(methodName);
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(this.classLoader);
                Object result = method.invoke(this.runtime);
                if (returnType == Void.class || result == null) return null;
                return returnType.cast(result);
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Cannot access " + this.configuration.runtimeName() + " runtime lifecycle: " + methodName,
                    exception
            );
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException(
                    this.configuration.runtimeName() + " runtime lifecycle failed: " + methodName,
                    cause
            );
        }
    }

    private void prepareRuntime() throws ReflectiveOperationException {
        if (this.runtime != null) return;
        Class<?> pluginType = this.configuration.plugin().getClass();
        List<URL> urls = new ArrayList<>();
        urls.add(this.pluginJarUrl(pluginType));
        urls.addAll(new RuntimeLibraryLoader(
                this.configuration.dataDirectory(),
                pluginType.getClassLoader(),
                this.configuration.manifestResource(),
                this.configuration.infoLogger(),
                this.configuration.warningLogger()
        ).resolveLibraries(this.configuration.activePlatforms()));
        this.classLoader = new SelectiveChildFirstClassLoader(
                urls.toArray(URL[]::new),
                pluginType.getClassLoader(),
                this.configuration.childFirstPackages(),
                this.configuration.parentFirstClasses(),
                this.configuration.parentFirstPackages(),
                this.configuration.parentFirstPredicate()
        );
        Class<?> runtimeType = Class.forName(
                this.configuration.runtimeClassName(),
                true,
                this.classLoader
        );
        this.runtime = runtimeType.getConstructor(pluginType).newInstance(this.configuration.plugin());
    }

    private URL pluginJarUrl(Class<?> pluginType) {
        CodeSource codeSource = pluginType.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException(
                    "Unable to resolve " + this.configuration.runtimeName() + " plugin JAR"
            );
        }
        return codeSource.getLocation();
    }

    private void closeAfterFailure(Throwable failure) {
        this.runtime = null;
        if (this.classLoader == null) return;
        try {
            this.classLoader.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
        this.classLoader = null;
    }

    public static final class Builder {
        private final Object plugin;
        private final String runtimeClassName;

        private String runtimeName;
        private Path dataDirectory;
        private String manifestResource;
        private Set<String> activePlatforms = Set.of();
        private List<String> childFirstPackages = FrameworkRuntimeClassLoading.childFirstPackages(List.of());
        private List<String> parentFirstClasses = FrameworkRuntimeClassLoading.parentFirstClasses(List.of());
        private List<String> parentFirstPackages = List.of();
        private Predicate<String> parentFirstPredicate = ignored -> false;
        private Consumer<String> infoLogger;
        private Consumer<String> warningLogger;
        private String loadMethod = "load";
        private String enableMethod = "enable";
        private String reloadMethod = "reload";
        private String unloadMethod = "unload";

        private Builder(Object plugin, String runtimeClassName) {
            this.plugin = plugin;
            this.runtimeClassName = runtimeClassName;
            this.runtimeName = plugin.getClass().getSimpleName();
        }

        public Builder runtimeName(String runtimeName) {
            this.runtimeName = runtimeName;
            return this;
        }

        public Builder libraries(
                Path dataDirectory,
                String manifestResource,
                Set<String> activePlatforms
        ) {
            this.dataDirectory = dataDirectory;
            this.manifestResource = manifestResource;
            this.activePlatforms = Set.copyOf(activePlatforms);
            return this;
        }

        public Builder loggers(Consumer<String> infoLogger, Consumer<String> warningLogger) {
            this.infoLogger = infoLogger;
            this.warningLogger = warningLogger;
            return this;
        }

        public Builder childFirstPackages(Collection<String> childFirstPackages) {
            this.childFirstPackages = FrameworkRuntimeClassLoading.childFirstPackages(childFirstPackages);
            return this;
        }

        public Builder parentFirstClasses(Collection<String> parentFirstClasses) {
            this.parentFirstClasses = FrameworkRuntimeClassLoading.parentFirstClasses(parentFirstClasses);
            return this;
        }

        public Builder parentFirstPackages(Collection<String> parentFirstPackages) {
            this.parentFirstPackages = List.copyOf(parentFirstPackages);
            return this;
        }

        public Builder parentFirstPredicate(Predicate<String> parentFirstPredicate) {
            this.parentFirstPredicate = parentFirstPredicate;
            return this;
        }

        public Builder classLoading(FrameworkRuntimeClassLoading.ClassLoadingPreset preset) {
            this.childFirstPackages = preset.childFirstPackages();
            this.parentFirstClasses = preset.parentFirstClasses();
            this.parentFirstPackages = preset.parentFirstPackages();
            this.parentFirstPredicate = preset.parentFirstPredicate();
            return this;
        }

        public Builder lifecycleMethods(
                String loadMethod,
                String enableMethod,
                String reloadMethod,
                String unloadMethod
        ) {
            this.loadMethod = loadMethod;
            this.enableMethod = enableMethod;
            this.reloadMethod = reloadMethod;
            this.unloadMethod = unloadMethod;
            return this;
        }

        public RuntimeBootstrap build() {
            return new RuntimeBootstrap(new Configuration(
                    this.plugin,
                    this.runtimeName,
                    this.runtimeClassName,
                    this.dataDirectory,
                    this.manifestResource,
                    this.activePlatforms,
                    this.childFirstPackages,
                    this.parentFirstClasses,
                    this.parentFirstPackages,
                    this.parentFirstPredicate,
                    this.infoLogger,
                    this.warningLogger,
                    this.loadMethod,
                    this.enableMethod,
                    this.reloadMethod,
                    this.unloadMethod
            ));
        }
    }

    private record Configuration(
            Object plugin,
            String runtimeName,
            String runtimeClassName,
            Path dataDirectory,
            String manifestResource,
            Set<String> activePlatforms,
            List<String> childFirstPackages,
            List<String> parentFirstClasses,
            List<String> parentFirstPackages,
            Predicate<String> parentFirstPredicate,
            Consumer<String> infoLogger,
            Consumer<String> warningLogger,
            String loadMethod,
            String enableMethod,
            String reloadMethod,
            String unloadMethod
    ) {
    }
}
