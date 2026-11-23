package com.pokato.whatchanged.server;

import com.mojang.brigadier.CommandDispatcher;
import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.core.Config;
import com.pokato.whatchanged.core.Model.WorldReport;
import com.pokato.whatchanged.core.Names;
import com.pokato.whatchanged.core.WorldTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/** Logical-server side (singleplayer integrated server or dedicated server). Loader entry points forward events here. */
public final class ServerHooks {
    private ServerHooks() {}

    public static void init() {
        Config.get();
        WhatChangedCommon.LOGGER.info("[What Changed?] Ready.");
    }

    public static void onServerStarted(MinecraftServer server) {
        WorldTracker.onServerStarted(server);
        selfTest("serverStarted", server);
    }

    public static void onDataPackReload(MinecraftServer server) {
        WorldTracker.onDataPackReload(server);
    }

    public static void onServerStopped(MinecraftServer server) {
        WorldTracker.onServerStopped();
    }

    public static void onPlayerJoin(MinecraftServer server, ServerPlayer player) {
        WorldTracker.onPlayerJoin(server, player);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("whatchanged")
                .executes(ctx -> summary(ctx.getSource()))
                .then(Commands.literal("export").requires(ServerHooks::isAdmin)
                        .executes(ctx -> export(ctx.getSource())))
                .then(Commands.literal("reset").requires(ServerHooks::isAdmin)
                        .executes(ctx -> reset(ctx.getSource()))));
    }

    /**
     * Console, the singleplayer/LAN host, or a listed operator.
     * (Permission-level APIs changed in 1.21.11; the op list did not.)
     */
    private static boolean isAdmin(CommandSourceStack src) {
        try {
            if (src.getEntity() == null) return true;
            MinecraftServer server = src.getServer();
            String name = src.getEntity().getName().getString();
            if (!server.isDedicatedServer()) {
                String host = Names.profileName(server.getSingleplayerProfile());
                if (host == null || host.equals(name)) return true;
            }
            for (String op : server.getPlayerList().getOps().getUserList()) {
                if (op.equalsIgnoreCase(name)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static int summary(CommandSourceStack src) {
        WorldReport r = WorldTracker.current;
        if (!WorldTracker.ready) {
            send(src, Component.translatableWithFallback("whatchanged.cmd.not_ready",
                    "Still checking this world, try again in a moment."), false);
        } else if (r == null || r.isEmpty()) {
            send(src, Component.translatableWithFallback("whatchanged.cmd.no_changes",
                    "No recipe or worldgen changes recorded for this world."), false);
        } else {
            send(src, Component.translatableWithFallback("whatchanged.chat.server_world",
                    "This world's recipes/worldgen changed since %s: %s", WorldTracker.date(r.previous),
                    WorldTracker.summary(r)), false);
        }
        return 1;
    }

    private static int export(CommandSourceStack src) {
        WorldReport r = WorldTracker.current;
        if (r == null || r.isEmpty()) return summary(src);
        Path out = WorldTracker.writeServerChangelog(r);
        send(src, Component.translatableWithFallback("whatchanged.cmd.exported",
                "Changelog written to %s", out.toAbsolutePath().toString()), true);
        return 1;
    }

    private static int reset(CommandSourceStack src) {
        WorldTracker.reset();
        send(src, Component.translatableWithFallback("whatchanged.cmd.reset",
                "Forgot this world's history; the current state is the new baseline."), true);
        return 1;
    }

    private static void send(CommandSourceStack src, Component body, boolean broadcast) {
        MutableComponent msg = Component.translatableWithFallback("whatchanged.chat.prefix", "[What Changed?] ")
                .withStyle(ChatFormatting.GOLD).append(body.copy().withStyle(ChatFormatting.WHITE));
        src.sendSuccess(() -> msg, broadcast);
    }

    /** Dev-only hook: the self-test class only exists in test builds. */
    private static void selfTest(String event, Object arg) {
        try {
            Class<?> c = Class.forName("com.pokato.whatchanged.selftest.SelfTest", true, ServerHooks.class.getClassLoader());
            c.getMethod("onEvent", String.class, Object.class).invoke(null, event, arg);
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] self-test hook failed: {}", t.toString());
        }
    }
}
