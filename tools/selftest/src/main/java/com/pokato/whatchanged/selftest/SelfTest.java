package com.pokato.whatchanged.selftest;

import com.pokato.whatchanged.core.Model.WorldReport;
import com.pokato.whatchanged.core.WorldTracker;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * DEV-ONLY automated in-game test, compiled only with -Pselftest (never in release JARs).
 * Env WCTEST_MODE=client|server, WCTEST_STEPS=title,screens,world.
 * Screenshots: writes wctest-out/NAME.req and waits until an external watcher (xwd/import) deletes it.
 */
public final class SelfTest {
    private SelfTest() {}

    private static String mode() {
        String v = System.getenv("WCTEST_MODE");
        return v == null ? "" : v;
    }

    static Path out() {
        return Path.of("wctest-out");
    }

    public static void result(String line) {
        try {
            Files.createDirectories(out());
            Files.writeString(out().resolve("result.txt"), line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("[WCTEST] " + line);
    }

    public static void onEvent(String event, Object arg) {
        if (("toast".equals(event) || "chat".equals(event)) && "client".equals(mode())) {
            result(event.toUpperCase(java.util.Locale.ROOT) + " " + arg);
            return;
        }
        if ("serverStarted".equals(event) && "server".equals(mode())) serverTest((MinecraftServer) arg);
        if ("clientTick".equals(event) && "client".equals(mode())) {
            try {
                // separate class: the dedicated server must never load client classes
                Class.forName("com.pokato.whatchanged.selftest.SelfTestClient").getMethod("tick", Object.class).invoke(null, arg);
            } catch (Throwable t) {
                result("ERROR client tick: " + t);
            }
        }
    }

    // ------------------------------------------------------------------ dedicated server

    private static void serverTest(MinecraftServer server) {
        if (!server.isDedicatedServer()) return;
        Thread t = new Thread(() -> {
            long end = System.currentTimeMillis() + 120_000;
            while (!WorldTracker.ready && System.currentTimeMillis() < end) sleep(200);
            WorldReport r = WorldTracker.current;
            result("SERVER ready=" + WorldTracker.ready + " report=" + (r == null ? "none" : WorldTracker.summary(r)));
            server.execute(() -> {
                SelfTest.command(server, "whatchanged");
                result("DONE");
                server.halt(false);
            });
        }, "wctest-server");
        t.setDaemon(true);
        t.start();
    }

    /** Commands.performPrefixedCommand returns int up to 1.20.2 and void after - use Brigadier directly. */
    static void command(MinecraftServer server, String cmd) {
        try {
            server.getCommands().getDispatcher().execute(cmd, server.createCommandSourceStack());
        } catch (Exception e) {
            result("ERROR command /" + cmd + ": " + e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
