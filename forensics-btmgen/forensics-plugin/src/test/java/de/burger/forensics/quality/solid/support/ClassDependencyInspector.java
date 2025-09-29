package de.burger.forensics.quality.solid.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Extracts dependency information from compiled classes.
 */
public final class ClassDependencyInspector {

    private ClassDependencyInspector() {
    }

    public static Set<Class<?>> collectDependencies(Class<?> type) {
        Set<Class<?>> dependencies = new LinkedHashSet<>();
        addType(type.getSuperclass(), dependencies);
        for (Class<?> iface : type.getInterfaces()) {
            addType(iface, dependencies);
        }
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            addType(field.getType(), dependencies);
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                addType(parameter, dependencies);
            }
            for (Class<?> exception : constructor.getExceptionTypes()) {
                addType(exception, dependencies);
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            addType(method.getReturnType(), dependencies);
            for (Class<?> parameter : method.getParameterTypes()) {
                addType(parameter, dependencies);
            }
            for (Class<?> exception : method.getExceptionTypes()) {
                addType(exception, dependencies);
            }
        }
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                addType(component.getType(), dependencies);
            }
        }
        dependencies.remove(type);
        return dependencies;
    }

    private static void addType(Class<?> type, Set<Class<?>> dependencies) {
        if (type == null || type.isPrimitive()) {
            return;
        }
        while (type.isArray()) {
            type = type.getComponentType();
            if (type == null) {
                return;
            }
        }
        String name = type.getName();
        if (name.startsWith("java.") || name.startsWith("javax.")) {
            return;
        }
        dependencies.add(type);
    }
}
