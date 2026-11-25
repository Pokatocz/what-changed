package com.pokato.whatchanged.forgelegacy;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/**
 * Listener registration for Forge 46 (1.20) - 57 (1.21.7).
 * EventBus 6 (Forge 46 - 55): MinecraftForge.EVENT_BUS / the mod event bus.
 * EventBus 7 (Forge 56 - 57): a static BUS field on the event class, or getBus(BusGroup) for mod-bus events.
 * Compiled against EventBus 6; the EventBus 7 path is reflective.
 */
public final class ForgeBus {
    private static final boolean EB7 = present("net.minecraftforge.eventbus.api.bus.EventBus");

    private ForgeBus() {}

    public static <E> void game(Class<E> type, Consumer<E> handler) {
        listen(type, false, handler);
    }

    public static <E> void mod(Class<E> type, Consumer<E> handler) {
        listen(type, true, handler);
    }

    public static <E> void listen(Class<E> type, boolean modBus, Consumer<E> handler) {
        if (EB7) {
            Eb7.listen(type, handler);
        } else {
            Eb6.listen(type, modBus, handler);
        }
    }

    static boolean present(String name) {
        try {
            Class.forName(name, false, ForgeBus.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Only loaded on EventBus 6. */
    private static final class Eb6 {
        @SuppressWarnings({"unchecked", "rawtypes"})
        static void listen(Class type, boolean modBus, Consumer handler) {
            IEventBus bus = modBus ? FMLJavaModLoadingContext.get().getModEventBus() : MinecraftForge.EVENT_BUS;
            bus.addListener(EventPriority.NORMAL, false, type, handler);
        }
    }

    /** Only used on EventBus 7. */
    private static final class Eb7 {
        static void listen(Class<?> type, Consumer<?> handler) {
            try {
                Object bus;
                Field f = null;
                try {
                    f = type.getField("BUS");
                } catch (NoSuchFieldException ignored) {
                }
                if (f != null && Modifier.isStatic(f.getModifiers())) {
                    bus = f.get(null);
                } else {
                    Class<?> groupType = Class.forName("net.minecraftforge.eventbus.api.bus.BusGroup");
                    Object ctx = FMLJavaModLoadingContext.class.getMethod("get").invoke(null);
                    Object group = ctx.getClass().getMethod("getModBusGroup").invoke(ctx);
                    bus = type.getMethod("getBus", groupType).invoke(null, group);
                }
                Method add = Class.forName("net.minecraftforge.eventbus.api.bus.EventBus").getMethod("addListener", Consumer.class);
                add.invoke(bus, handler);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("What Changed?: could not listen to " + type.getName(), e);
            }
        }
    }
}
