package com.pokato.whatchanged.g1212;

import com.pokato.whatchanged.gscreen.WhatChangedScreenLegacy;

import com.pokato.whatchanged.client.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.21.2 - 1.21.5: ToastManager. */
public final class Client1212Compat implements ClientCompat {
    @Override
    public Screen currentScreen() {
        return Minecraft.getInstance().screen;
    }

    @Override
    public void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    public void addChatMessage(Component message) {
        Minecraft.getInstance().gui.getChat().addMessage(message);
    }

    @Override
    public void toast(Component title, Component body) {
        SystemToast.add(Minecraft.getInstance().getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, body);
    }

    @Override
    public boolean loadingOverlay() {
        return Minecraft.getInstance().getOverlay() != null;
    }

    @Override
    public void clearToasts() {
        Minecraft.getInstance().getToastManager().clear();
    }

    @Override
    public Screen createScreen(Screen parent) {
        return new WhatChangedScreenLegacy(parent);
    }
}
