package com.pokato.whatchanged.fabric121;

import com.pokato.whatchanged.fabric.FabricPlatform;
import com.pokato.whatchanged.platform.Platform;
import com.pokato.whatchanged.server.ServerHooks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class WhatChangedFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.set(new FabricPlatform());
        ServerHooks.init();
        ServerLifecycleEvents.SERVER_STARTED.register(ServerHooks::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(ServerHooks::onServerStopped);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) ServerHooks.onDataPackReload(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerHooks.onPlayerJoin(server, handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> ServerHooks.registerCommands(dispatcher));
    }
}
