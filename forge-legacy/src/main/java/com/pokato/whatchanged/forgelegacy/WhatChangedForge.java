package com.pokato.whatchanged.forgelegacy;

import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.platform.Platform;
import com.pokato.whatchanged.server.ServerHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge 46 (1.20) - 57 (1.21.7). No-arg constructor: Forge 51 does not inject FMLJavaModLoadingContext.
 * Client detection by FMLEnvironment.dist - probing client classes aborts mod loading on a dedicated server.
 */
@Mod(WhatChangedCommon.MOD_ID)
public class WhatChangedForge {
    public WhatChangedForge() {
        Platform.set(new ForgePlatform());
        ServerHooks.init();
        ForgeBus.game(ServerStartedEvent.class, e -> ServerHooks.onServerStarted(e.getServer()));
        ForgeBus.game(ServerStoppedEvent.class, e -> ServerHooks.onServerStopped(e.getServer()));
        ForgeBus.game(OnDatapackSyncEvent.class, e -> {
            if (e.getPlayer() == null) ServerHooks.onDataPackReload(e.getPlayerList().getServer()); // null player = /reload
        });
        ForgeBus.game(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) ServerHooks.onPlayerJoin(sp.getServer(), sp);
        });
        ForgeBus.game(RegisterCommandsEvent.class, e -> ServerHooks.registerCommands(e.getDispatcher()));
        if (FMLEnvironment.dist == Dist.CLIENT) WhatChangedForgeClient.init();
    }
}
