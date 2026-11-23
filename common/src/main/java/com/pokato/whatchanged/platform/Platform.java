package com.pokato.whatchanged.platform;

import java.nio.file.Path;
import java.util.List;

/** The few things that differ between Fabric, Forge and NeoForge. Set once by the loader entry point. */
public interface Platform {
    /** One installed mod (top-level only, no nested libraries). */
    record ModEntry(String id, String name, String version, String url) {}

    Path configDir();

    List<ModEntry> mods();

    /** Display name for a mod id, or null when unknown. */
    String modName(String id);

    String minecraftVersion();

    String loaderName();

    static Platform get() {
        Platform p = Holder.instance;
        if (p == null) throw new IllegalStateException("What Changed? platform not initialised");
        return p;
    }

    static void set(Platform platform) {
        Holder.instance = platform;
    }

    final class Holder {
        private static volatile Platform instance;

        private Holder() {}
    }
}
