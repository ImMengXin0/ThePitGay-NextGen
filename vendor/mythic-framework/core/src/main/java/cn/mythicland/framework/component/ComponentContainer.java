package cn.mythicland.framework.component;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class ComponentContainer implements ComponentResolver {
    private final Map<Class<?>, Object> components = new LinkedHashMap<>();
    private final List<ManagedLifecycle> loadedLifecycles = new ArrayList<>();
    private final Set<Class<?>> explicitlyBoundManagedTypes = new LinkedHashSet<>();
    private final ThreadLocal<List<Class<?>>> resolvingTypes = ThreadLocal.withInitial(ArrayList::new);

    private Set<Class<?>> managedTypes = new LinkedHashSet<>();
    private String basePackage;
    private Reflections reflections;

    public ComponentContainer bind(Object dependency) {
        if (dependency == null) throw new IllegalArgumentException("Component binding cannot be null");
        this.components.put(dependency.getClass(), dependency);
        this.trackManagedBinding(dependency);
        return this;
    }

    public ComponentContainer bind(Class<?> contract, Object dependency) {
        if (contract == null || dependency == null) {
            throw new IllegalArgumentException("Component contract and binding cannot be null");
        }
        if (!contract.isInstance(dependency)) {
            throw new IllegalArgumentException(
                    dependency.getClass().getName() + " does not implement " + contract.getName()
            );
        }
        this.components.put(contract, dependency);
        this.trackManagedBinding(dependency);
        return this;
    }

    public <T> void registerSingleton(Class<T> contract, T dependency) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(dependency, "dependency");
        if (!contract.isInstance(dependency)) {
            throw new IllegalArgumentException(
                    dependency.getClass().getName() + " does not implement " + contract.getName()
            );
        }
        Object existing = this.components.get(contract);
        if (existing != null && existing != dependency) {
            throw new IllegalStateException("Singleton already registered for " + contract.getName());
        }
        this.components.put(contract, dependency);
        this.trackManagedBinding(dependency);
    }

    public void scan(String packageName) {
        this.scan(packageName, type -> true);
    }

    public void scan(String packageName, Predicate<Class<?>> typeFilter) {
        this.validate(packageName, typeFilter);
        try {
            this.instantiateSingletons();
            this.loadLifecycles();
        } catch (RuntimeException | Error exception) {
            this.clearState();
            throw exception;
        }
    }

    public void validate(String packageName) {
        this.validate(packageName, type -> true);
    }

    public void validate(String packageName, Predicate<Class<?>> typeFilter) {
        this.discover(packageName, typeFilter, true);
    }

    public void discover(String packageName) {
        this.discover(packageName, type -> true);
    }

    public void discover(String packageName, Predicate<Class<?>> typeFilter) {
        this.discover(packageName, typeFilter, false);
    }

    private void discover(
            String packageName,
            Predicate<Class<?>> typeFilter,
            boolean validateDependencies
    ) {
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(typeFilter, "typeFilter");
        if (this.reflections != null || !this.managedTypes.isEmpty() || !this.loadedLifecycles.isEmpty()) {
            throw new IllegalStateException("Component container has already been scanned");
        }
        this.resetScanState();
        try {
            this.basePackage = packageName;
            this.reflections = new Reflections(
                    new ConfigurationBuilder()
                            .forPackages(packageName)
                            .setScanners(Scanners.TypesAnnotated, Scanners.SubTypes, Scanners.MethodsAnnotated)
            );

            Set<Class<?>> injectableTypes = this.findAnnotatedTypes(InjectComponent.class, typeFilter);
            Set<Class<? extends Annotation>> roles = this.findComponentRoles(typeFilter);
            for (Class<?> injectableType : injectableTypes) {
                for (Annotation annotation : injectableType.getAnnotations()) {
                    Class<? extends Annotation> annotationType = annotation.annotationType();
                    if (annotationType.isAnnotationPresent(ComponentRole.class)) roles.add(annotationType);
                }
            }
            Set<Class<?>> discovered = new LinkedHashSet<>();
            discovered.addAll(injectableTypes);
            discovered.addAll(this.explicitlyBoundManagedTypes);
            for (Class<? extends Annotation> role : roles) {
                ComponentRole metadata = role.getAnnotation(ComponentRole.class);
                if (metadata == null) {
                    throw new IllegalStateException("Component role is missing @ComponentRole: " + role.getName());
                }
                discovered.addAll(this.findAnnotatedTypes(role, typeFilter));
            }
            discovered.removeIf(type -> type.isInterface() || Modifier.isAbstract(type.getModifiers()));
            this.managedTypes = discovered;
            this.validateComponentContracts(roles, typeFilter);
            this.validateDefaultImplementations(typeFilter);
            this.validateComponentScopes(typeFilter);
            this.validateLoadOrders();
            if (validateDependencies) this.validateDependencyGraph();
        } catch (RuntimeException | Error exception) {
            this.clearState();
            throw exception;
        }
    }

    @Override
    public <T> T resolve(Class<T> requestedType) {
        Objects.requireNonNull(requestedType, "requestedType");
        Object existing = this.find(requestedType);
        if (existing != null) return requestedType.cast(existing);
        Class<? extends T> implementation = this.resolveImplementation(requestedType);
        this.requireSingletonScope(implementation, "resolve");
        return this.instantiateSingleton(requestedType, implementation);
    }

    @Override
    public <T> T create(Class<T> requestedType, ComponentArguments<?> arguments) {
        Objects.requireNonNull(requestedType, "requestedType");
        Objects.requireNonNull(arguments, "arguments");
        Class<? extends T> implementation = this.resolveImplementation(requestedType);
        if (componentScope(implementation) == ComponentScope.SINGLETON) {
            if (arguments.isPresent()) {
                throw new IllegalArgumentException(
                        "Singleton component does not accept runtime arguments: " + implementation.getName()
                );
            }
            return this.resolve(requestedType);
        }
        return this.instantiateWithCycleGuard(requestedType, implementation, arguments);
    }

    @Override
    public <A extends Annotation, T> List<T> resolveAll(Class<A> mark, Class<T> contract) {
        Objects.requireNonNull(mark, "mark");
        Objects.requireNonNull(contract, "contract");
        List<T> result = new ArrayList<>();
        for (Class<?> type : this.ordered(this.managedTypes)) {
            if (!type.isAnnotationPresent(mark) || !contract.isAssignableFrom(type)) continue;
            this.requireSingletonScope(type, "resolveAll");
            result.add(contract.cast(this.resolve(type)));
        }
        return List.copyOf(result);
    }

    @Override
    public List<Class<?>> managedTypes() {
        return this.ordered(this.managedTypes);
    }

    @Override
    public List<Class<?>> methodAnnotationOwners(Class<? extends Annotation> annotation) {
        Objects.requireNonNull(annotation, "annotation");
        Set<Class<?>> owners = new LinkedHashSet<>();
        for (Class<?> type : this.managedTypes) {
            for (var method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(annotation)) owners.add(type);
            }
        }
        return this.ordered(owners);
    }

    public <T> List<T> componentsAnnotatedWith(
            Class<? extends Annotation> annotation,
            Class<T> contract
    ) {
        Objects.requireNonNull(annotation, "annotation");
        Objects.requireNonNull(contract, "contract");
        return this.components.values().stream()
                .filter(component -> component.getClass().isAnnotationPresent(annotation))
                .filter(contract::isInstance)
                .map(contract::cast)
                .distinct()
                .sorted(Comparator.comparing(value -> value.getClass().getName()))
                .toList();
    }

    public void validateDependencyGraph() {
        Map<Class<?>, List<Class<?>>> dependencies = new LinkedHashMap<>();
        for (Class<?> type : this.ordered(this.managedTypes)) {
            dependencies.put(type, this.constructorDependencies(type));
        }
        Map<Class<?>, Boolean> completed = new LinkedHashMap<>();
        List<Class<?>> path = new ArrayList<>();
        for (Class<?> type : this.ordered(this.managedTypes)) {
            this.validateDependencyPath(type, dependencies, completed, path);
        }
    }

    public void reload() {
        for (ManagedLifecycle lifecycle : this.loadedLifecycles) lifecycle.reload();
    }

    public void unload() {
        Throwable failure = null;
        try {
            for (int index = this.loadedLifecycles.size() - 1; index >= 0; index--) {
                try {
                    this.loadedLifecycles.get(index).unload();
                } catch (RuntimeException | Error exception) {
                    if (failure == null) failure = exception;
                    else failure.addSuppressed(exception);
                }
            }
        } finally {
            this.clearState();
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }

    public void clear() {
        this.unload();
    }

    private void instantiateSingletons() {
        List<Class<?>> types = new ArrayList<>(this.managedTypes);
        types.sort(Comparator.comparingInt((Class<?> type) -> this.phase(type)).thenComparing(Class::getName));
        int currentPhase = Integer.MIN_VALUE;
        for (Class<?> type : types) {
            int componentPhase = this.phase(type);
            if (currentPhase != Integer.MIN_VALUE && componentPhase > currentPhase) {
                this.loadLifecycles(componentPhase);
            }
            if (componentScope(type) == ComponentScope.SINGLETON) this.resolve(type);
            currentPhase = componentPhase;
        }
    }

    private <T> T instantiateSingleton(Class<T> requestedType, Class<? extends T> implementation) {
        Object existing = this.components.get(implementation);
        if (existing != null) return requestedType.cast(existing);
        T created = this.instantiateWithCycleGuard(requestedType, implementation, ComponentArguments.empty());
        this.components.put(implementation, created);
        if (requestedType != implementation) this.components.put(requestedType, created);
        return created;
    }

    private <T> T instantiateWithCycleGuard(
            Class<T> requestedType,
            Class<? extends T> implementation,
            ComponentArguments<?> arguments
    ) {
        List<Class<?>> resolving = this.resolvingTypes.get();
        int cycleStart = resolving.indexOf(implementation);
        if (cycleStart >= 0) {
            throw new IllegalStateException(this.formatCycle(resolving, cycleStart, implementation));
        }
        resolving.add(implementation);
        try {
            return this.instantiate(requestedType, implementation, arguments);
        } finally {
            resolving.remove(resolving.size() - 1);
            if (resolving.isEmpty()) this.resolvingTypes.remove();
        }
    }

    private <T> T instantiate(
            Class<T> requestedType,
            Class<? extends T> implementation,
            ComponentArguments<?> arguments
    ) {
        Constructor<? extends T> constructor = this.resolveConstructor(implementation);
        Parameter[] parameters = constructor.getParameters();
        Object[] values = new Object[parameters.length];
        int runtimeArgumentCount = 0;
        for (int index = 0; index < parameters.length; index++) {
            Parameter parameter = parameters[index];
            if (parameter.isAnnotationPresent(RuntimeArgument.class)) runtimeArgumentCount++;
            values[index] = this.resolveConstructorArgument(implementation, parameter, arguments);
        }
        if (runtimeArgumentCount > 1) {
            throw new IllegalStateException(
                    "Prototype component must declare at most one @RuntimeArgument: " + implementation.getName()
            );
        }
        if (runtimeArgumentCount == 0 && arguments.isPresent()) {
            throw new IllegalArgumentException(
                    "Component does not declare a runtime input: " + implementation.getName()
            );
        }
        try {
            constructor.setAccessible(true);
            return requestedType.cast(constructor.newInstance(values));
        } catch (InstantiationException | IllegalAccessException exception) {
            throw new IllegalStateException("Failed to create component: " + implementation.getName(), exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("Component constructor failed: " + implementation.getName(), cause);
        }
    }

    private Object resolveConstructorArgument(
            Class<?> owner,
            Parameter parameter,
            ComponentArguments<?> arguments
    ) {
        if (parameter.isAnnotationPresent(RuntimeArgument.class)) {
            if (componentScope(owner) != ComponentScope.PROTOTYPE) {
                throw new IllegalStateException(
                        "Singleton component cannot declare @RuntimeArgument: " + owner.getName()
                );
            }
            if (!arguments.isPresent()) {
                throw new IllegalArgumentException("Missing runtime argument for component: " + owner.getName());
            }
            Object value = arguments.value();
            if (!wrapPrimitive(parameter.getType()).isInstance(value)) {
                throw new IllegalArgumentException(
                        "Runtime argument for " + owner.getName() + " must be " + parameter.getType().getName()
                );
            }
            return value;
        }
        if (parameter.getType() == ComponentSet.class) {
            Class<?> elementType = this.componentSetElementType(parameter.getParameterizedType(), owner);
            return ComponentSet.of(this.resolveAll(InjectComponent.class, elementType));
        }
        return this.resolve(parameter.getType());
    }

    private List<Class<?>> constructorDependencies(Class<?> owner) {
        Constructor<?> constructor = this.resolveConstructor(owner);
        Parameter[] parameters = constructor.getParameters();
        Type[] genericTypes = constructor.getGenericParameterTypes();
        Set<Class<?>> dependencies = new LinkedHashSet<>();
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index].isAnnotationPresent(RuntimeArgument.class)) continue;
            Class<?> parameterType = parameters[index].getType();
            if (parameterType == ComponentSet.class) {
                Class<?> elementType = this.componentSetElementType(genericTypes[index], owner);
                for (Class<?> type : this.ordered(this.managedTypes)) {
                    if (elementType.isAssignableFrom(type)) {
                        this.requireSingletonScope(type, "ComponentSet");
                        dependencies.add(type);
                    }
                }
                continue;
            }
            Object bound = this.find(parameterType);
            if (bound != null) continue;
            Class<?> implementation = this.resolveImplementation(parameterType);
            this.requireSingletonScope(implementation, "constructor injection");
            dependencies.add(implementation);
        }
        return List.copyOf(dependencies);
    }

    private void validateDependencyPath(
            Class<?> type,
            Map<Class<?>, List<Class<?>>> dependencies,
            Map<Class<?>, Boolean> completed,
            List<Class<?>> path
    ) {
        if (Boolean.TRUE.equals(completed.get(type))) return;
        int cycleStart = path.indexOf(type);
        if (cycleStart >= 0) throw new IllegalStateException(this.formatCycle(path, cycleStart, type));
        path.add(type);
        for (Class<?> dependency : dependencies.getOrDefault(type, List.of())) {
            this.validateDependencyPath(dependency, dependencies, completed, path);
        }
        path.remove(path.size() - 1);
        completed.put(type, true);
    }

    private void loadLifecycles() {
        this.loadLifecycles(Integer.MAX_VALUE);
    }

    private void loadLifecycles(int beforePhase) {
        List<ManagedLifecycle> lifecycles = this.components.values().stream()
                .filter(ManagedLifecycle.class::isInstance)
                .map(ManagedLifecycle.class::cast)
                .distinct()
                .filter(lifecycle -> !this.loadedLifecycles.contains(lifecycle))
                .filter(lifecycle -> this.phase(lifecycle) < beforePhase)
                .sorted(Comparator
                        .comparingInt((ManagedLifecycle lifecycle) -> this.phase(lifecycle))
                        .thenComparing(value -> value.getClass().getName()))
                .toList();
        try {
            for (ManagedLifecycle lifecycle : lifecycles) {
                lifecycle.load();
                this.loadedLifecycles.add(lifecycle);
            }
        } catch (RuntimeException | Error exception) {
            this.rollbackLifecycles(exception);
            this.clearState();
            throw exception;
        }
    }

    private void rollbackLifecycles(Throwable failure) {
        for (int index = this.loadedLifecycles.size() - 1; index >= 0; index--) {
            try {
                this.loadedLifecycles.get(index).unload();
            } catch (RuntimeException | Error exception) {
                failure.addSuppressed(exception);
            }
        }
        this.loadedLifecycles.clear();
    }

    private int phase(ManagedLifecycle lifecycle) {
        return this.phase(lifecycle.getClass());
    }

    private int phase(Class<?> type) {
        LoadOrder order = type.getAnnotation(LoadOrder.class);
        return order == null ? LoadPhase.SERVICES : order.phase();
    }

    private <T> Class<? extends T> resolveImplementation(Class<T> requestedType) {
        if (!requestedType.isInterface() && !Modifier.isAbstract(requestedType.getModifiers())) {
            if (!this.managedTypes.contains(requestedType)) {
                throw new IllegalStateException("Missing component: " + requestedType.getName());
            }
            return requestedType;
        }
        List<Class<? extends T>> candidates = new ArrayList<>();
        for (Class<?> type : this.ordered(this.managedTypes)) {
            if (requestedType.isAssignableFrom(type)) candidates.add(type.asSubclass(requestedType));
        }
        if (candidates.isEmpty()) throw new IllegalStateException("Missing component: " + requestedType.getName());
        Class<? extends T> defaultImplementation = null;
        for (Class<? extends T> candidate : candidates) {
            if (!candidate.isAnnotationPresent(DefaultImplementation.class)) continue;
            if (defaultImplementation != null) {
                throw new IllegalStateException("Multiple components implement " + requestedType.getName());
            }
            defaultImplementation = candidate;
        }
        if (defaultImplementation != null) return defaultImplementation;
        if (candidates.size() == 1) return candidates.get(0);
        throw new IllegalStateException(
                "Multiple components implement " + requestedType.getName() + ": " + candidates
        );
    }

    private Object find(Class<?> type) {
        Object direct = this.components.get(type);
        if (direct != null) return direct;
        Object match = null;
        for (Object component : this.components.values()) {
            if (!type.isInstance(component)) continue;
            if (match == null || match == component) {
                match = component;
                continue;
            }
            if (match.getClass().isAnnotationPresent(DefaultImplementation.class)) continue;
            if (component.getClass().isAnnotationPresent(DefaultImplementation.class)) {
                match = component;
                continue;
            }
            throw new IllegalStateException("Multiple components implement " + type.getName());
        }
        return match;
    }

    private void validateComponentContracts(
            Set<Class<? extends Annotation>> roles,
            Predicate<Class<?>> typeFilter
    ) {
        for (Class<? extends Annotation> role : roles) {
            ComponentRole metadata = role.getAnnotation(ComponentRole.class);
            Class<?> contract = metadata.contract();
            for (Class<?> type : this.findAnnotatedTypes(role, typeFilter)) {
                if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                    throw new IllegalStateException(
                            "@" + role.getSimpleName() + " can only be used on concrete classes: " + type.getName()
                    );
                }
                if (!contract.isAssignableFrom(type)) {
                    throw new IllegalStateException(
                            "@" + role.getSimpleName() + " requires " + contract.getName() + ": " + type.getName()
                    );
                }
                if (!type.isAnnotationPresent(InjectComponent.class)) {
                    throw new IllegalStateException(
                            "@" + role.getSimpleName() + " requires @InjectComponent: " + type.getName()
                    );
                }
            }
            for (Class<?> type : this.findAnnotatedTypes(InjectComponent.class, typeFilter)) {
                if (!contract.isAssignableFrom(type) || type.isInterface() || Modifier.isAbstract(type.getModifiers())) continue;
                if (!type.isAnnotationPresent(role)) {
                    throw new IllegalStateException(
                            "Injectable " + contract.getName() + " component requires @"
                                    + role.getSimpleName() + ": " + type.getName()
                    );
                }
            }
        }
    }

    private void validateDefaultImplementations(Predicate<Class<?>> typeFilter) {
        for (Class<?> type : this.findAnnotatedTypes(DefaultImplementation.class, typeFilter)) {
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                throw new IllegalStateException(
                        "@DefaultImplementation can only be used on concrete classes: " + type.getName()
                );
            }
            if (!type.isAnnotationPresent(InjectComponent.class)) {
                throw new IllegalStateException(
                        "@DefaultImplementation requires @InjectComponent: " + type.getName()
                );
            }
        }
    }

    private void validateComponentScopes(Predicate<Class<?>> typeFilter) {
        for (Class<?> type : this.findAnnotatedTypes(InjectComponent.class, typeFilter)) {
            InjectComponent annotation = type.getAnnotation(InjectComponent.class);
            if (annotation.scope() != ComponentScope.PROTOTYPE) continue;
            if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers())) {
                throw new IllegalStateException(
                        "Prototype component cannot be a non-static member class: " + type.getName()
                );
            }
            Constructor<?> constructor = this.resolveConstructor(type);
            int runtimeArgumentCount = 0;
            for (Parameter parameter : constructor.getParameters()) {
                if (!parameter.isAnnotationPresent(RuntimeArgument.class)) continue;
                runtimeArgumentCount++;
                if (runtimeArgumentCount > 1) {
                    throw new IllegalStateException(
                            "Prototype component must declare at most one @RuntimeArgument: " + type.getName()
                    );
                }
                if (!parameter.getType().isRecord()) {
                    throw new IllegalStateException(
                            "@RuntimeArgument parameter must be a record input: " + type.getName()
                    );
                }
            }
        }
    }

    private void validateLoadOrders() {
        for (Class<?> type : this.managedTypes) {
            boolean ordered = type.isAnnotationPresent(LoadOrder.class);
            if (ordered && !ManagedLifecycle.class.isAssignableFrom(type)) {
                throw new IllegalStateException(
                        "@LoadOrder can only be used on ManagedLifecycle classes: " + type.getName()
                );
            }
        }
    }

    private Set<Class<? extends Annotation>> findComponentRoles(Predicate<Class<?>> filter) {
        Set<Class<? extends Annotation>> roles = new LinkedHashSet<>();
        for (Class<?> type : this.reflections.getTypesAnnotatedWith(ComponentRole.class)) {
            if (!type.isAnnotation() || !this.isInSource(type) || !filter.test(type)) continue;
            roles.add(type.asSubclass(Annotation.class));
        }
        return roles;
    }

    private <A extends Annotation> Set<Class<?>> findAnnotatedTypes(
            Class<A> annotation,
            Predicate<Class<?>> filter
    ) {
        Set<Class<?>> result = new LinkedHashSet<>();
        if (this.reflections == null) return result;
        for (Class<?> type : this.reflections.getTypesAnnotatedWith(annotation)) {
            if (this.isInSource(type) && filter.test(type)) result.add(type);
        }
        return result;
    }

    private boolean isInSource(Class<?> type) {
        return this.basePackage != null && type.getName().startsWith(this.basePackage + ".");
    }

    private <T> Constructor<T> resolveConstructor(Class<T> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        List<Constructor<T>> injectable = new ArrayList<>();
        for (Constructor<?> rawConstructor : constructors) {
            @SuppressWarnings("unchecked")
            Constructor<T> constructor = (Constructor<T>) rawConstructor;
            if (!Modifier.isPrivate(constructor.getModifiers())) injectable.add(constructor);
        }
        if (injectable.size() != 1) {
            throw new IllegalStateException(
                    "Injectable component must declare exactly one non-private constructor: " + type.getName()
            );
        }
        return injectable.get(0);
    }

    private void requireSingletonScope(Class<?> type, String usage) {
        if (componentScope(type) == ComponentScope.PROTOTYPE) {
            throw new IllegalStateException(
                    "Prototype component requires explicit creation and cannot be used by " + usage + ": "
                            + type.getName()
            );
        }
    }

    private static ComponentScope componentScope(Class<?> type) {
        InjectComponent annotation = type.getAnnotation(InjectComponent.class);
        if (annotation == null) {
            throw new IllegalStateException("Component is missing @InjectComponent: " + type.getName());
        }
        return annotation.scope();
    }

    private static Class<?> componentSetElementType(Type genericType, Class<?> owner) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            throw new IllegalStateException("ComponentSet must declare an element type in " + owner.getName());
        }
        Type elementType = parameterizedType.getActualTypeArguments()[0];
        if (!(elementType instanceof Class<?> type)) {
            throw new IllegalStateException("Unsupported ComponentSet element type in " + owner.getName());
        }
        return type;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }

    private static List<Class<?>> ordered(Iterable<Class<?>> types) {
        List<Class<?>> result = new ArrayList<>();
        types.forEach(result::add);
        result.sort(Comparator.comparing(Class::getName));
        return List.copyOf(result);
    }

    private String formatCycle(List<Class<?>> path, int cycleStart, Class<?> repeatedType) {
        List<String> names = new ArrayList<>();
        for (int index = cycleStart; index < path.size(); index++) names.add(path.get(index).getName());
        names.add(repeatedType.getName());
        return "Circular component dependency detected: " + String.join(" -> ", names);
    }

    private void resetScanState() {
        this.loadedLifecycles.clear();
        this.managedTypes.clear();
        this.reflections = null;
        this.basePackage = null;
    }

    private void clearState() {
        this.loadedLifecycles.clear();
        this.components.clear();
        this.managedTypes.clear();
        this.explicitlyBoundManagedTypes.clear();
        this.reflections = null;
        this.basePackage = null;
    }

    private void trackManagedBinding(Object dependency) {
        Class<?> type = dependency.getClass();
        if (type.isAnnotationPresent(InjectComponent.class)) this.explicitlyBoundManagedTypes.add(type);
    }
}
