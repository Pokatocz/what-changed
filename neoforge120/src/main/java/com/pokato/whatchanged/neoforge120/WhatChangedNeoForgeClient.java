package com.pokato.whatchanged.neoforge120;

import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.client.ClientCore;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Field;
import java.util.function.Consumer;

final class WhatChangedNeoForgeClient {
    private WhatChangedNeoForgeClient() {}

    static void init(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(ClientCore.openKey()));
        registerClientTick();
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, e -> ClientCore.onScreenInit(e.getScreen(), e::addListener));
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e ->
                e.getDispatcher().register(ClientCore.clientCommand(Commands::literal,
                        (src, msg) -> src.sendSystemMessage(msg))));
    }

    /**
     * 20.5+: client.event.ClientTickEvent.Post. 20.2 - 20.4: event.TickEvent.ClientTickEvent with phase END.
     * Both looked up by name so neither has to exist at compile time or in the other version.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerClientTick() {
        ClassLoader cl = WhatChangedNeoForgeClient.class.getClassLoader();
        try {
            Class post = Class.forName("net.neoforged.neoforge.client.event.ClientTickEvent$Post", false, cl);
            NeoForge.EVENT_BUS.addListener(post, (Consumer<Event>) e -> ClientCore.onClientTick());
            return;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class legacy = Class.forName("net.neoforged.neoforge.event.TickEvent$ClientTickEvent", false, cl);
            Field phase = legacy.getField("phase");
            NeoForge.EVENT_BUS.addListener(legacy, (Consumer<Event>) e -> {
                try {
                    if ("END".equals(String.valueOf(phase.get(e)))) ClientCore.onClientTick();
                } catch (IllegalAccessException ignored) {
                }
            });
        } catch (ReflectiveOperationException ex) {
            WhatChangedCommon.LOGGER.error("[What Changed?] No client tick event found - the J key will not work", ex);
        }
    }
}
