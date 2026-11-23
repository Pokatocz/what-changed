package com.pokato.whatchanged.forgelegacy;

import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.client.ClientCore;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;

import java.lang.reflect.Field;
import java.util.function.Consumer;

final class WhatChangedForgeClient {
    private WhatChangedForgeClient() {}

    static void init() {
        ForgeBus.mod(RegisterKeyMappingsEvent.class, e -> e.register(ClientCore.openKey()));
        registerClientTick();
        ForgeBus.game(ScreenEvent.Init.Post.class, e -> ClientCore.onScreenInit(e.getScreen(), e::addListener));
        ForgeBus.game(RegisterClientCommandsEvent.class, e ->
                e.getDispatcher().register(ClientCore.clientCommand(Commands::literal,
                        (src, msg) -> src.sendSystemMessage(msg))));
    }

    /** Forge 56+: TickEvent.ClientTickEvent.Post. Forge 46 - 55: TickEvent.ClientTickEvent with phase END. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerClientTick() {
        ClassLoader cl = WhatChangedForgeClient.class.getClassLoader();
        try {
            Class post = Class.forName("net.minecraftforge.event.TickEvent$ClientTickEvent$Post", false, cl);
            ForgeBus.game(post, (Consumer<Object>) e -> ClientCore.onClientTick());
            return;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class legacy = Class.forName("net.minecraftforge.event.TickEvent$ClientTickEvent", false, cl);
            Field phase = legacy.getField("phase");
            ForgeBus.game(legacy, (Consumer<Object>) e -> {
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
