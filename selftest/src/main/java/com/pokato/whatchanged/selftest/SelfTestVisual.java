package com.pokato.whatchanged.selftest;

import com.pokato.whatchanged.client.AbstractWhatChangedScreen;
import com.pokato.whatchanged.client.ClientCompat;
import com.pokato.whatchanged.client.ClientCore;
import com.pokato.whatchanged.core.Config;
import com.pokato.whatchanged.core.Model.ConfigChange;
import com.pokato.whatchanged.core.Model.KeyChange;
import com.pokato.whatchanged.core.Model.ModChange;
import com.pokato.whatchanged.core.Model.Named;
import com.pokato.whatchanged.core.Model.PackReport;
import com.pokato.whatchanged.core.Names;
import com.pokato.whatchanged.core.PackTracker;
import com.pokato.whatchanged.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.pokato.whatchanged.selftest.SelfTest.result;
import static com.pokato.whatchanged.selftest.SelfTestClient.add;
import static com.pokato.whatchanged.selftest.SelfTestClient.mouse;
import static com.pokato.whatchanged.selftest.SelfTestClient.shot;

/**
 * DEV-ONLY visual test: feeds the screen, title button and toasts with synthetic extreme reports
 * (very long names and values, hundreds of rows, unicode, § codes, multi-line values) and takes screenshots,
 * including tooltips (the mouse is moved for real through the X server).
 */
final class SelfTestVisual {
    private static AbstractWhatChangedScreen screen;

    private SelfTestVisual() {}

    static void build(Minecraft mc, ClientCompat compat, Screen title) {
        // --- title screen button + toasts
        add("v-set", null, 2, () -> {
            setReport(synthetic(true));
            compat.setScreen(title); // re-init: our hook adds the title button
        });
        add("v-title", null, 10, () -> {
            result("V TITLEBUTTON " + SelfTestClient.titleButton(compat));
            compat.clearToasts();
            compat.toast(Component.translatable("whatchanged.toast.changed.title"), toastBody(mc, PackTracker.report()));
        });
        add("v-toast-long-shot", null, 25, () -> shot("v-toast-long"));
        add("v-toast-short", null, 2, () -> {
            compat.clearToasts();
            PackReport small = new PackReport();
            small.modsUpdated.add(new ModChange("a", "A", "1", "2", null));
            small.itemsAdded.add(new Named("minecraft:stone", "Stone", "Minecraft"));
            compat.toast(Component.translatable("whatchanged.toast.changed.title"), toastBody(mc, small));
        });
        add("v-toast-short-shot", null, 25, () -> shot("v-toast-short"));
        add("v-toast-first", null, 2, () -> {
            compat.clearToasts();
            compat.toast(Component.translatable("whatchanged.toast.first.title"), Component.translatable("whatchanged.toast.first.body"));
        });
        add("v-toast-first-shot", null, 25, () -> shot("v-toast-first"));

        // --- the screen with the big report
        add("v-open", null, 2, () -> {
            compat.clearToasts();
            open(compat, title);
            result("V INITIALTAB " + SelfTestClient.initialTab(screen));
        });
        for (int i = 0; i <= AbstractWhatChangedScreen.TAB_WORLD; i++) {
            final int tab = i;
            add("v-tab" + i, null, 3, () -> screen.selectTab(tab));
            add("v-tab" + i + "-shot", null, 6, () -> shot("v-tab" + tab));
        }
        add("v-mods-bottom", null, 2, () -> {
            screen.selectTab(AbstractWhatChangedScreen.TAB_MODS);
            scrollBy(30);
        });
        add("v-mods-bottom-shot", null, 6, () -> shot("v-mods-bottom"));
        add("v-configs-mid", null, 2, () -> {
            screen.selectTab(AbstractWhatChangedScreen.TAB_CONFIGS);
            scrollBy(4);
        });
        add("v-configs-mid-shot", null, 6, () -> shot("v-configs-mid"));
        add("v-configs-bottom", null, 2, () -> scrollBy(10000));
        add("v-configs-bottom-shot", null, 6, () -> shot("v-configs-bottom"));
        // search for a value name only: its file line must still be shown
        add("v-search-key", null, 2, () -> screen.setQuery("spawnRate"));
        add("v-search-key-shot", null, 6, () -> shot("v-search-key"));
        add("v-search-file", null, 2, () -> screen.setQuery("rendering-and"));
        add("v-search-file-shot", null, 6, () -> shot("v-search-file"));
        // tooltips: header, subheader, file line, value line -> row 3 is the long value
        add("v-search-long", null, 2, () -> screen.setQuery("longvalue"));
        add("v-hover-long", null, 4, () -> hoverRow(mc, 3));
        add("v-hover-long-shot", null, 10, () -> shot("v-tooltip-long"));
        add("v-hover-unicode", null, 2, () -> screen.setQuery("unicode"));
        add("v-hover-unicode-move", null, 4, () -> hoverRow(mc, 3));
        add("v-hover-unicode-shot", null, 10, () -> shot("v-tooltip-unicode"));
        add("v-hover-loader", null, 2, () -> {
            screen.setQuery("");
            screen.selectTab(AbstractWhatChangedScreen.TAB_MODS);
        });
        add("v-hover-loader-move", null, 4, () -> hoverRow(mc, 1));
        add("v-hover-loader-shot", null, 10, () -> shot("v-tooltip-loader"));
        add("v-hover-mod", null, 4, () -> hoverRow(mc, 4)); // first added mod: very long name + url
        add("v-hover-mod-shot", null, 10, () -> shot("v-tooltip-mod"));
        add("v-hover-notify", null, 4, () -> hoverNotify(mc, 0));
        add("v-hover-notify2", null, 4, () -> hoverNotify(mc, 2));  // a real user moves the mouse more than once
        add("v-hover-notify3", null, 4, () -> hoverNotify(mc, 4));
        add("v-hover-notify-shot", null, 30, () -> shot("v-tooltip-notify"));
        add("v-mouse-away", null, 2, () -> hoverAt(mc, 2, 2));

        // --- notification button states
        add("v-notify-quiet", null, 4, () -> screen.cycleNotifications());
        add("v-notify-quiet-shot", null, 6, () -> shot("v-notify-quiet"));
        add("v-notify-off", null, 2, () -> screen.cycleNotifications());
        add("v-notify-off-shot", null, 6, () -> shot("v-notify-off"));
        add("v-notify-custom", null, 2, () -> {
            Config c = Config.get();
            c.toasts = false;
            c.chatNotifications = true;
            c.titleScreenButton = true;
            open(compat, title);
        });
        add("v-notify-custom-shot", null, 6, () -> {
            shot("v-notify-custom");
            result("V NOTIFYMODE custom=" + Config.get().notificationMode());
        });
        add("v-notify-all", null, 2, () -> Config.get().setNotificationMode(Config.NOTIFY_ALL));
        add("v-export", null, 2, () -> result("V EXPORT " + screen.exportChangelog().toAbsolutePath()));

        // --- empty states
        add("v-empty", null, 2, () -> {
            setReport(null);
            open(compat, title);
        });
        add("v-empty-mods-shot", null, 6, () -> shot("v-empty-mods"));
        add("v-empty-configs", null, 2, () -> screen.selectTab(AbstractWhatChangedScreen.TAB_CONFIGS));
        add("v-empty-configs-shot", null, 6, () -> shot("v-empty-configs"));
        add("v-nochange", null, 2, () -> {
            PackReport r = new PackReport();
            r.modsAdded.add(new ModChange("x", "Only A Mod", null, "1.0", null));
            r.detected = System.currentTimeMillis();
            r.previous = r.detected - 86400000L;
            setReport(r);
            open(compat, title);
            screen.selectTab(AbstractWhatChangedScreen.TAB_CONFIGS);
        });
        add("v-noconfigs-shot", null, 6, () -> shot("v-no-config-changes"));
        add("v-configs-off", null, 2, () -> {
            Config.get().trackConfigs = false;
            screen.selectTab(AbstractWhatChangedScreen.TAB_CONFIGS);
        });
        add("v-configs-off-shot", null, 6, () -> {
            shot("v-configs-off");
            Config.get().trackConfigs = true;
        });

        // --- config-only report: opens on the Configs tab, no title button / toast
        add("v-configonly", null, 2, () -> {
            PackReport r = synthetic(false);
            setReport(r);
            compat.setScreen(title);
        });
        add("v-configonly-title", null, 6, () -> {
            result("V CONFIGONLY unseen=" + PackTracker.hasUnseen() + " titlebutton=" + SelfTestClient.titleButton(compat));
            open(compat, title);
            result("V CONFIGONLY INITIALTAB " + SelfTestClient.initialTab(screen));
        });
        add("v-configonly-shot", null, 6, () -> shot("v-configonly"));

        // --- loader switch + Minecraft downgrade
        add("v-switch", null, 2, () -> {
            PackReport r = new PackReport();
            r.detected = System.currentTimeMillis();
            r.previous = r.detected - 86400000L;
            r.mcOld = "99.1";
            r.mcNew = Platform.get().minecraftVersion();
            r.loaderOldName = "Forge";
            r.loaderName = Platform.get().loaderName().equals("Forge") ? "NeoForge" : Platform.get().loaderName();
            r.loaderOld = "47.2.0";
            r.loaderNew = "47.1.106";
            setReport(r);
            open(compat, title);
            result("V SWITCH summary=" + ClientCore.summary(r, false).getString() + " | compact=" + ClientCore.compact(r));
        });
        add("v-switch-shot", null, 6, () -> shot("v-loader-switch"));
        add("v-close", null, 2, () -> compat.setScreen(title));
    }

    // ------------------------------------------------------------------ helpers

    private static void open(ClientCompat compat, Screen title) {
        screen = (AbstractWhatChangedScreen) compat.createScreen(title);
        compat.setScreen(screen);
    }

    private static void scrollBy(int rows) {
        screen.setQuery(""); // keeps the tab, resets scroll
        for (int i = 0; i < rows; i++) callScroll(-1.0 / 3.0);
    }

    private static void callScroll(double amount) {
        try {
            Method m = AbstractWhatChangedScreen.class.getDeclaredMethod("scrollList", double.class);
            m.setAccessible(true);
            m.invoke(screen, amount);
        } catch (ReflectiveOperationException e) {
            result("ERROR scroll " + e);
        }
    }

    /** Mouse over list row i (0 = first visible row), at the same geometry the screen uses. */
    private static void hoverRow(Minecraft mc, int row) {
        int w = mc.getWindow().getGuiScaledWidth();
        int listW = Math.min(440, w - 30);
        int listX = (w - listW) / 2;
        hoverAt(mc, listX + 80, 86 + row * 18 + 9);
    }

    private static void hoverNotify(Minecraft mc, int dx) {
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int listW = Math.min(440, w - 30);
        int bw = Math.min(140, (listW - 8) / 3);
        int bx = w / 2 - (bw * 3 + 8) / 2;
        hoverAt(mc, bx + bw / 2 + dx, h - 16);
    }

    private static void hoverAt(Minecraft mc, int guiX, int guiY) {
        double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        result("V MOUSE gui " + guiX + "," + guiY + " -> px " + Math.round(guiX * scale) + "," + Math.round(guiY * scale)
                + " (screen " + mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight() + ", gui "
                + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight() + ")");
        mouse((int) Math.round(guiX * scale), (int) Math.round(guiY * scale));
    }

    private static Component toastBody(Minecraft mc, PackReport r) {
        try {
            Method m = ClientCore.class.getDeclaredMethod("toastBody", Minecraft.class, PackReport.class);
            m.setAccessible(true);
            return (Component) m.invoke(null, mc, r);
        } catch (ReflectiveOperationException e) {
            result("ERROR toastBody " + e);
            return Component.literal("?");
        }
    }

    private static void setReport(PackReport r) {
        try {
            Field f = PackTracker.class.getDeclaredField("report");
            f.setAccessible(true);
            f.set(null, r);
        } catch (ReflectiveOperationException e) {
            result("ERROR setReport " + e);
        }
    }

    /** big = everything at once; otherwise only config changes. */
    static PackReport synthetic(boolean big) {
        PackReport r = new PackReport();
        long now = System.currentTimeMillis();
        r.detected = now;
        r.previous = now - 3 * 86400000L;
        String longName = "An Extremely Long Mod Name That Keeps Going And Going For Layout Testing";
        if (big) {
            r.mcOld = "1.19.4";
            r.mcNew = Platform.get().minecraftVersion();
            r.loaderName = Platform.get().loaderName();
            r.loaderOld = "99.9.9";
            r.loaderNew = "1.0.0";
            r.modsAdded.add(new ModChange("verylong", longName, null, "1.2.3-beta.4+build.567-mc1.21.8-neoforge-extra-long-suffix",
                    "https://example.com/very/long/url/that/goes/on/and/on/for/testing/the/tooltip/wrapping/behaviour/of/the/screen"));
            r.modsAdded.add(new ModChange("fancy", "§6Fancy §lColored§r Mod §kOBF", null, "2.0", null));
            r.modsAdded.add(new ModChange("unicode", "Příliš žluťoučký kůň ⚙ 日本語", null, "3.0", null));
            for (int i = 0; i < 25; i++) r.modsUpdated.add(new ModChange("mod" + i, "Updated Mod " + i, "1." + i + ".0", "1." + (i + 1) + ".0", null));
            for (int i = 0; i < 3; i++) r.modsDowngraded.add(new ModChange("down" + i, "Downgraded Mod " + i, "5.0." + i, "4.9." + i, null));
            for (int i = 0; i < 4; i++) r.modsRemoved.add(new ModChange("gone" + i, "Removed Mod " + i, "0." + i, null, null));
            int n = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                String id = Names.itemId(item);
                r.itemsAdded.add(new Named(id, Names.itemName(item, id), "Minecraft"));
                if (++n >= 40) break;
            }
            for (int i = 0; i < 5; i++) r.itemsRemoved.add(new Named("oldmod:ruby_thing_" + i, "Ruby Thing " + i, "Old Ruby Mod"));
            for (int i = 0; i < 6; i++) r.entitiesAdded.add(new Named("newmod:mob_" + i, "New Mob " + i, longName));
        }
        ConfigChange c1 = new ConfigChange("averyveryverylongmodname/client/subfolder/rendering-and-performance-configuration-file.toml",
                longName, ConfigChange.CHANGED);
        c1.keys.add(new KeyChange("general.spawnRate", "12", "20"));
        c1.keys.add(new KeyChange("general.longvalue", "The quick brown fox jumps over the lazy dog ".repeat(4).trim(), "short"));
        c1.keys.add(new KeyChange("general.unicode", "Příliš žluťoučký kůň úpěl ďábelské ódy ⚙ ✓ 日本語", "nové"));
        c1.keys.add(new KeyChange("general.formatted", "&6[Server] (was a § code)".replace("&", "§"), "§c[Server]"));
        c1.keys.add(new KeyChange("general.removedKey", "true", null));
        c1.keys.add(new KeyChange("general.newKey", null, "false"));
        c1.keys.add(new KeyChange("general.empty", "", "x"));
        c1.keys.add(new KeyChange("general.multiline", "Line one ⏎ Line two", "Line one ⏎ Line 2"));
        c1.keys.add(new KeyChange("a.really.deeply.nested.key.path.that.is.much.longer.than.the.screen.is.wide.for.testing", "1", "2"));
        ConfigChange c2 = new ConfigChange("bigmod-common.toml", "Big Config Mod", ConfigChange.CHANGED);
        for (int i = 0; i < 100; i++) c2.keys.add(new KeyChange("values.option" + i, String.valueOf(i), String.valueOf(i + 1)));
        c2.moreKeys = 50;
        ConfigChange c3 = new ConfigChange("weird-format.hocon", "Other", ConfigChange.CHANGED);
        ConfigChange a1 = new ConfigChange("newmod-client.toml", "New Mod", ConfigChange.ADDED);
        ConfigChange a2 = new ConfigChange("newmod/sub/thing.json5", "New Mod", ConfigChange.ADDED);
        ConfigChange d1 = new ConfigChange("oldmod-legacy.properties", "Old Ruby Mod", ConfigChange.REMOVED);
        r.configs.add(c1);
        r.configs.add(c2);
        r.configs.add(a1);
        r.configs.add(a2);
        r.configs.add(c3);
        r.configs.add(d1);
        r.configs.sort(java.util.Comparator.comparing((ConfigChange c) -> c.mod.toLowerCase()).thenComparing(c -> c.path));
        return r;
    }
}
