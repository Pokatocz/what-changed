package com.pokato.whatchanged.client26;

import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.client.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 26.x (unobfuscated, so names are safe to reflect on).
 * 26.1 keeps Minecraft.screen / setScreen / getToastManager; 26.2 moved them to Gui (screen(), setScreen, toastManager()).
 */
public final class Client26Compat implements ClientCompat {
    private final Method guiScreen, guiSetScreen, guiToasts, mcSetScreen, mcToasts, guiChat, mcOverlay, guiOverlay;
    private Method hudChat;
    private final Field mcScreen;
    private Field guiHud;

    public Client26Compat() {
        Class<?> gui = net.minecraft.client.gui.Gui.class;
        guiScreen = method(gui, "screen");
        guiSetScreen = method(gui, "setScreen", Screen.class);
        guiToasts = method(gui, "toastManager");
        mcSetScreen = method(Minecraft.class, "setScreen", Screen.class);
        mcToasts = method(Minecraft.class, "getToastManager");
        guiChat = method(gui, "getChat");
        mcOverlay = method(Minecraft.class, "getOverlay"); // 26.1
        guiOverlay = method(gui, "overlay");               // 26.2+
        if (guiChat == null) {
            try {
                guiHud = gui.getField("hud");
                hudChat = method(guiHud.getType(), "getChat");
            } catch (NoSuchFieldException ignored) {
            }
        }
        Field f = null;
        try {
            f = Minecraft.class.getField("screen");
        } catch (NoSuchFieldException ignored) {
        }
        mcScreen = f;
        if ((guiScreen == null && mcScreen == null) || (guiSetScreen == null && mcSetScreen == null)) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] Screen access not found on this game version");
        }
    }

    private static Method method(Class<?> c, String name, Class<?>... params) {
        try {
            return c.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public Screen currentScreen() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (guiScreen != null) return (Screen) guiScreen.invoke(mc.gui);
            if (mcScreen != null) return (Screen) mcScreen.get(mc);
        } catch (ReflectiveOperationException e) {
            WhatChangedCommon.LOGGER.debug("[What Changed?] currentScreen failed: {}", e.toString());
        }
        return null;
    }

    @Override
    public boolean loadingOverlay() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (guiOverlay != null) return guiOverlay.invoke(mc.gui) != null;
            if (mcOverlay != null) return mcOverlay.invoke(mc) != null;
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    @Override
    public void setScreen(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (guiSetScreen != null) guiSetScreen.invoke(mc.gui, screen);
            else if (mcSetScreen != null) mcSetScreen.invoke(mc, screen);
        } catch (ReflectiveOperationException e) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] setScreen failed: {}", e.toString());
        }
    }

    private ToastManager toasts() throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        if (guiToasts != null) return (ToastManager) guiToasts.invoke(mc.gui);
        if (mcToasts != null) return (ToastManager) mcToasts.invoke(mc);
        return null;
    }

    /** 26.1: Gui.getChat(); 26.2+: Gui.hud.getChat(). */
    @Override
    public void addChatMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Object owner = mc.gui;
            if (guiChat == null) {
                owner = guiHud != null ? guiHud.get(mc.gui) : null;
            }
            Method m = guiChat != null ? guiChat : hudChat;
            if (owner != null && m != null) ((ChatComponent) m.invoke(owner)).addClientSystemMessage(message);
        } catch (ReflectiveOperationException e) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] chat message failed: {}", e.toString());
        }
    }

    @Override
    public void toast(Component title, Component body) {
        try {
            ToastManager t = toasts();
            if (t != null) SystemToast.add(t, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, body);
        } catch (ReflectiveOperationException e) {
            WhatChangedCommon.LOGGER.debug("[What Changed?] toast failed: {}", e.toString());
        }
    }

    @Override
    public void clearToasts() {
        try {
            ToastManager t = toasts();
            if (t != null) t.clear();
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public Screen createScreen(Screen parent) {
        return new WhatChangedScreen26(parent);
    }
}
