package com.pokato.whatchanged.fabric121;

import com.pokato.whatchanged.client.ClientCore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

public class WhatChangedFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(ClientCore.openKey());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientCore.onClientTick());
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
                ClientCore.onScreenInit(screen, widget -> Screens.getButtons(screen).add(widget)));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCore.clientCommand(ClientCommandManager::literal,
                        (src, msg) -> src.sendFeedback(msg))));
    }
}
