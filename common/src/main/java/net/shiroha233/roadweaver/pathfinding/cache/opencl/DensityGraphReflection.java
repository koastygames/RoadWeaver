package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class DensityGraphReflection {
    private DensityGraphReflection() {}

    static Object read(Object owner, String name) {
        if (owner == null || name == null || name.isBlank()) {
            return null;
        }
        Object accessorValue = readAccessor(owner, name);
        if (accessorValue != null) {
            return accessorValue;
        }
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object readAccessor(Object owner, String name) {
        try {
            Method method = owner.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static double readDouble(Object owner, String name, double fallback) {
        Object value = read(owner, name);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    static int readInt(Object owner, String name, int fallback) {
        Object value = read(owner, name);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}