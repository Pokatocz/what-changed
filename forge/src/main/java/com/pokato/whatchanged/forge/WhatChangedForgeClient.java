package com.pokato.whatchanged.forge;

import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.client.ClientCore;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

final class WhatChangedForgeClient {
    private WhatChangedForgeClient() {}

    static void init(FMLJavaModLoadingContext context) {
        modBusListener(context, RegisterKeyMappingsEvent.class,
                (Consumer<RegisterKeyMappingsEvent>) e -> e.register(ClientCore.openKey()));
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> ClientCore.onClientTick());
        ScreenEvent.Init.Post.BUS.addListener(e -> ClientCore.onScreenInit(e.getScreen(), e::addListener));
        RegisterClientCommandsEvent.BUS.addListener(e ->
                e.getDispatcher().register(ClientCore.clientCommand(Commands::literal,
                        (src, msg) -> src.sendSystemMessage(msg))));
    }

    /**
     * Mod-bus events: Forge 56-58 reach them via Event.getBus(BusGroup), Forge 59+ via a static BUS field.
     * Both are EventBus 7 (net.minecraftforge.eventbus.api.bus.EventBus#addListener(Consumer)).
     */
    private static <E> void modBusListener(FMLJavaModLoadingContext context, Class<E> event, Consumer<E> listener) {
        try {
            Object bus = null;
            try {
                Field f = event.getField("BUS");
                if (Modifier.isStatic(f.getModifiers())) bus = f.get(null);
            } catch (NoSuchFieldException ignored) {
            }
            if (bus == null) {
                Object group = context.getClass().getMethod("getModBusGroup").invoke(context);
                Method getBus = null;
                for (Method m : event.getMethods()) {
                    if (m.getName().equals("getBus") && Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1) getBus = m;
                }
                if (getBus == null) throw new NoSuchMethodException(event.getName() + ".getBus");
                bus = getBus.invoke(null, group);
            }
            Method add = null;
            for (Method m : bus.getClass().getMethods()) {
                if (m.getName().equals("addListener") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Consumer.class) add = m;
            }
            if (add == null) throw new NoSuchMethodException("addListener(Consumer) on " + bus.getClass().getName());
            add.setAccessible(true);
            add.invoke(bus, listener);
        } catch (ReflectiveOperationException e) {
            WhatChangedCommon.LOGGER.error("[What Changed?] Could not listen to {}", event.getSimpleName(), e);
        }
    }
}
