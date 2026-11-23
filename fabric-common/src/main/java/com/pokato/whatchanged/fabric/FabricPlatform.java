package com.pokato.whatchanged.fabric;

import com.pokato.whatchanged.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FabricPlatform implements Platform {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public List<ModEntry> mods() {
        List<ModEntry> out = new ArrayList<>();
        for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
            if (c.getContainingMod().isPresent()) continue; // nested library (e.g. Fabric API modules)
            ModMetadata m = c.getMetadata();
            out.add(new ModEntry(m.getId(), m.getName(), m.getVersion().getFriendlyString(), url(m)));
        }
        return out;
    }

    private static String url(ModMetadata meta) {
        try {
            ContactInformation contact = meta.getContact();
            for (String key : new String[]{"homepage", "sources", "issues"}) {
                String v = contact.get(key).orElse(null);
                if (v != null && v.startsWith("http")) return v;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public String modName(String id) {
        return FabricLoader.getInstance().getModContainer(id).map(c -> c.getMetadata().getName()).orElse(null);
    }

    @Override
    public String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    @Override
    public String loaderName() {
        return "Fabric";
    }
}
