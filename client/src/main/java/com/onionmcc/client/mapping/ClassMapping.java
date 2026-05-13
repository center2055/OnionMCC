package com.onionmcc.client.mapping;

import com.onionmcc.client.OnionMCC;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents class/field/method mappings for a single Minecraft class.
 */
public class ClassMapping {

    private final String obfuscatedName;
    private final String deobfuscatedName;
    private final Map<String, String> fieldMappings = new HashMap<>(); // deobf -> obf
    private final Map<String, String> methodMappings = new HashMap<>(); // deobf -> obf

    // Cached reflection objects
    private Class<?> resolvedClass;
    private final Map<String, Field> resolvedFields = new HashMap<>();
    private final Map<String, Method> resolvedMethods = new HashMap<>();

    public ClassMapping(String obfuscatedName, String deobfuscatedName) {
        this.obfuscatedName = obfuscatedName;
        this.deobfuscatedName = deobfuscatedName;
    }

    public void addFieldMapping(String deobfName, String obfName) {
        fieldMappings.put(deobfName, obfName);
    }

    public void addMethodMapping(String deobfName, String obfName) {
        methodMappings.put(deobfName, obfName);
    }

    public String getObfuscatedName() {
        return obfuscatedName;
    }

    public String getDeobfuscatedName() {
        return deobfuscatedName;
    }

    /**
     * Resolve the actual Class object from the current classloader.
     */
    public Class<?> resolveClass() throws ClassNotFoundException {
        if (resolvedClass == null) {
            // First, try finding it in loaded classes (this handles LaunchClassLoader correctly)
            resolvedClass = findLoadedClass(obfuscatedName);
            if (resolvedClass == null) {
                resolvedClass = findLoadedClass(deobfuscatedName);
            }
            
            if (resolvedClass == null) {
                try {
                    resolvedClass = Class.forName(obfuscatedName);
                } catch (Throwable e) {
                    try {
                        // Try deobfuscated name (for development/deobf environments)
                        resolvedClass = Class.forName(deobfuscatedName);
                    } catch (Throwable ignored) {
                        throw new ClassNotFoundException(obfuscatedName + " (and " + deobfuscatedName + ") not found or rejected.");
                    }
                }
            }
        }
        return resolvedClass;
    }

    private Class<?> findLoadedClass(String className) {
        try {
            Instrumentation instrumentation = OnionMCC.getInstance().getInstrumentation();
            if (instrumentation == null) {
                return null;
            }

            for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
                if (loadedClass.getName().equals(className)) {
                    return loadedClass;
                }
            }
        } catch (Throwable ignored) {
            // Best-effort fallback only.
        }
        return null;
    }

    /**
     * Get a field by its deobfuscated name.
     */
    public Field getField(String deobfName) throws Exception {
        if (!resolvedFields.containsKey(deobfName)) {
            Class<?> clazz = resolveClass();
            String actualNameStr = fieldMappings.getOrDefault(deobfName, deobfName);
            Field field = null;

            String[] namesToTry = actualNameStr.split(",");
            for (String name : namesToTry) {
                try {
                    field = findFieldInHierarchy(clazz, name.trim());
                    break;
                } catch (NoSuchFieldException ignored) {}
            }

            if (field == null) {
                if (!actualNameStr.contains(deobfName)) {
                    try {
                        field = findFieldInHierarchy(clazz, deobfName);
                    } catch (NoSuchFieldException e) {
                        throw new NoSuchFieldException(deobfName + " (tried: " + actualNameStr + ") in " + clazz.getName());
                    }
                } else {
                    throw new NoSuchFieldException(deobfName + " in " + clazz.getName());
                }
            }
            field.setAccessible(true);
            resolvedFields.put(deobfName, field);
        }
        return resolvedFields.get(deobfName);
    }

    /**
     * Get a method by its deobfuscated name and parameter types.
     */
    public Method getMethod(String deobfName, Class<?>... paramTypes) throws Exception {
        String key = deobfName + ":" + paramTypes.length;
        if (!resolvedMethods.containsKey(key)) {
            Class<?> clazz = resolveClass();
            String actualNameStr = methodMappings.getOrDefault(deobfName, deobfName);
            Method method = null;

            String[] namesToTry = actualNameStr.split(",");
            for (String name : namesToTry) {
                try {
                    method = findMethodInHierarchy(clazz, name.trim(), paramTypes);
                    break;
                } catch (NoSuchMethodException ignored) {}
            }

            if (method == null) {
                if (!actualNameStr.contains(deobfName)) {
                    try {
                        method = findMethodInHierarchy(clazz, deobfName, paramTypes);
                    } catch (NoSuchMethodException e) {
                        throw new NoSuchMethodException(deobfName + " (tried: " + actualNameStr + ") in " + clazz.getName());
                    }
                } else {
                    throw new NoSuchMethodException(deobfName + " in " + clazz.getName());
                }
            }
            method.setAccessible(true);
            resolvedMethods.put(key, method);
        }
        return resolvedMethods.get(key);
    }

    private Field findFieldInHierarchy(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " in " + clazz.getName());
    }

    private Method findMethodInHierarchy(Class<?> clazz, String name, Class<?>... paramTypes)
            throws NoSuchMethodException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                for (Method m : current.getDeclaredMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == paramTypes.length) {
                        m.setAccessible(true);
                        return m;
                    }
                }
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name + " in " + clazz.getName());
    }
}

