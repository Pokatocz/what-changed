package com.pokato.whatchanged.g1202;

import com.pokato.whatchanged.client.ClientCompat;
import com.pokato.whatchanged.gscreen.WhatChangedScreenLegacy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.20.2: ToastComponent + SystemToast.SystemToastIds (SystemToastId from 1.20.3). */
public final class Client1202Compat implements ClientCompat {
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
        SystemToast.add(Minecraft.getInstance().getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, body);
    }

    @Override
    public boolean loadingOverlay() {
        return Minecraft.getInstance().getOverlay() != null;
    }

    @Override
    public void clearToasts() {
        Minecraft.getInstance().getToasts().clear();
    }

    @Override
    public Screen createScreen(Screen parent) {
        return new WhatChangedScreenLegacy(parent);
    }
}
