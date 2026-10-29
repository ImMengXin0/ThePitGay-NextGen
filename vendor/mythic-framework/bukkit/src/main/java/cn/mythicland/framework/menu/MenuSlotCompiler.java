package cn.mythicland.framework.menu;

import org.bukkit.entity.Player;

import java.lang.reflect.*;
import java.util.*;

public final class MenuSlotCompiler {
    private static final Map<Class<?>, CompiledLayout> CACHE = new HashMap<>();

    private MenuSlotCompiler() {
    }

    public static void validate(Class<? extends AbstractMenu> menuType) {
        compiledLayout(menuType);
    }

    public static void populate(AbstractMenu menu, Player player, Menu contents) {
        for (CompiledSlot slot : compiledLayout(menu.getClass()).slots()) {
            AbstractButton button = slot.invoke(menu, player);
            if (button != null) contents.setButton(slot.slot(), button);
        }
    }

    private static CompiledLayout compiledLayout(Class<?> menuType) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(menuType, MenuSlotCompiler::compile);
        }
    }

    private static CompiledLayout compile(Class<?> menuType) {
        Map<Integer, Method> methodsBySlot = new LinkedHashMap<>();
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = menuType;
        while (current != null && current != AbstractMenu.class) {
            hierarchy.add(current);
            current = current.getSuperclass();
        }
        Collections.reverse(hierarchy);
        for (Class<?> type : hierarchy) {
            List<Method> methods = new ArrayList<>();
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(MenuSlot.class)) methods.add(method);
            }
            methods.sort(Comparator.comparing(Method::getName).thenComparing(Method::toGenericString));
            for (Method method : methods) {
                MenuSlot annotation = method.getAnnotation(MenuSlot.class);
                validateMethod(menuType, method, annotation, methodsBySlot);
                methodsBySlot.put(annotation.slot(), method);
            }
        }
        List<CompiledSlot> slots = methodsBySlot.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new CompiledSlot(entry.getKey(), entry.getValue()))
                .toList();
        boolean hasManualLayout = declaresManualLayout(menuType);
        if (!slots.isEmpty() && hasManualLayout) {
            throw new IllegalStateException(
                    "Menu cannot combine @MenuSlot layout with populateMenu: " + menuType.getName()
            );
        }
        if (slots.isEmpty() && !hasManualLayout) {
            throw new IllegalStateException(
                    "Menu must declare @MenuSlot methods or override populateMenu: " + menuType.getName()
            );
        }
        return new CompiledLayout(slots);
    }

    private static boolean declaresManualLayout(Class<?> menuType) {
        Class<?> current = menuType;
        while (current != null && current != AbstractMenu.class) {
            try {
                current.getDeclaredMethod("populateMenu", Player.class, Menu.class);
                return true;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return false;
    }

    private static void validateMethod(
            Class<?> menuType,
            Method method,
            MenuSlot annotation,
            Map<Integer, Method> methodsBySlot
    ) {
        int slot = annotation.slot();
        if (slot < 0 || slot > 53) {
            throw invalid(menuType, method, slot, "slot must be between 0 and 53");
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw invalid(menuType, method, slot, "method must be an instance method");
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 1 || (parameterTypes.length == 1 && parameterTypes[0] != Player.class)) {
            throw invalid(menuType, method, slot, "method parameters must be empty or exactly Player");
        }
        if (!AbstractButton.class.isAssignableFrom(method.getReturnType())
                && !isOptionalButtonReturn(method.getGenericReturnType(), method.getReturnType())) {
            throw invalid(menuType, method, slot,
                    "method must return AbstractButton or Optional<? extends AbstractButton>");
        }
        Method previous = methodsBySlot.get(slot);
        if (previous != null) {
            throw invalid(
                    menuType,
                    method,
                    slot,
                    "slot is already declared by " + previous.getDeclaringClass().getName()
                            + "#" + previous.getName()
            );
        }
    }

    private static boolean isOptionalButtonReturn(Type genericReturnType, Class<?> rawReturnType) {
        if (rawReturnType != Optional.class) return false;
        if (!(genericReturnType instanceof ParameterizedType parameterizedType)) return false;
        Type argument = parameterizedType.getActualTypeArguments()[0];
        if (argument instanceof Class<?> argumentType) {
            return AbstractButton.class.isAssignableFrom(argumentType);
        }
        if (argument instanceof WildcardType wildcardType) {
            for (Type upperBound : wildcardType.getUpperBounds()) {
                if (upperBound instanceof Class<?> upperType
                        && AbstractButton.class.isAssignableFrom(upperType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static IllegalStateException invalid(Class<?> menuType, Method method, int slot, String reason) {
        return new IllegalStateException(
                "Invalid @MenuSlot in " + menuType.getName() + "#" + method.getName()
                        + " at slot " + slot + ": " + reason
        );
    }

    private record CompiledLayout(List<CompiledSlot> slots) {
    }

    private record CompiledSlot(int slot, Method method) {
        private CompiledSlot {
            method.setAccessible(true);
        }

        private AbstractButton invoke(AbstractMenu menu, Player player) {
            try {
                Object result = this.method.getParameterCount() == 0
                        ? this.method.invoke(menu)
                        : this.method.invoke(menu, player);
                if (result instanceof Optional<?> optional) {
                    if (optional.isEmpty()) return null;
                    result = optional.orElseThrow();
                }
                if (result == null) {
                    throw new IllegalStateException("returned null");
                }
                return (AbstractButton) result;
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                throw new IllegalStateException(
                        "Failed to invoke @MenuSlot " + this.method.getDeclaringClass().getName()
                                + "#" + this.method.getName() + " at slot " + this.slot,
                        cause
                );
            } catch (IllegalStateException exception) {
                throw new IllegalStateException(
                        "Failed to invoke @MenuSlot " + this.method.getDeclaringClass().getName()
                                + "#" + this.method.getName() + " at slot " + this.slot,
                        exception
                );
            } catch (ReflectiveOperationException | ClassCastException exception) {
                throw new IllegalStateException(
                        "Failed to invoke @MenuSlot " + this.method.getDeclaringClass().getName()
                                + "#" + this.method.getName() + " at slot " + this.slot,
                        exception
                );
            }
        }
    }
}
