package org.inventivetalent.reflection.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public abstract class AccessUtil {
    public static Field setAccessible(Field field) throws ReflectiveOperationException {
        field.setAccessible(true);
        return field;
    }

    public static Method setAccessible(Method method) throws ReflectiveOperationException {
        method.setAccessible(true);
        return method;
    }

    public static Constructor setAccessible(Constructor constructor) throws ReflectiveOperationException {
        constructor.setAccessible(true);
        return constructor;
    }
}
