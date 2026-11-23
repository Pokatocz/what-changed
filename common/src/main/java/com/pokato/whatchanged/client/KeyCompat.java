package com.pokato.whatchanged.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * KeyMapping creation that works from 1.21.8 to 26.x on every loader.
 * - 1.21.8: category is a String; 1.21.9+: a KeyMapping.Category object (registering one twice throws -> cached).
 * - 1.21.11: ResourceLocation became Identifier, so the id type is taken from the factory's parameter.
 * - 26.3: key codes and the KEYSYM type changed, so the default key is resolved by name ("key.keyboard.j").
 * Everything is matched by signature, never by member name, because Fabric 1.21.x runs on intermediary names.
 */
public final class KeyCompat {
    private static final Map<String, Object> CATEGORIES = new HashMap<>();

    private KeyCompat() {}

    public static KeyMapping create(String translationKey, String defaultKeyName, String modId, String categoryPath) {
        InputConstants.Key key = InputConstants.getKey(defaultKeyName);
        InputConstants.Type type = key.getType();
        int code = key.getValue();
        String legacyCategory = "key.category." + modId + "." + categoryPath;
        try {
            for (Constructor<?> c : KeyMapping.class.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length != 4 || p[0] != String.class || p[1] != InputConstants.Type.class || p[2] != int.class) continue;
                if (p[3] == String.class) return (KeyMapping) c.newInstance(translationKey, type, code, legacyCategory);
                return (KeyMapping) c.newInstance(translationKey, type, code, category(p[3], modId, categoryPath));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create key mapping " + translationKey, e);
        }
        throw new IllegalStateException("No compatible KeyMapping constructor found");
    }

    private static synchronized Object category(Class<?> categoryType, String modId, String path) throws ReflectiveOperationException {
        String cacheKey = modId + ":" + path;
        Object cached = CATEGORIES.get(cacheKey);
        if (cached != null) return cached;
        Object created = null;
        // static Category factory(<id type>) - prefer it, it also registers the category for the Controls screen
        Method factory = null;
        for (Method m : categoryType.getDeclaredMethods()) {
            if (!m.isSynthetic() && Modifier.isStatic(m.getModifiers()) && m.getReturnType() == categoryType
                    && m.getParameterCount() == 1 && m.getParameterTypes()[0] != String.class
                    && m.getParameterTypes()[0] != categoryType) {
                if (factory == null || Modifier.isPublic(m.getModifiers())) factory = m;
            }
        }
        Class<?> idType = factory != null ? factory.getParameterTypes()[0] : null;
        if (idType == null) {
            for (Constructor<?> c : categoryType.getDeclaredConstructors()) {
                if (c.getParameterCount() == 1 && c.getParameterTypes()[0] != String.class) idType = c.getParameterTypes()[0];
            }
        }
        Object id = idType == null ? null : newId(idType, modId, path);
        if (factory != null && id != null) {
            factory.setAccessible(true);
            created = factory.invoke(null, id);
        } else if (id != null) {
            Constructor<?> ctor = categoryType.getDeclaredConstructor(idType);
            ctor.setAccessible(true);
            created = ctor.newInstance(id);
        }
        if (created == null) throw new IllegalStateException("Could not create key category " + cacheKey);
        CATEGORIES.put(cacheKey, created);
        return created;
    }

    /** Any static (String, String) -> id factory (fromNamespaceAndPath / tryBuild / of). */
    private static Object newId(Class<?> idType, String ns, String path) throws ReflectiveOperationException {
        for (Method m : idType.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == idType && m.getParameterCount() == 2
                    && m.getParameterTypes()[0] == String.class && m.getParameterTypes()[1] == String.class) {
                Object v = m.invoke(null, ns, path);
                if (v != null) return v;
            }
        }
        return null;
    }
}
