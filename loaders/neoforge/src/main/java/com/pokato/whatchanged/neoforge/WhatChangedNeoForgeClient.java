package com.pokato.whatchanged.neoforge;

import com.pokato.whatchanged.client.ClientCore;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

final class WhatChangedNeoForgeClient {
    private WhatChangedNeoForgeClient() {}

    static void init(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(ClientCore.openKey()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> ClientCore.onClientTick());
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, e -> ClientCore.onScreenInit(e.getScreen(), e::addListener));
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e ->
                e.getDispatcher().register(ClientCore.clientCommand(Commands::literal,
                        (src, msg) -> src.sendSystemMessage(msg))));
    }
}
