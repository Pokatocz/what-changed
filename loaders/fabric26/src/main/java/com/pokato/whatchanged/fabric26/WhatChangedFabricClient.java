package com.pokato.whatchanged.fabric26;

import com.pokato.whatchanged.client.ClientCore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

public class WhatChangedFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(ClientCore.openKey());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientCore.onClientTick());
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
                ClientCore.onScreenInit(screen, widget -> Screens.getWidgets(screen).add(widget)));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCore.clientCommand(ClientCommands::literal,
                        (src, msg) -> src.sendFeedback(msg))));
    }
}
