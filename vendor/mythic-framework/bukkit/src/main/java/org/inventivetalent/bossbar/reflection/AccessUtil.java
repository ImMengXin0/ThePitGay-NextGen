package org.inventivetalent.bossbar.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public abstract class AccessUtil {
    public static Field setAccessible(Field field)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        field.setAccessible(true);
        return field;
    }

    public static Method setAccessible(Method method)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        method.setAccessible(true);
        return method;
    }
}
