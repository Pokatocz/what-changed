package com.pokato.whatchanged.forgelegacy;

import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.platform.Platform;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ModList is an instance API in Forge 58 - 61 (ModList.get().getMods()) and fully static from Forge 62 (26.1)
 * (ModList.getMods(), no get()). Only getMods() is used, resolved reflectively; IModInfo is stable.
 */
public final class ForgePlatform implements Platform {
    private static Method getMods;
    private static Object modListInstance;
    private static boolean resolved;

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @SuppressWarnings("unchecked")
    private static List<IModInfo> modInfos() {
        try {
            synchronized (ForgePlatform.class) {
                if (!resolved) {
                    resolved = true;
                    getMods = ModList.class.getMethod("getMods");
                    if (!Modifier.isStatic(getMods.getModifiers())) modListInstance = ModList.class.getMethod("get").invoke(null);
                }
            }
            if (getMods == null) return List.of();
            Object r = getMods.invoke(modListInstance);
            return r == null ? List.of() : (List<IModInfo>) r;
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] Could not read the Forge mod list: {}", t.toString());
            return List.of();
        }
    }

    @Override
    public List<ModEntry> mods() {
        List<ModEntry> out = new ArrayList<>();
        for (IModInfo m : modInfos()) {
            String url = null;
            try {
                url = m.getModURL().map(Object::toString).orElse(null);
            } catch (Throwable ignored) {
            }
            out.add(new ModEntry(m.getModId(), m.getDisplayName(), String.valueOf(m.getVersion()), url));
        }
        return out;
    }

    private static IModInfo find(String id) {
        for (IModInfo m : modInfos()) if (id.equals(m.getModId())) return m;
        return null;
    }

    @Override
    public String modName(String id) {
        IModInfo m = find(id);
        return m == null ? null : m.getDisplayName();
    }

    @Override
    public String minecraftVersion() {
        IModInfo m = find("minecraft");
        return m == null ? "?" : String.valueOf(m.getVersion());
    }

    /** NeoForge for 1.20.1 is a Forge 47 fork: same mod id "forge", but its display name says NeoForge. */
    @Override
    public String loaderName() {
        IModInfo forge = find("forge");
        String name = forge == null ? null : forge.getDisplayName();
        return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("neoforge") ? "NeoForge" : "Forge";
    }
}
