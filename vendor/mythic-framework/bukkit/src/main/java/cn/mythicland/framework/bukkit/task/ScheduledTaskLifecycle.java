package cn.mythicland.framework.bukkit.task;

import cn.mythicland.framework.component.ComponentResolver;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.LoadOrder;
import cn.mythicland.framework.component.ManagedLifecycle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@InjectComponent
@LoadOrder(phase = cn.mythicland.framework.component.LoadPhase.ADAPTERS)
public final class ScheduledTaskLifecycle implements ManagedLifecycle {
    private final ComponentResolver componentResolver;
    private final TaskScheduler taskScheduler;
    private List<ScheduledMethod> scheduledMethods;
    private final List<ScheduledTaskHandle> taskHandles;

    public ScheduledTaskLifecycle(
            ComponentResolver componentResolver,
            TaskScheduler taskScheduler
    ) {
        this.componentResolver = Objects.requireNonNull(componentResolver, "componentResolver");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        this.taskHandles = new ArrayList<>();
    }

    @Override
    public void load() {
        this.scheduledMethods = this.discoverScheduledMethods();
    }

    public void start() {
        if (!this.taskHandles.isEmpty()) return;

        if (this.scheduledMethods == null) {
            throw new IllegalStateException("Scheduled task lifecycle is not loaded");
        }
        List<ScheduledTaskHandle> handles = new ArrayList<>(this.scheduledMethods.size());
        try {
            for (ScheduledMethod scheduledMethod : this.scheduledMethods) {
                ScheduledTask annotation = scheduledMethod.method().getAnnotation(ScheduledTask.class);
                ScheduledTaskHandle handle = this.taskScheduler.scheduleSyncRepeating(
                        () -> this.invoke(scheduledMethod),
                        annotation.delayTicks(),
                        annotation.periodTicks()
                );
                if (handle == null) {
                    throw new IllegalStateException(
                            "Task scheduler returned null handle for " + this.failureMessage(scheduledMethod)
                    );
                }
                handles.add(handle);
            }
        } catch (RuntimeException | Error exception) {
            this.cancel(handles, exception);
            throw exception;
        }
        this.taskHandles.addAll(handles);
    }

    @Override
    public void unload() {
        Throwable failure = null;
        for (int index = this.taskHandles.size() - 1; index >= 0; index--) {
            try {
                this.taskHandles.get(index).cancel();
            } catch (RuntimeException | Error exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        this.taskHandles.clear();
        this.scheduledMethods = null;
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }

    private List<ScheduledMethod> discoverScheduledMethods() {
        List<ScheduledMethod> scheduledMethods = new ArrayList<>();
        for (Class<?> componentType : this.componentResolver.methodAnnotationOwners(ScheduledTask.class)) {
            if (!this.componentResolver.managedTypes().contains(componentType)) {
                throw new IllegalStateException(
                        "@ScheduledTask owner must be a managed component: " + componentType.getName()
                );
            }
            for (Method method : this.declaredMethods(componentType)) {
                ScheduledTask annotation = method.getAnnotation(ScheduledTask.class);
                if (annotation == null) continue;
                this.validate(componentType, method, annotation);
                Object component = this.componentResolver.resolve(componentType);
                scheduledMethods.add(new ScheduledMethod(componentType, component, method));
            }
        }
        scheduledMethods.sort(Comparator
                .comparing((ScheduledMethod value) -> value.componentType().getName())
                .thenComparing(value -> value.method().getName())
                .thenComparing(value -> value.method().toGenericString()));
        return scheduledMethods;
    }

    private List<Method> declaredMethods(Class<?> componentType) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> type = componentType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(ScheduledTask.class)) methods.add(method);
            }
        }
        return methods;
    }

    private void validate(Class<?> componentType, Method method, ScheduledTask annotation) {
        if (!componentType.isAnnotationPresent(InjectComponent.class)) {
            throw new IllegalStateException(
                    "@ScheduledTask owner must be @InjectComponent: " + componentType.getName()
            );
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException("@ScheduledTask method must not be static: " + method);
        }
        if (method.getParameterCount() != 0 || method.getReturnType() != void.class) {
            throw new IllegalStateException("@ScheduledTask method must have signature void(): " + method);
        }
        if (annotation.delayTicks() < 0L || annotation.periodTicks() <= 0L) {
            throw new IllegalStateException(
                    "@ScheduledTask ticks must be delay >= 0 and period > 0: " + method
            );
        }
        if (!method.trySetAccessible()) {
            throw new IllegalStateException("Cannot access @ScheduledTask method: " + method);
        }
    }

    private void invoke(ScheduledMethod scheduledMethod) {
        try {
            scheduledMethod.method().invoke(scheduledMethod.component());
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(this.failureMessage(scheduledMethod), exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException(this.failureMessage(scheduledMethod), cause);
        }
    }

    private void cancel(List<ScheduledTaskHandle> handles, Throwable failure) {
        for (int index = handles.size() - 1; index >= 0; index--) {
            try {
                handles.get(index).cancel();
            } catch (RuntimeException | Error exception) {
                failure.addSuppressed(exception);
            }
        }
    }

    private String failureMessage(ScheduledMethod scheduledMethod) {
        return "Scheduled task failed: "
                + scheduledMethod.componentType().getName()
                + "#"
                + scheduledMethod.method().getName();
    }

    private record ScheduledMethod(Class<?> componentType, Object component, Method method) {
    }
}
