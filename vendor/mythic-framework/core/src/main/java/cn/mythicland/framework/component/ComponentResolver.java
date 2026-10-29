package cn.mythicland.framework.component;

import java.lang.annotation.Annotation;
import java.util.List;

public interface ComponentResolver {
    <T> T resolve(Class<T> type);

    <T> T create(Class<T> type, ComponentArguments<?> arguments);

    <A extends Annotation, T> List<T> resolveAll(Class<A> mark, Class<T> contract);

    List<Class<?>> managedTypes();

    List<Class<?>> methodAnnotationOwners(Class<? extends Annotation> annotation);
}
