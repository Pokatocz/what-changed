package com.pokato.whatchanged.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.core.Config;
import com.pokato.whatchanged.core.CoreCompat;
import com.pokato.whatchanged.core.Model.PackReport;
import com.pokato.whatchanged.core.Model.WorldReport;
import com.pokato.whatchanged.core.PackTracker;
import com.pokato.whatchanged.core.WorldTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Client logic shared by all loaders. Loader entry points forward: key registration, client tick, screen init, client commands. */
public final class ClientCore {
    private static KeyMapping openKey;
    private static boolean toastShown;
    private static int titleButtonRight;
    private static volatile boolean toastPending;
    private static boolean packAnnounced;
    private static WorldReport announcedWorld;

    private ClientCore() {}

    /** Create (once) the "open What Changed?" key - default J. The loader registers it. */
    public static synchronized KeyMapping openKey() {
        if (openKey == null) {
            Config.get();
            openKey = KeyCompat.create("key.whatchanged.open", "key.keyboard.j", WhatChangedCommon.MOD_ID, "general");
        }
        return openKey;
    }

    public static void openScreen() {
        ClientCompat compat = ClientCompat.get();
        compat.setScreen(compat.createScreen(compat.currentScreen()));
    }

    /** Called after any screen's init. addWidget adds a widget to that screen. */
    public static void onScreenInit(Screen screen, Consumer<AbstractWidget> addWidget) {
        if (!(screen instanceof TitleScreen)) return;
        PackTracker.checkOnce();
        Minecraft mc = Minecraft.getInstance();
        Config cfg = Config.get();
        PackReport r = PackTracker.report();
        boolean unseen = PackTracker.hasUnseen();
        titleButtonRight = 0;

        if (cfg.titleScreenButton && unseen) {
            MutableComponent label = Component.translatable("whatchanged.title.button");
            int[] n = counts(r);
            String[] signs = {" +", " -", " ↑", " ↓", " ⚙"};
            ChatFormatting[] colors = {ChatFormatting.GREEN, ChatFormatting.RED, ChatFormatting.YELLOW, ChatFormatting.GOLD, ChatFormatting.AQUA};
            for (int i = 0; i < n.length; i++) {
                if (n[i] > 0) label.append(Component.literal(signs[i] + n[i]).withStyle(colors[i]));
            }
            int width = Math.max(110, width(mc, label.getString()) + 16);
            titleButtonRight = cfg.titleButtonY < 36 ? cfg.titleButtonX + width : 0; // only a button up in the toast's row matters
            addWidget.accept(Button.builder(label, b -> {
                ClientCompat compat = ClientCompat.get();
                compat.setScreen(compat.createScreen(screen));
            }).bounds(cfg.titleButtonX, cfg.titleButtonY, width, 20).build());
        }

        if (!toastShown && cfg.toasts) toastPending = true; // shown from the client tick once loading is over
    }

    /**
     * The title screen is first built while the loading overlay still covers it, before fonts are loaded - a toast
     * made then measures its text as nothing wide and the text runs out of the box. So wait for the overlay to go.
     */
    private static void showPendingToast(Minecraft mc) {
        if (!toastPending || toastShown) return;
        ClientCompat compat = ClientCompat.get();
        if (!(compat.currentScreen() instanceof TitleScreen)) return;
        try {
            if (compat.loadingOverlay()) return;
        } catch (Throwable ignored) {
        }
        toastPending = false;
        toastShown = true;
        try {
            if (PackTracker.hasUnseen()) {
                Component body = toastBody(mc, PackTracker.report());
                compat.toast(Component.translatable("whatchanged.toast.changed.title"), body);
                selfTest("toast", body.getString());
            } else if (PackTracker.isFirstRun()) {
                compat.toast(Component.translatable("whatchanged.toast.first.title"),
                        Component.translatable("whatchanged.toast.first.body"));
                selfTest("toast", "first run");
            }
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.debug("[What Changed?] Toast failed: {}", t.toString());
        }
    }

    /** End of every client tick. */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping key = openKey;
        if (key != null) {
            while (key.consumeClick()) {
                if (ClientCompat.get().currentScreen() == null) openScreen();
            }
        }
        showPendingToast(mc);
        if (mc.player != null && mc.level != null) {
            PackTracker.checkOnce(); // joined a server directly (quick play) without passing the title screen
            announce(mc);
        }
        selfTest("clientTick", mc);
    }

    /**
     * Client command tree: /whatchanged opens the screen, /whatchanged reset forgets the modpack baseline.
     * The loader supplies its own literal() and a way to send feedback for its command source type.
     */
    public static <S> LiteralArgumentBuilder<S> clientCommand(Function<String, LiteralArgumentBuilder<S>> literal,
                                                              BiConsumer<S, Component> feedback) {
        return literal.apply("whatchanged")
                .executes(ctx -> {
                    Minecraft.getInstance().execute(ClientCore::openScreen);
                    return 1;
                })
                .then(literal.apply("reset").executes(ctx -> {
                    PackTracker.reset();
                    feedback.accept(ctx.getSource(), prefix().append(Component.translatable("whatchanged.cmd.reset_pack")));
                    return 1;
                }));
    }

    /** Short chat notices once per game session (pack) and once per world report (singleplayer host). */
    private static void announce(Minecraft mc) {
        if (!Config.get().chatNotifications) return;
        if (!packAnnounced) {
            packAnnounced = true;
            if (PackTracker.hasUnseen()) {
                PackReport r = PackTracker.report();
                chat(Component.translatable("whatchanged.chat.pack", summary(r, true)).withStyle(ChatFormatting.GRAY));
            }
        }
        WorldReport w = WorldTracker.current;
        if (mc.hasSingleplayerServer() && WorldTracker.ready && w != null && w != announcedWorld
                && !w.isEmpty() && !w.seen && w.craftedChecked) {
            announcedWorld = w;
            MutableComponent msg = Component.translatable("whatchanged.chat.world", WorldTracker.date(w.previous),
                    Component.literal(WorldTracker.summary(w)).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY);
            int crafted = w.craftedCount();
            if (crafted > 0) {
                msg.append(Component.translatable(crafted == 1 ? "whatchanged.chat.crafted.one" : "whatchanged.chat.crafted.many",
                        crafted).withStyle(ChatFormatting.YELLOW));
            }
            chat(msg);
        }
    }

    private static void chat(Component body) {
        selfTest("chat", body.getString());
        ClientCompat.get().addChatMessage(prefix()
                .append(body)
                .append(Component.literal(" "))
                .append(CoreCompat.get().withRunCommand(
                        Component.translatable("whatchanged.chat.details", keyName()).withStyle(ChatFormatting.AQUA), "/whatchanged")));
    }

    /** "+3 new, -1 removed, 2 mods updated, Forge updated to 47.4.0, 4 config files changed" - only the parts that apply. */
    public static MutableComponent summary(PackReport r, boolean colored) {
        java.util.List<MutableComponent> parts = new java.util.ArrayList<>();
        if (r.mcOld != null && r.mcNew != null && !r.mcOld.equals(r.mcNew)) {
            boolean down = com.pokato.whatchanged.core.VersionCompare.isDowngrade(r.mcOld, r.mcNew);
            parts.add(part(colored, down ? ChatFormatting.GOLD : ChatFormatting.YELLOW,
                    down ? "whatchanged.summary.minecraft.downgraded" : "whatchanged.summary.minecraft.updated", r.mcNew));
        }
        if (r.added() > 0) parts.add(part(colored, ChatFormatting.GREEN, "whatchanged.summary.added", r.added()));
        if (r.removed() > 0) parts.add(part(colored, ChatFormatting.RED, "whatchanged.summary.removed", r.removed()));
        if (!r.modsUpdated.isEmpty()) parts.add(part(colored, ChatFormatting.YELLOW,
                r.modsUpdated.size() == 1 ? "whatchanged.summary.updated.one" : "whatchanged.summary.updated", r.modsUpdated.size()));
        if (!r.modsDowngraded.isEmpty()) parts.add(part(colored, ChatFormatting.GOLD,
                r.modsDowngraded.size() == 1 ? "whatchanged.summary.downgraded.one" : "whatchanged.summary.downgraded", r.modsDowngraded.size()));
        if (r.loaderChanged()) {
            boolean down = r.loaderDowngraded();
            parts.add(part(colored, down ? ChatFormatting.GOLD : ChatFormatting.YELLOW,
                    r.loaderSwitched() ? "whatchanged.summary.loader.switched"
                            : down ? "whatchanged.summary.loader.downgraded" : "whatchanged.summary.loader.updated",
                    com.pokato.whatchanged.core.Loaders.displayName(r.loaderName), r.loaderNew));
        }
        if (!r.configs.isEmpty()) parts.add(part(colored, ChatFormatting.AQUA,
                r.configs.size() == 1 ? "whatchanged.summary.configs.one" : "whatchanged.summary.configs", r.configs.size()));
        MutableComponent out = Component.literal("");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(Component.literal(", "));
            out.append(parts.get(i));
        }
        return out;
    }

    /**
     * The toast grows with its text and is anchored to the right edge, so a long line would run off the screen:
     * full sentence when it fits, then "summary · press J", then the symbols of the title button ("+6 -5 ↑2 ↓1 ⚙5").
     */
    private static Component toastBody(Minecraft mc, PackReport r) {
        // the toast sits in the top-right corner: keep it clear of our title button and inside a small window
        int screenW = mc.getWindow().getGuiScaledWidth();
        final int max = Math.max(110, Math.min(280, screenW - 36 - (titleButtonRight > 0 ? titleButtonRight + 4 : 0)));
        Component full = Component.translatable("whatchanged.toast.changed.body", summary(r, false), keyName());
        if (width(mc, full.getString()) <= max) return full;
        Component shorter = Component.translatable("whatchanged.toast.changed.body.short", summary(r, false), keyName());
        if (width(mc, shorter.getString()) <= max) return shorter;
        Component summaryOnly = summary(r, false);
        if (width(mc, summaryOnly.getString()) <= max) return summaryOnly;
        return Component.translatable("whatchanged.toast.changed.body.short", compact(r), keyName());
    }

    /** Text width, never trusting a font that measures less than 5 px per character (not loaded yet). */
    private static int width(Minecraft mc, String text) {
        return Math.max(mc.font.width(text), text.length() * 5);
    }

    /** added, removed, updated, downgraded, config files - the loader counts as an updated/downgraded mod here. */
    private static int[] counts(PackReport r) {
        boolean loaderDown = r.loaderDowngraded();
        return new int[]{r.added(), r.removed(), r.modsUpdated.size() + (r.loaderChanged() && !loaderDown ? 1 : 0),
                r.modsDowngraded.size() + (loaderDown ? 1 : 0), r.configs.size()};
    }

    /** "MC 1.21.8 +6 -5 ↑2 ↓1 ⚙5" (the symbols of the title-screen button, plus a new Minecraft version). */
    public static String compact(PackReport r) {
        int[] n = counts(r);
        String[] signs = {"+", "-", "↑", "↓", "⚙"};
        StringBuilder b = new StringBuilder();
        if (r.mcOld != null && r.mcNew != null && !r.mcOld.equals(r.mcNew)) b.append(" MC ").append(r.mcNew);
        for (int i = 0; i < n.length; i++) if (n[i] > 0) b.append(' ').append(signs[i]).append(n[i]);
        return b.toString().trim();
    }

    private static MutableComponent part(boolean colored, ChatFormatting color, String key, Object... args) {
        MutableComponent c = Component.translatable(key, args);
        return colored ? c.withStyle(color) : c;
    }

    private static MutableComponent prefix() {
        return Component.translatable("whatchanged.chat.prefix").withStyle(ChatFormatting.GOLD);
    }

    /** The key that opens the screen, as the player sees it ("J" unless rebound). */
    public static Component keyName() {
        KeyMapping key = openKey;
        return key != null ? key.getTranslatedKeyMessage() : Component.literal("J");
    }

    /** Dev-only hook: the self-test class only exists in test builds. */
    private static void selfTest(String event, Object arg) {
        if (!SelfTestHook.present) return;
        try {
            SelfTestHook.method.invoke(null, event, arg);
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] self-test hook failed: {}", t.toString());
        }
    }

    private static final class SelfTestHook {
        static final java.lang.reflect.Method method;
        static final boolean present;

        static {
            java.lang.reflect.Method m = null;
            try {
                Class<?> c = Class.forName("com.pokato.whatchanged.selftest.SelfTest", true, ClientCore.class.getClassLoader());
                m = c.getMethod("onEvent", String.class, Object.class);
            } catch (Throwable ignored) {
            }
            method = m;
            present = m != null;
        }
    }
}
