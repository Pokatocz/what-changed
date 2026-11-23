package com.pokato.whatchanged.core;

import java.util.List;
import java.util.Locale;

/** The mod loader as a "mod": its id in the mod list, its display name, and where its changelog lives. */
public final class Loaders {
    private Loaders() {}

    /** Platform.loaderName() ("Fabric", "Forge", "NeoForge") -> the mod ids the loader can appear under, best first. */
    public static List<String> modIds(String loaderName) {
        if (loaderName == null) return List.of();
        return switch (loaderName.toLowerCase(Locale.ROOT)) {
            case "fabric" -> List.of("fabricloader");
            case "quilt" -> List.of("quilt_loader", "fabricloader");
            case "forge" -> List.of("forge");
            case "neoforge" -> List.of("neoforge", "forge"); // NeoForge for 1.20.1 is a Forge 47 fork: mod id "forge"
            default -> List.of();
        };
    }

    /** The first of the loader's mod ids that is in this mod list, or null. */
    public static String findIn(String loaderName, java.util.Map<String, ?> mods) {
        if (mods == null) return null;
        for (String id : modIds(loaderName)) if (mods.containsKey(id)) return id;
        return null;
    }

    public static String displayName(String loaderName) {
        if (loaderName == null) return "Mod loader";
        return switch (loaderName.toLowerCase(Locale.ROOT)) {
            case "fabric" -> "Fabric Loader";
            case "quilt" -> "Quilt Loader";
            default -> loaderName;
        };
    }

    /** Official changelog of a loader version, or null. */
    public static String changelogUrl(String loaderName, String version, String minecraft) {
        if (loaderName == null || version == null || version.isBlank()) return null;
        return switch (loaderName.toLowerCase(Locale.ROOT)) {
            case "fabric", "fabric loader" -> "https://github.com/FabricMC/fabric-loader/releases/tag/" + version;
            case "forge" -> minecraft == null ? null
                    : "https://maven.minecraftforge.net/net/minecraftforge/forge/" + minecraft + "-" + version
                    + "/forge-" + minecraft + "-" + version + "-changelog.txt";
            case "neoforge" -> version.startsWith("47.") && minecraft != null // the 1.20.1 fork keeps Forge's coordinates
                    ? "https://maven.neoforged.net/releases/net/neoforged/forge/" + minecraft + "-" + version
                    + "/forge-" + minecraft + "-" + version + "-changelog.txt"
                    : "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + version
                    + "/neoforge-" + version + "-changelog.txt";
            default -> null;
        };
    }

    /** "NeoForge 21.1.200 → 21.1.209", or "Forge 47.2.0 → NeoForge 47.1.106" when the loader was switched. */
    public static String describe(Model.PackReport r) {
        if (r.loaderSwitched()) {
            return displayName(r.loaderOldName) + " " + r.loaderOld + " → " + displayName(r.loaderName) + " " + r.loaderNew;
        }
        return displayName(r.loaderName) + " " + r.loaderOld + " → " + r.loaderNew;
    }

    /** Minecraft Wiki page of a Java Edition release. */
    public static String minecraftUrl(String version) {
        return version == null || version.isBlank() ? null : "https://minecraft.wiki/w/Java_Edition_" + version;
    }
}
