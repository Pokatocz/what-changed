package com.pokato.whatchanged.core;

import com.pokato.whatchanged.platform.Platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** config/whatchanged/config.json */
public final class Config {
    /** Show the "What changed?" button on the title screen after an update. */
    public boolean titleScreenButton = true;
    /** Where that button sits (pixels from the top-left corner). */
    public int titleButtonX = 4;
    public int titleButtonY = 4;
    /** Post a short summary in chat when you join a world after a change (also used by servers). */
    public boolean chatNotifications = true;
    /** Show a toast on the title screen after an update. */
    public boolean toasts = true;
    /** Count mods whose only change is a new version number. */
    public boolean trackModUpdates = true;
    /** Don't list new recipes whose output is itself a new item (the item is already listed). */
    public boolean hideRecipesForNewItems = true;
    /** Remember the files in config/ and show which were added, removed or changed (and which values). */
    public boolean trackConfigs = true;
    /** Also notify (toast, chat, title button) when nothing but config files changed. Off because many mods rewrite
     *  their own configs while you play; modpack authors who ship config-only updates can turn it on. */
    public boolean notifyConfigChanges = false;
    /** config/ files to leave out (globs relative to config/: * = any name, ** = any folders). */
    public List<String> configIgnore = new ArrayList<>(List.of("**/*.bak", "**/*.old", "**/*.tmp", "**/*.lock",
            "**/*.log", "**/backups/**", "**/backup/**", "jei/world/**", "jei/bookmarks.ini", "**/*cache*", "**/*history*"));
    /** Values whose key contains one of these (case-insensitive) are not reported - mods that store timestamps. */
    public List<String> configIgnoreKeys = new ArrayList<>(List.of("timestamp", "lastcheck", "last_check", "lastupdate",
            "last_update", "lastlaunch", "last_launch", "lastplayed", "last_played", "lastseen", "last_seen", "lastrun"));

    private static Config instance;

    public static Path dir() {
        return Platform.get().configDir().resolve("whatchanged");
    }

    /** Notification presets for the button in the What Changed? screen. */
    public static final int NOTIFY_ALL = 0, NOTIFY_QUIET = 1, NOTIFY_OFF = 2, NOTIFY_CUSTOM = 3;

    /** All = toast + chat + title button; Quiet = title button only; Off = nothing (J / command still work). */
    public int notificationMode() {
        if (toasts && chatNotifications && titleScreenButton) return NOTIFY_ALL;
        if (!toasts && !chatNotifications && titleScreenButton) return NOTIFY_QUIET;
        if (!toasts && !chatNotifications && !titleScreenButton) return NOTIFY_OFF;
        return NOTIFY_CUSTOM;
    }

    public void setNotificationMode(int mode) {
        toasts = mode == NOTIFY_ALL;
        chatNotifications = mode == NOTIFY_ALL;
        titleScreenButton = mode != NOTIFY_OFF;
        save();
    }

    public void save() {
        Json.writeAsync(dir().resolve("config.json"), this, true);
    }

    public static synchronized Config get() {
        if (instance == null) {
            Path p = dir().resolve("config.json");
            instance = Json.read(p, Config.class);
            if (instance == null) instance = new Config();
            Json.write(p, instance, true); // write defaults / new keys
        }
        return instance;
    }
}
