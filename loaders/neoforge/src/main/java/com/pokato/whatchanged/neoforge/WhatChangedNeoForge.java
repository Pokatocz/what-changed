package com.pokato.whatchanged.neoforge;

import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.platform.Platform;
import com.pokato.whatchanged.server.ServerHooks;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** NeoForge 21.8 (1.21.8) - 26.3. The NeoForge event API used here is identical across that range. */
@Mod(WhatChangedCommon.MOD_ID)
public class WhatChangedNeoForge {
    public WhatChangedNeoForge(IEventBus modBus, Dist dist) {
        Platform.set(new NeoForgePlatform());
        ServerHooks.init();
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> ServerHooks.onServerStarted(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> ServerHooks.onServerStopped(e.getServer()));
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, e -> {
            if (e.getPlayer() == null) ServerHooks.onDataPackReload(e.getPlayerList().getServer()); // null player = /reload
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) ServerHooks.onPlayerJoin(e.getEntity().level().getServer(), sp);
        });
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> ServerHooks.registerCommands(e.getDispatcher()));
        if (dist.isClient()) WhatChangedNeoForgeClient.init(modBus);
    }
}
