package com.pokato.whatchanged.selftest;

import com.pokato.whatchanged.client.AbstractWhatChangedScreen;
import com.pokato.whatchanged.client.ClientCompat;
import com.pokato.whatchanged.core.Config;
import com.pokato.whatchanged.core.Model;
import com.pokato.whatchanged.core.Model.PackReport;
import com.pokato.whatchanged.core.Model.WorldReport;
import com.pokato.whatchanged.core.PackTracker;
import com.pokato.whatchanged.core.WorldTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

import static com.pokato.whatchanged.selftest.SelfTest.out;
import static com.pokato.whatchanged.selftest.SelfTest.result;

/** DEV-ONLY client half of the self-test (see SelfTest). */
public final class SelfTestClient {
    private record Step(String name, BooleanSupplier ready, int delay, Runnable action) {}

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean started;
    private static int wait, guard;
    private static Screen title;
    private static AbstractWhatChangedScreen screen;
    private static Path pendingShot;

    private SelfTestClient() {}

    public static void tick(Object mc) {
        clientTick((Minecraft) mc);
    }

    private static void clientTick(Minecraft mc) {
        ClientCompat compat = ClientCompat.get();
        if (!started) {
            if (!(compat.currentScreen() instanceof TitleScreen)) return;
            started = true;
            title = compat.currentScreen();
            String steps = System.getenv("WCTEST_STEPS");
            build(mc, compat, steps == null ? "title" : steps);
        }
        if (pendingShot != null) {
            if (Files.exists(pendingShot) && guard++ < 20 * 30) return;
            pendingShot = null;
        }
        Step step = STEPS.peekFirst();
        if (step == null) return;
        if (++guard > 20 * 300) {
            result("TIMEOUT at step " + step.name());
            STEPS.clear();
            mc.stop();
            return;
        }
        if (step.ready() != null && !step.ready().getAsBoolean()) return;
        if (wait < step.delay()) {
            wait++;
            return;
        }
        wait = 0;
        guard = 0;
        STEPS.pollFirst();
        try {
            step.action().run();
        } catch (Throwable t) {
            result("ERROR in step " + step.name() + ": " + t);
            t.printStackTrace();
        }
    }

    private static void build(Minecraft mc, ClientCompat compat, String plan) {
        add("title-shot", () -> !compat.loadingOverlay(), 60, () -> {
            shot("title");
            result("TITLEBUTTON " + titleButton(compat));
            PackReport r = PackTracker.report();
            result("PACK firstRun=" + PackTracker.isFirstRun() + " unseen=" + PackTracker.hasUnseen() + " report="
                    + (r == null ? "none" : ("+" + r.added() + " -" + r.removed() + " upd=" + r.modsUpdated.size()
                    + " items+" + r.itemsAdded.size() + " items-" + r.itemsRemoved.size() + " mobs+" + r.entitiesAdded.size()
                    + " mods+" + r.modsAdded.size() + " mods-" + r.modsRemoved.size()
                    + " down=" + r.modsDowngraded.size() + " loader=" + (r.loaderChanged() ? r.loaderName + ":" + r.loaderOld + "->" + r.loaderNew : "-")
                    + " configs=" + r.configs.size())));
            if (r != null && !r.isEmpty()) {
                result("SUMMARY " + com.pokato.whatchanged.client.ClientCore.summary(r, false).getString()
                        + " | compact=" + com.pokato.whatchanged.client.ClientCore.compact(r));
            }
            if (r != null) {
                for (Model.ModChange m : r.modsDowngraded) result("DOWNGRADED " + m.id + " " + m.oldVersion + " -> " + m.newVersion);
                for (Model.ConfigChange c : r.configs) {
                    StringBuilder b = new StringBuilder("CONFIG " + "~+-".charAt(c.kind) + " " + c.path + " [" + c.mod + "]");
                    for (Model.KeyChange k : c.keys) b.append(" ").append(k.key).append("=").append(k.oldValue).append("->").append(k.newValue);
                    result(b.toString());
                }
            }
        });
        if (plan.contains("visual")) SelfTestVisual.build(mc, compat, title);
        if (plan.contains("screens")) {
            add("open", null, 2, () -> {
                screen = (AbstractWhatChangedScreen) compat.createScreen(title);
                compat.setScreen(screen);
                result("INITIALTAB " + initialTab(screen));
            });
            for (int i = 0; i <= AbstractWhatChangedScreen.TAB_WORLD; i++) {
                final int tab = i;
                add("tab" + i, null, 3, () -> screen.selectTab(tab));
                add("tab" + i + "-shot", null, 6, () -> shot("tab" + tab));
            }
            add("search", null, 2, () -> {
                screen.selectTab(1);
                screen.setQuery("ruby");
            });
            add("search-shot", null, 6, () -> shot("search"));
            add("search-configs", null, 2, () -> {
                screen.selectTab(AbstractWhatChangedScreen.TAB_CONFIGS);
                screen.setQuery("ruby");
            });
            add("search-configs-shot", null, 6, () -> shot("search-configs"));
            add("notify", null, 2, () -> {
                StringBuilder b = new StringBuilder("NOTIFY start=" + Config.get().notificationMode());
                for (int i = 0; i < 3; i++) {
                    screen.cycleNotifications();
                    Config c = Config.get();
                    b.append(" -> ").append(c.notificationMode()).append("(toast=").append(c.toasts).append(",chat=")
                            .append(c.chatNotifications).append(",button=").append(c.titleScreenButton).append(")");
                }
                result(b.toString());
                screen.cycleNotifications(); // -> Quiet, for the screenshot
            });
            add("notify-shot", null, 6, () -> shot("notify-quiet"));
            add("notify-back", null, 2, () -> {
                while (Config.get().notificationMode() != Config.NOTIFY_ALL) screen.cycleNotifications();
            });
            add("export", null, 2, () -> {
                screen.setQuery("");
                result("EXPORT " + screen.exportChangelog().toAbsolutePath());
            });
            add("close", null, 2, () -> {
                compat.setScreen(title);
                result("SEEN unseen=" + PackTracker.hasUnseen());
            });
        }
        if (plan.contains("world")) {
            add("load-world", null, 5, () -> {
                try {
                    openWorld(mc, "TestWorld");
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            add("in-world", () -> mc.player != null && mc.level != null && WorldTracker.ready, 120, () -> {
                shot("world-chat");
                WorldReport w = WorldTracker.current;
                result("WORLD ready=" + WorldTracker.ready + " report=" + (w == null ? "none"
                        : WorldTracker.summary(w) + " crafted=" + w.craftedCount() + " checked=" + w.craftedChecked));
                MinecraftServer server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.awardStat(Stats.ITEM_CRAFTED.get(Items.CRAFTING_TABLE), 1);
                    }
                });
            });
            add("world-screen", null, 10, () -> {
                screen = (AbstractWhatChangedScreen) compat.createScreen(null);
                compat.setScreen(screen);
                screen.selectTab(AbstractWhatChangedScreen.TAB_WORLD);
            });
            add("world-screen-shot", null, 8, () -> shot("world-tab"));
            add("world-export", null, 2, () -> result("EXPORT " + screen.exportChangelog().toAbsolutePath()));
            add("world-close", null, 2, () -> compat.setScreen(null));
            add("world-cmd", null, 5, () -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (server != null) server.execute(() ->
                        SelfTest.command(server, "whatchanged export"));
            });
            add("world-cmd-shot", null, 20, () -> shot("world-cmd"));
        }
        add("done", null, 20, () -> {
            result("DONE");
            mc.stop();
        });
    }

    /**
     * WorldOpenFlows: loadLevel(Screen, String) up to 1.20.2, checkForBackupAndLoad(String, Runnable) in 1.20.3/1.20.4,
     * openWorld(String, Runnable) from 1.20.5. Matched by signature - names differ per loader.
     */
    private static void openWorld(Minecraft mc, String name) throws ReflectiveOperationException {
        Object flows = mc.createWorldOpenFlows();
        Runnable cancelled = () -> result("ERROR world load cancelled");
        for (java.lang.reflect.Method m : flows.getClass().getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getReturnType() != void.class || p.length != 2) continue;
            if (p[0] == String.class && p[1] == Runnable.class) {
                m.invoke(flows, name, cancelled);
                return;
            }
            if (p[0] == Screen.class && p[1] == String.class) {
                m.invoke(flows, title, name);
                return;
            }
        }
        throw new IllegalStateException("no world open method");
    }

    static void add(String name, BooleanSupplier ready, int delay, Runnable action) {
        STEPS.addLast(new Step(name, ready, delay, action));
    }

    /** The label of our title-screen button, or "none". */
    static String titleButton(ClientCompat compat) {
        Screen s = compat.currentScreen();
        if (s == null) return "none";
        String prefix = net.minecraft.network.chat.Component.translatable("whatchanged.title.button").getString();
        for (Object w : s.children()) {
            if (w instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().startsWith(prefix)) {
                return b.getMessage().getString();
            }
        }
        return "none";
    }

    static int initialTab(AbstractWhatChangedScreen s) {
        try {
            java.lang.reflect.Field f = AbstractWhatChangedScreen.class.getDeclaredField("tab");
            f.setAccessible(true);
            return f.getInt(s);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    /** Moves the real mouse (the watcher does it through XTest) to window pixel x/y and waits until it's done. */
    static void mouse(int x, int y) {
        try {
            Files.createDirectories(out());
            pendingShot = out().resolve("m" + System.nanoTime() + ".mouse");
            Files.writeString(pendingShot, x + " " + y);
            guard = 0;
        } catch (Exception e) {
            pendingShot = null;
        }
    }

    static void shot(String name) {
        String prefix = System.getenv("WCTEST_SHOTPREFIX");
        if (prefix != null) name = prefix + name;
        try {
            Files.createDirectories(out());
            pendingShot = out().resolve(name + ".req");
            Files.writeString(pendingShot, name);
            guard = 0;
        } catch (Exception e) {
            pendingShot = null;
        }
        result("SHOT " + name);
    }

}
