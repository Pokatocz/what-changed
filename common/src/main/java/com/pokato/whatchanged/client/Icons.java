package com.pokato.whatchanged.client;

import com.pokato.whatchanged.WhatChangedCommon;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

public final class Icons {
    private static final String[] NEEDS_WORLD = new String[]{"software.bernie.geckolib.", "mod.azure.azurelib."};
    private static final Map<Class<?>, Boolean> needsWorld = new ConcurrentHashMap<>();
    private static final Set<Item> broken = ConcurrentHashMap.newKeySet();

    private Icons() {
    }

    public static boolean canDraw(Item item, boolean drawnLater) {
        if (item == null || broken.contains(item)) {
            return false;
        } else if (!drawnLater) {
            return true;
        } else {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.level != null ? true : !needsWorld.computeIfAbsent(item.getClass(), Icons::animated);
        }
    }

    public static void failed(Item item, Throwable error) {
        if (item != null && broken.add(item)) {
            Throwable root = error;

            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }

            if (broken.size() == 1) {
                WhatChangedCommon.LOGGER
                    .warn(
                        "[What Changed?] Could not draw the icon of {} ({}), showing a placeholder (further icons that fail are logged at debug level)",
                        item,
                        root.toString()
                    );
            } else {
                WhatChangedCommon.LOGGER.debug("[What Changed?] Could not draw the icon of {} ({})", item, root.toString());
            }

            WhatChangedCommon.LOGGER.debug("[What Changed?] Icon error", error);
        }
    }

    private static boolean animated(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            if (fromLibrary(k)) {
                return true;
            }

            for (Class<?> i : k.getInterfaces()) {
                if (interfaceFromLibrary(i)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean interfaceFromLibrary(Class<?> i) {
        if (fromLibrary(i)) {
            return true;
        } else {
            for (Class<?> parent : i.getInterfaces()) {
                if (interfaceFromLibrary(parent)) {
                    return true;
                }
            }

            return false;
        }
    }

    private static boolean fromLibrary(Class<?> k) {
        String n = k.getName();

        for (String p : NEEDS_WORLD) {
            if (n.startsWith(p)) {
                return true;
            }
        }

        return false;
    }
}
