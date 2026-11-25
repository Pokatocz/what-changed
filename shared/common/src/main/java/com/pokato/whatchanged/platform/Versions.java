package com.pokato.whatchanged.platform;

import com.pokato.whatchanged.WhatChangedCommon;

/**
 * Picks the version-specific implementation of an interface by the running Minecraft version.
 * Each JAR only contains the implementations for its own version range, so the newest one that exists in the
 * JAR and is not newer than the game wins. Selection is by version number, not by probing Minecraft classes,
 * because on Fabric 1.20 - 1.21.11 Minecraft classes have intermediary names at runtime.
 */
public final class Versions {
    private Versions() {}

    /** "1.20.1" -> {1,20,1}; "26.1-snapshot-2" / "1.21.11-rc1" -> leading numbers only. */
    public static int[] parse(String v) {
        int[] out = new int[3];
        if (v == null) return out;
        int part = 0, num = 0;
        boolean any = false;
        for (int i = 0; i < v.length() && part < 3; i++) {
            char c = v.charAt(i);
            if (c >= '0' && c <= '9') {
                num = num * 10 + (c - '0');
                any = true;
            } else if (c == '.') {
                out[part++] = num;
                num = 0;
            } else {
                break;
            }
        }
        if (any && part < 3) out[part] = num;
        return out;
    }

    public static int compare(String a, String b) {
        int[] x = parse(a), y = parse(b);
        for (int i = 0; i < 3; i++) if (x[i] != y[i]) return Integer.compare(x[i], y[i]);
        return 0;
    }

    public static String current() {
        try {
            return Platform.get().minecraftVersion();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * @param candidates pairs of (first Minecraft version, implementation class name), oldest first
     */
    public static <T> T pick(Class<T> type, String... candidates) {
        String mc = current();
        ClassLoader cl = Versions.class.getClassLoader();
        String chosen = null, lowestPresent = null;
        for (int i = 0; i + 1 < candidates.length; i += 2) {
            String since = candidates[i], impl = candidates[i + 1];
            if (!present(impl, cl)) continue;
            if (lowestPresent == null) lowestPresent = impl;
            if (mc == null || compare(mc, since) >= 0) chosen = impl;
        }
        if (chosen == null) chosen = lowestPresent;
        if (chosen == null) throw new IllegalStateException("What Changed?: no " + type.getSimpleName() + " implementation in this build");
        try {
            T inst = type.cast(Class.forName(chosen, true, cl).getConstructor().newInstance());
            WhatChangedCommon.LOGGER.info("[What Changed?] Minecraft {}: using {}", mc, chosen.substring(chosen.lastIndexOf('.') + 1));
            return inst;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("What Changed?: could not create " + chosen, e);
        }
    }

    private static boolean present(String name, ClassLoader cl) {
        try {
            Class.forName(name, false, cl);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
