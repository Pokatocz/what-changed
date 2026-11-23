package com.pokato.whatchanged.core;

import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.core.Model.ModChange;
import com.pokato.whatchanged.core.Model.ModInfo;
import com.pokato.whatchanged.core.Model.Named;
import com.pokato.whatchanged.core.Model.PackReport;
import com.pokato.whatchanged.core.Model.PackSnapshot;
import com.pokato.whatchanged.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Map.Entry;

/**
 * Remembers the installed mods, items and mobs and builds a report of what changed.
 * The stored snapshot is a baseline: it only advances once the player has looked at the
 * report, so several updates in a row add up instead of overwriting each other.
 * Runs once per game start (client side), after resources are loaded.
 */
public final class PackTracker {
    private static final Set<String> IGNORED_MODS = Set.of("java", "mixinextras", "minecraft");

    private static boolean checked;
    private static boolean firstRun;
    private static PackSnapshot current;
    private static PackReport report;
    private static long baselineTime;

    private PackTracker() {}

    private static Path snapshotPath() {
        return Config.dir().resolve("pack-snapshot.json");
    }

    private static Path reportPath() {
        return Config.dir().resolve("pack-report.json");
    }

    /** Safe to call many times; only the first call does work. */
    public static synchronized void checkOnce() {
        if (checked) return;
        checked = true;
        long start = System.currentTimeMillis();
        try {
            current = capture();
            PackSnapshot baseline = Json.read(snapshotPath(), PackSnapshot.class);
            if (baseline != null && baseline.format != Model.FORMAT) baseline = null; // older layout: start over
            if (baseline != null && baseline.configs == null && current.configs != null) {
                // snapshot from before config tracking (or tracking just switched on): configs start from now
                baseline.configs = current.configs;
                Json.writeAsync(snapshotPath(), baseline, false);
            }
            PackReport existing = Json.read(reportPath(), PackReport.class);
            String outcome;
            if (baseline == null) {
                firstRun = true;
                baselineTime = current.time;
                report = null;
                Json.delete(reportPath());
                Json.writeAsync(snapshotPath(), current, false);
                outcome = "first run, snapshot saved";
            } else {
                baselineTime = baseline.time;
                PackReport diff = diff(baseline, current, Config.get().trackModUpdates);
                if (diff.isEmpty()) {
                    // Nothing differs from the baseline: keep an already-seen report for reference, drop a stale one.
                    report = existing != null && existing.seen ? existing : null;
                    if (report == null && existing != null) Json.delete(reportPath());
                    outcome = "no changes";
                } else if (existing != null && !existing.seen && diff.sameChangesAs(existing)) {
                    report = existing; // keep the original detection time
                    outcome = "unchanged report: " + describe(report);
                } else {
                    report = diff;
                    Json.writeAsync(reportPath(), report, true);
                    outcome = describe(report);
                }
            }
            WhatChangedCommon.LOGGER.info("[What Changed?] Modpack check done in {} ms ({} mods, {} items): {}",
                    System.currentTimeMillis() - start, current.mods.size(), current.items.size(), outcome);
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.error("[What Changed?] Modpack check failed", t);
        }
    }

    private static String describe(PackReport r) {
        return "+" + r.added() + " / -" + r.removed() + " / " + r.modsUpdated.size() + " updated / "
                + r.modsDowngraded.size() + " downgraded / " + r.configs.size() + " configs"
                + (r.loaderChanged() ? " / loader " + (r.loaderSwitched() ? r.loaderOldName + " " : "") + r.loaderOld
                + " -> " + (r.loaderSwitched() ? r.loaderName + " " : "") + r.loaderNew : "");
    }

    public static PackReport report() {
        return report;
    }

    public static boolean isFirstRun() {
        return firstRun;
    }

    /** When the baseline we compared against was taken (or now, on the first run). */
    public static long baselineTime() {
        return baselineTime;
    }

    /** The player has looked at the report: advance the baseline to the current state. */
    public static synchronized void markSeen() {
        if (report != null && !report.seen) {
            report.seen = true;
            Json.writeAsync(reportPath(), report, true);
            if (current != null) {
                Json.writeAsync(snapshotPath(), current, false);
                baselineTime = current.time;
            }
        }
    }

    /** Forget everything and start from the current state. */
    public static synchronized void reset() {
        report = null;
        firstRun = true;
        Json.delete(reportPath());
        if (current == null) current = capture();
        baselineTime = current.time;
        Json.writeAsync(snapshotPath(), current, false);
    }

    /**
     * True when there is a report the player should be told about (toast, chat, title button). Changes that are only
     * in config files stay quiet unless notifyConfigChanges is on - many mods rewrite their configs while you play.
     */
    public static boolean hasUnseen() {
        if (report == null || report.seen || report.isEmpty()) return false;
        return !report.onlyConfigs() || Config.get().notifyConfigChanges;
    }

    public static PackSnapshot capture() {
        PackSnapshot s = new PackSnapshot();
        s.time = System.currentTimeMillis();
        Platform platform = Platform.get();
        s.minecraft = platform.minecraftVersion();
        s.loader = platform.loaderName();
        if (Config.get().trackConfigs) {
            List<String> ignore = new java.util.ArrayList<>();
            if (Config.get().configIgnore != null) ignore.addAll(Config.get().configIgnore);
            ignore.add("whatchanged/**"); // our own files
            s.configs = ConfigTracker.capture(platform.configDir(), ignore);
        }
        for (Platform.ModEntry m : platform.mods()) {
            if (IGNORED_MODS.contains(m.id())) continue;
            s.mods.put(m.id(), new ModInfo(m.name(), m.version(), m.url()));
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String id = Names.itemId(item);
            s.items.put(id, Names.itemName(item, id));
        }
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String id = Names.entityId(type);
            s.entities.put(id, Names.entityName(type, id));
        }
        return s;
    }

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    public static Model.PackReport diff(Model.PackSnapshot prev, Model.PackSnapshot cur, boolean trackUpdates) {
        Model.PackReport r = new Model.PackReport();
        r.detected = cur.time;
        r.previous = prev.time;
        r.mcOld = prev.minecraft;
        r.mcNew = cur.minecraft;
        String curLoader = cur.loader != null ? cur.loader : prev.loader;
        String prevLoader = prev.loader != null ? prev.loader : curLoader;
        String curId = Loaders.findIn(curLoader, cur.mods);
        String prevId = Loaders.findIn(prevLoader, prev.mods);
        Set<String> loaderIds = new HashSet<>();
        if (curId != null && prevId != null) {
            loaderIds.add(curId);
            loaderIds.add(prevId);
            String o = prev.mods.get(prevId).version;
            String n = cur.mods.get(curId).version;
            boolean switched = !Objects.equals(prevLoader, curLoader);
            if (o != null && n != null && (switched || !o.equals(n))) {
                r.loaderName = curLoader;
                r.loaderOldName = switched ? prevLoader : null;
                r.loaderOld = o;
                r.loaderNew = n;
            }
        }

        for (Entry<String, Model.ModInfo> e : cur.mods.entrySet()) {
            if (!loaderIds.contains(e.getKey())) {
                Model.ModInfo old = prev.mods.get(e.getKey());
                Model.ModInfo now = e.getValue();
                if (old == null) {
                    r.modsAdded.add(new Model.ModChange(e.getKey(), now.name, null, now.version, now.url));
                } else if (trackUpdates && !safe(old.version).equals(safe(now.version))) {
                    Model.ModChange c = new Model.ModChange(e.getKey(), now.name, old.version, now.version, now.url);
                    if (VersionCompare.isModDowngrade(old.version, now.version, prev.minecraft, cur.minecraft)) {
                        r.modsDowngraded.add(c);
                    } else {
                        r.modsUpdated.add(c);
                    }
                }
            }
        }

        for (Entry<String, Model.ModInfo> ex : prev.mods.entrySet()) {
            if (!cur.mods.containsKey(ex.getKey()) && !loaderIds.contains(ex.getKey())) {
                r.modsRemoved.add(new Model.ModChange(ex.getKey(), ex.getValue().name, ex.getValue().version, null, ex.getValue().url));
            }
        }

        r.configs = ConfigTracker.diff(prev.configs, cur.configs, Config.get().configIgnoreKeys, cur.mods, prev.mods);
        r.configs.sort(Comparator.<Model.ConfigChange, String>comparing(cx -> Names.lower(cx.mod)).thenComparing(cx -> cx.path));
        diffNamed(prev.items, cur.items, r.itemsAdded, r.itemsRemoved, prev, cur);
        diffNamed(prev.entities, cur.entities, r.entitiesAdded, r.entitiesRemoved, prev, cur);
        Comparator<Model.ModChange> byName = Comparator.comparing(m -> Names.lower(m.name));
        r.modsAdded.sort(byName);
        r.modsRemoved.sort(byName);
        r.modsUpdated.sort(byName);
        r.modsDowngraded.sort(byName);
        return r;
    }

    private static void diffNamed(Map<String, String> prev, Map<String, String> cur,
                                  List<Named> added, List<Named> removed,
                                  PackSnapshot prevSnap, PackSnapshot curSnap) {
        for (Map.Entry<String, String> e : cur.entrySet()) {
            if (!prev.containsKey(e.getKey())) {
                added.add(new Named(e.getKey(), e.getValue(),
                        Names.modName(Names.namespace(e.getKey()), curSnap.mods, prevSnap.mods)));
            }
        }
        for (Map.Entry<String, String> e : prev.entrySet()) {
            if (!cur.containsKey(e.getKey())) {
                removed.add(new Named(e.getKey(), e.getValue(),
                        Names.modName(Names.namespace(e.getKey()), prevSnap.mods, curSnap.mods)));
            }
        }
        Comparator<Named> order = Comparator.comparing((Named n) -> Names.lower(n.mod)).thenComparing(n -> Names.lower(n.name));
        added.sort(order);
        removed.sort(order);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
