package com.pokato.whatchanged.forge;

import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.platform.Platform;
import com.pokato.whatchanged.server.ServerHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;

/** Forge 58 (1.21.8) - 66 (26.3). EventBus 7 throughout: game events expose a static BUS. */
@Mod(WhatChangedCommon.MOD_ID)
public class WhatChangedForge {
    public WhatChangedForge(FMLJavaModLoadingContext context) {
        Platform.set(new ForgePlatform());
        ServerHooks.init();
        ServerStartedEvent.BUS.addListener(e -> ServerHooks.onServerStarted(e.getServer()));
        ServerStoppedEvent.BUS.addListener(e -> ServerHooks.onServerStopped(e.getServer()));
        OnDatapackSyncEvent.BUS.addListener(e -> {
            if (e.getPlayer() == null) ServerHooks.onDataPackReload(e.getPlayerList().getServer()); // null player = /reload
        });
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer sp) ServerHooks.onPlayerJoin(sp.level().getServer(), sp);
        });
        RegisterCommandsEvent.BUS.addListener(e -> ServerHooks.registerCommands(e.getDispatcher()));
        if (isClient()) WhatChangedForgeClient.init(context);
    }

    /** FMLEnvironment.dist is identical in Forge 58 - 66. Never probe client classes: on a dedicated server
     *  Forge's dist cleaner throws a RuntimeException (not ClassNotFoundException) and aborts mod loading. */
    private static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }
}
