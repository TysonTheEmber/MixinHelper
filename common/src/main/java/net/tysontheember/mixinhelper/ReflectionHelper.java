package net.tysontheember.mixinhelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Utility for safely accessing Mixin internals via reflection.
 * All methods log errors and return null on failure rather than throwing.
 */
public final class ReflectionHelper {

    private ReflectionHelper() {}

    public static Object invokeMethod(Object instance, Class<?> clazz, String methodName) {
        try {
            Method method = clazz.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(instance);
        } catch (Exception e) {
            Log.error("Failed to invoke " + clazz.getSimpleName() + "." + methodName + "(): " + e.getMessage());
            return null;
        }
    }

    public static Object getFieldValue(Object instance, Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(instance);
        } catch (Exception e) {
            Log.error("Failed to get field " + clazz.getSimpleName() + "." + fieldName + ": " + e.getMessage());
            return null;
        }
    }

    public static Object getStaticFieldValue(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (Exception e) {
            Log.error("Failed to get static field " + clazz.getSimpleName() + "." + fieldName + ": " + e.getMessage());
            return null;
        }
    }

    public static boolean setFieldValue(Object instance, Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
            return true;
        } catch (Exception e) {
            Log.error("Failed to set field " + clazz.getSimpleName() + "." + fieldName + ": " + e.getMessage());
            return false;
        }
    }

    public static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            Log.error("Class not found: " + className);
            return null;
        }
    }

    /**
     * Gets ALL mixin Config handles from the internal Config.allConfigs map.
     * Unlike Mixins.getConfigs() which returns only unvisited/pending configs
     * (and is empty after processing), this accesses the permanent registry.
     */
    @SuppressWarnings("unchecked")
    public static Collection<Object> getAllMixinConfigs() {
        try {
            Class<?> configClass = Class.forName("org.spongepowered.asm.mixin.transformer.Config");
            Map<String, Object> allConfigs = (Map<String, Object>) getStaticFieldValue(configClass, "allConfigs");
            if (allConfigs != null && !allConfigs.isEmpty()) {
                Log.debug("Found " + allConfigs.size() + " config(s) in Config.allConfigs");
                return allConfigs.values();
            }
            Log.warn("Config.allConfigs is null or empty");
        } catch (Exception e) {
            Log.error("Failed to access Config.allConfigs: " + e.getMessage());
        }
        return Collections.emptyList();
    }
}
