package com.pokato.whatchanged.core;

import com.pokato.whatchanged.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Human readable names and ids. Deliberately avoids ResourceLocation/Identifier (renamed in 1.21.11):
 * ids come from ResourceKey.toString(), stable across 1.20 - 26.x.
 */
public final class Names {
    private Names() {}

    public static String namespace(String id) {
        int i = id.indexOf(':');
        return i < 0 ? "minecraft" : id.substring(0, i);
    }

    public static String path(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    /** "biomes_o_plenty" -> "Biomes O Plenty", "crafting/brass_hand" -> "Brass Hand". */
    public static String prettify(String raw) {
        String s = raw;
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        StringBuilder b = new StringBuilder();
        for (String w : s.split("[_\\-.]+")) {
            if (w.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.isEmpty() ? raw : b.toString();
    }

    /** "ResourceKey[minecraft:recipe / minecraft:stone]" -> "minecraft:stone". */
    public static String keyId(ResourceKey<?> key) {
        String s = String.valueOf(key);
        int slash = s.lastIndexOf(" / ");
        if (slash >= 0) {
            int end = s.endsWith("]") ? s.length() - 1 : s.length();
            return s.substring(slash + 3, end).trim();
        }
        return s;
    }

    /** Holder.Reference.key() exists unchanged from 1.20 to 26.x (getRegisteredName() only from 1.20.5). */
    public static String itemId(Item item) {
        return keyId(item.builtInRegistryHolder().key());
    }

    public static String entityId(EntityType<?> type) {
        return keyId(type.builtInRegistryHolder().key());
    }

    /** Display name of the mod that owns a namespace, looking in the given snapshots first. */
    @SafeVarargs
    public static String modName(String namespace, Map<String, Model.ModInfo>... known) {
        if ("minecraft".equals(namespace)) return "Minecraft";
        for (Map<String, Model.ModInfo> m : known) {
            if (m == null) continue;
            Model.ModInfo info = m.get(namespace);
            if (info != null && info.name != null && !info.name.isBlank()) return info.name;
        }
        String n = null;
        try {
            n = Platform.get().modName(namespace);
        } catch (Throwable ignored) {
        }
        return n != null && !n.isBlank() ? n : prettify(namespace);
    }

    public static String itemName(Item item, String fallbackId) {
        try {
            String n = new ItemStack(item).getHoverName().getString();
            if (n != null && !n.isBlank()) return n;
        } catch (Throwable ignored) {
            // 26.3+: item components are only bound once a world's registries are loaded (not on the title screen)
        }
        try {
            String key = item.getDescriptionId();
            Language lang = Language.getInstance();
            if (key != null && lang.has(key)) return lang.getOrDefault(key);
        } catch (Throwable ignored) {
        }
        return prettify(path(fallbackId));
    }

    /** An ItemStack for an icon, or null when the game can't build one right now (26.3+ outside a world). */
    public static ItemStack stack(Item item) {
        if (item == null) return null;
        try {
            return new ItemStack(item);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String entityName(EntityType<?> type, String fallbackId) {
        try {
            String n = type.getDescription().getString();
            if (n != null && !n.isBlank()) return n;
        } catch (Throwable ignored) {
        }
        return prettify(path(fallbackId));
    }

    /** Translated name for an id like biome.<ns>.<path> / enchantment.<ns>.<path>, or a prettified path. */
    public static String translatedOrPretty(String keyPrefix, String id) {
        if (keyPrefix != null) {
            String key = keyPrefix + "." + namespace(id) + "." + path(id).replace('/', '.');
            try {
                Language lang = Language.getInstance();
                if (lang.has(key)) return lang.getOrDefault(key);
            } catch (Throwable ignored) {
            }
        }
        return prettify(path(id));
    }

    /** id -> Item for every registered item. */
    public static Map<String, Item> itemsById() {
        Map<String, Item> map = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) map.put(itemId(item), item);
        return map;
    }

    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /** authlib 6 has getName(), authlib 7 (1.21.9+) has name(); authlib names are never obfuscated. */
    public static String profileName(Object profile) {
        if (profile == null) return null;
        for (String m : new String[]{"name", "getName"}) {
            try {
                Method method = profile.getClass().getMethod(m);
                Object v = method.invoke(profile);
                if (v != null) return v.toString();
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
