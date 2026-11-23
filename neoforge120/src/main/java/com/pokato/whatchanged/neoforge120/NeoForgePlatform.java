package com.pokato.whatchanged.neoforge120;

import com.pokato.whatchanged.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NeoForgePlatform implements Platform {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public List<ModEntry> mods() {
        List<ModEntry> out = new ArrayList<>();
        for (IModInfo m : ModList.get().getMods()) {
            String url = null;
            try {
                url = m.getModURL().map(Object::toString).orElse(null);
            } catch (Throwable ignored) {
            }
            out.add(new ModEntry(m.getModId(), m.getDisplayName(), String.valueOf(m.getVersion()), url));
        }
        return out;
    }

    @Override
    public String modName(String id) {
        return ModList.get().getModContainerById(id).map(c -> c.getModInfo().getDisplayName()).orElse(null);
    }

    @Override
    public String minecraftVersion() {
        return ModList.get().getModContainerById("minecraft").map(c -> String.valueOf(c.getModInfo().getVersion())).orElse("?");
    }

    @Override
    public String loaderName() {
        return "NeoForge";
    }
}
