package com.pokato.whatchanged.client;

import com.pokato.whatchanged.platform.Versions;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Client APIs that moved between 1.20 and 26.x (screen management, toasts, chat, the GUI renderer, the mouse
 * wheel). One implementation per API generation, each compiled against its own game version:
 * <pre>
 *   g120      1.20 - 1.20.1   ToastComponent + SystemToastIds, explicit background, 3-arg mouseScrolled
 *   g1202     1.20.2          SystemToastIds, 4-arg mouseScrolled (its screen is reused up to 1.21.5)
 *   g1203     1.20.3 - 1.21.1 SystemToastId
 *   g1212     1.21.2 - 1.21.5 ToastManager
 *   client121 1.21.6 - 1.21.11 deferred GUI rendering (setComponentTooltipForNextFrame)
 *   client26  26.x            GuiGraphicsExtractor, Gui.setScreen on 26.2+
 * </pre>
 */
public interface ClientCompat {
    Screen currentScreen();

    void setScreen(Screen screen);

    void addChatMessage(Component message);

    void toast(Component title, Component body);

    void clearToasts();

    /** True while the resource-loading overlay covers the screen (fonts are not usable yet). */
    default boolean loadingOverlay() {
        return false;
    }

    /** The What Changed? screen, built on the renderer this game version uses. */
    Screen createScreen(Screen parent);

    static ClientCompat get() {
        ClientCompat c = Holder.instance;
        if (c == null) {
            synchronized (Holder.class) {
                if (Holder.instance == null) Holder.instance = create();
                c = Holder.instance;
            }
        }
        return c;
    }

    private static ClientCompat create() {
        return Versions.pick(ClientCompat.class,
                "1.20", "com.pokato.whatchanged.g120.Client120Compat",
                "1.20.2", "com.pokato.whatchanged.g1202.Client1202Compat",
                "1.20.3", "com.pokato.whatchanged.g1203.Client1203Compat",
                "1.21.2", "com.pokato.whatchanged.g1212.Client1212Compat",
                "1.21.6", "com.pokato.whatchanged.client121.Client121Compat",
                "26.1", "com.pokato.whatchanged.client26.Client26Compat");
    }

    final class Holder {
        private static volatile ClientCompat instance;

        private Holder() {}
    }
}
