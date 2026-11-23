package com.pokato.whatchanged.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.core.Model.RecipeChange;
import com.pokato.whatchanged.core.Model.RecipeFp;
import com.pokato.whatchanged.core.Model.WorldReport;
import com.pokato.whatchanged.core.Model.WorldSnapshot;
import com.pokato.whatchanged.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Per-world tracking (runs on the logical server: singleplayer or dedicated).
 * Recipes, biomes, structures, enchantments and dimensions come from the world's mods + datapacks,
 * so each world keeps its own snapshot in <world>/whatchanged/.
 * In singleplayer the snapshot is a baseline that only advances once the host has seen the report;
 * on dedicated servers it advances immediately (the changelog file is the "seen").
 */
public final class WorldTracker {
    /** Report of the currently running world (null until computed). Read by the client in singleplayer. */
    public static volatile WorldReport current;
    public static volatile boolean ready;
    private static volatile WorldSnapshot currentSnapshot;
    private static volatile Path snapshotPath;
    private static volatile Path reportPath;
    private static volatile int generation;

    private WorldTracker() {}

    public static void onServerStarted(MinecraftServer server) {
        check(server, "world start");
    }

    public static void onDataPackReload(MinecraftServer server) {
        check(server, "datapack reload");
    }

    private static void check(MinecraftServer server, String reason) {
        ready = false;
        final int gen = ++generation;
        try {
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("whatchanged");
            Path snapPath = dir.resolve("world-snapshot.json");
            Path repPath = dir.resolve("world-report.json");
            snapshotPath = snapPath;
            reportPath = repPath;
            String worldName = server.getWorldData().getLevelName();
            boolean dedicated = server.isDedicatedServer();

            // Gather everything on the server thread, hash it on a background thread.
            List<CoreCompat.RecipeEntry> recipes = CoreCompat.get().recipes(server);
            List<String> biomes = ids(server, Registries.BIOME);
            List<String> structures = ids(server, Registries.STRUCTURE);
            List<String> enchantments = ids(server, Registries.ENCHANTMENT);
            List<String> dimensions = new ArrayList<>();
            for (ResourceKey<Level> k : server.levelKeys()) dimensions.add(Names.keyId(k));
            dimensions.sort(null);

            Thread t = new Thread(() -> {
                long start = System.currentTimeMillis();
                WorldSnapshot snap = capture(recipes, biomes, structures, enchantments, dimensions);
                WorldSnapshot baseline = Json.read(snapPath, WorldSnapshot.class);
                if (baseline != null && baseline.format != Model.FORMAT) baseline = null;
                WorldReport existing = Json.read(repPath, WorldReport.class);
                WorldReport report;
                boolean changed = false;
                String outcome;
                if (baseline == null) {
                    report = null;
                    Json.delete(repPath);
                    Json.write(snapPath, snap, false);
                    outcome = "first run, snapshot saved";
                } else {
                    WorldReport diff = diff(baseline, snap, worldName);
                    if (diff.isEmpty()) {
                        report = existing != null && existing.seen ? existing : null;
                        if (report == null && existing != null) Json.delete(repPath);
                        if (dedicated) Json.write(snapPath, snap, false);
                        outcome = "no changes";
                    } else if (existing != null && !existing.seen && diff.sameChangesAs(existing)) {
                        report = existing;
                        outcome = "unchanged report: " + summary(report);
                    } else {
                        report = diff;
                        changed = true;
                        Json.write(repPath, report, true);
                        if (dedicated) Json.write(snapPath, snap, false); // dedicated: baseline advances right away
                        outcome = summary(report);
                    }
                }
                WhatChangedCommon.LOGGER.info("[What Changed?] World '{}' checked after {} in {} ms ({} recipes): {}", worldName,
                        reason, System.currentTimeMillis() - start, snap.recipes.size(), outcome);

                final WorldReport finalReport = report;
                final boolean finalChanged = changed;
                server.execute(() -> {
                    if (gen != generation) return; // world was closed meanwhile
                    currentSnapshot = snap;
                    current = finalReport;
                    ready = true;
                    if (finalReport == null || finalReport.isEmpty()) return;
                    if (finalChanged && dedicated) writeServerChangelog(finalReport);
                    for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
                        onPlayerJoin(server, p);
                    }
                });
            }, "WhatChanged world check");
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.error("[What Changed?] World check failed", t);
        }
    }

    public static Path writeServerChangelog(WorldReport r) {
        String md = Markdown.world(r, null, false);
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date(r.detected));
        Path out = Config.dir().resolve("changelogs").resolve("server-" + stamp + ".md");
        Json.writeText(out, md);
        WhatChangedCommon.LOGGER.info("[What Changed?] Server changelog written to {}", out.toAbsolutePath());
        return out;
    }

    public static void onServerStopped() {
        generation++;
        current = null;
        currentSnapshot = null;
        ready = false;
        reportPath = null;
        snapshotPath = null;
    }

    /** Server thread. Host (singleplayer): mark crafted recipes. Everyone else: one chat summary per report. */
    public static void onPlayerJoin(MinecraftServer server, ServerPlayer player) {
        WorldReport r = current;
        if (!ready || r == null || r.isEmpty()) return;
        try {
            if (isHost(server, player)) {
                markCrafted(r, player); // re-checked on every join: the host may have crafted more since
                r.craftedChecked = true;
                save(r);
                return; // the host's client shows its own notice + full screen
            }
            String uuid = player.getUUID().toString();
            if (r.notified.contains(uuid) || !Config.get().chatNotifications) return;
            r.notified.add(uuid);
            save(r);
            Component body = Component.translatableWithFallback("whatchanged.chat.server_world",
                    "This world's recipes/worldgen changed since %s: %s", date(r.previous), summary(r));
            CoreCompat.get().sendSystemMessage(player, Component.translatableWithFallback("whatchanged.chat.prefix", "[What Changed?] ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(body.copy().withStyle(ChatFormatting.GRAY))
                    .append(CoreCompat.get().withRunCommand(Component.translatableWithFallback("whatchanged.chat.click_details", " [details]")
                            .withStyle(ChatFormatting.AQUA), "/whatchanged")));
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] Join notice failed: {}", t.toString());
        }
    }

    private static boolean isHost(MinecraftServer server, ServerPlayer player) {
        if (server.isDedicatedServer()) return false;
        String host = Names.profileName(server.getSingleplayerProfile());
        return host == null || host.equals(player.getName().getString());
    }

    /** The host has looked at the report: advance the world baseline (singleplayer only). */
    public static void markSeen() {
        WorldReport r = current;
        if (r != null && !r.seen) {
            r.seen = true;
            save(r);
            WorldSnapshot snap = currentSnapshot;
            Path p = snapshotPath;
            if (snap != null && p != null) Json.writeAsync(p, snap, false);
        }
    }

    /** Forget this world's history and start from its current state. */
    public static void reset() {
        current = null;
        Json.delete(reportPath);
        WorldSnapshot snap = currentSnapshot;
        Path p = snapshotPath;
        if (snap != null && p != null) {
            snap.time = System.currentTimeMillis();
            Json.writeAsync(p, snap, false);
        }
    }

    private static void save(WorldReport r) {
        Path p = reportPath;
        if (p != null) Json.writeAsync(p, r, true);
    }

    private static void markCrafted(WorldReport r, ServerPlayer player) {
        Map<String, Item> items = Names.itemsById();
        List<RecipeChange> all = new ArrayList<>(r.recipesChanged);
        all.addAll(r.recipesRemoved);
        for (RecipeChange c : all) {
            if (c.out == null || c.crafted) continue;
            Item item = items.get(c.out);
            if (item == null) continue;
            try {
                if (player.getStats().getValue(Stats.ITEM_CRAFTED.get(item)) > 0) c.crafted = true;
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Ids of a dynamic registry. registries() / registryKeySet() exist unchanged from 1.20 to 26.x, unlike
     * registryOrThrow (<= 1.21.1) / lookupOrThrow (1.21.2+), and avoid ResourceLocation (renamed Identifier in 1.21.11).
     */
    private static List<String> ids(MinecraftServer server, ResourceKey<? extends Registry<?>> key) {
        List<String> out = new ArrayList<>();
        try {
            server.registryAccess().registries()
                    .filter(e -> e.key().equals(key))
                    .findFirst()
                    .ifPresent(e -> {
                        for (ResourceKey<?> k : e.value().registryKeySet()) out.add(Names.keyId(k));
                    });
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] Could not read registry {}: {}", key, t.toString());
        }
        out.sort(null);
        return out;
    }

    private static WorldSnapshot capture(List<CoreCompat.RecipeEntry> recipes,
                                         List<String> biomes, List<String> structures,
                                         List<String> enchantments, List<String> dimensions) {
        WorldSnapshot s = new WorldSnapshot();
        s.time = System.currentTimeMillis();
        s.minecraft = Platform.get().minecraftVersion();
        s.biomes = biomes;
        s.structures = structures;
        s.enchantments = enchantments;
        s.dimensions = dimensions;
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-1");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        for (CoreCompat.RecipeEntry entry : recipes) {
            String hash;
            String out = null;
            try {
                CoreCompat.Encoded enc = entry.encode().get();
                out = enc.output();
                byte[] d = sha.digest(enc.canonical());
                StringBuilder hex = new StringBuilder();
                for (int i = 0; i < 8; i++) hex.append(String.format("%02x", d[i]));
                hash = hex.toString();
            } catch (Throwable t) {
                hash = "?" + entry.type(); // not serializable: track existence only
            }
            s.recipes.put(entry.id(), new RecipeFp(hash, out));
        }
        return s;
    }

    /** Canonical (sorted keys) JSON bytes plus the output id read from it - used by the JSON based implementations. */
    public static CoreCompat.Encoded encodedJson(JsonElement json) {
        return new CoreCompat.Encoded(Json.canonical(json).toString().getBytes(StandardCharsets.UTF_8), outputId(json));
    }

    /** Best-effort output item id from a recipe's JSON (vanilla and most modded formats). */
    public static String outputId(JsonElement json) {
        if (json == null || !json.isJsonObject()) return null;
        JsonObject o = json.getAsJsonObject();
        for (String key : new String[]{"result", "output", "results", "outputs"}) {
            String id = idOf(o.get(key), 0);
            if (id != null) return id;
        }
        return null;
    }

    private static String idOf(JsonElement e, int depth) {
        if (e == null || e.isJsonNull() || depth > 3) return null;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString().trim();
            if (s.isEmpty() || s.startsWith("#")) return null; // empty or a tag, not an item
            return s.contains(":") ? s : "minecraft:" + s;
        }
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            return a.isEmpty() ? null : idOf(a.get(0), depth + 1);
        }
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            for (String key : new String[]{"id", "item", "fluid"}) {
                JsonElement v = o.get(key);
                if (v != null && v.isJsonPrimitive()) return idOf(v, depth + 1);
                if (v != null && v.isJsonObject()) return idOf(v, depth + 1);
            }
        }
        return null;
    }

    static WorldReport diff(WorldSnapshot prev, WorldSnapshot cur, String worldName) {
        WorldReport r = new WorldReport();
        r.detected = cur.time;
        r.previous = prev.time;
        r.world = worldName;
        r.mcOld = prev.minecraft;
        r.mcNew = cur.minecraft;
        // A new game version serializes recipes differently - hash differences would be noise.
        r.recipesUncomparable = prev.minecraft != null && cur.minecraft != null && !prev.minecraft.equals(cur.minecraft);
        Map<String, Item> items = Names.itemsById();

        for (Map.Entry<String, RecipeFp> e : cur.recipes.entrySet()) {
            RecipeFp old = prev.recipes.get(e.getKey());
            RecipeFp now = e.getValue();
            if (old == null) {
                r.recipesAdded.add(change(e.getKey(), now, items));
            } else if (!r.recipesUncomparable && !old.h.startsWith("?") && !now.h.startsWith("?") && !old.h.equals(now.h)) {
                r.recipesChanged.add(change(e.getKey(), now, items));
            }
        }
        for (Map.Entry<String, RecipeFp> e : prev.recipes.entrySet()) {
            if (!cur.recipes.containsKey(e.getKey())) r.recipesRemoved.add(change(e.getKey(), e.getValue(), items));
        }
        setDiff(prev.biomes, cur.biomes, r.biomesAdded, r.biomesRemoved);
        setDiff(prev.structures, cur.structures, r.structuresAdded, r.structuresRemoved);
        setDiff(prev.enchantments, cur.enchantments, r.enchantmentsAdded, r.enchantmentsRemoved);
        setDiff(prev.dimensions, cur.dimensions, r.dimensionsAdded, r.dimensionsRemoved);

        Comparator<RecipeChange> order = Comparator.comparing((RecipeChange c) -> Names.lower(c.mod))
                .thenComparing(c -> Names.lower(c.outName)).thenComparing(c -> c.id);
        r.recipesAdded.sort(order);
        r.recipesRemoved.sort(order);
        r.recipesChanged.sort(order);
        return r;
    }

    private static RecipeChange change(String id, RecipeFp fp, Map<String, Item> items) {
        String outName;
        if (fp.o != null && items.containsKey(fp.o)) outName = Names.itemName(items.get(fp.o), fp.o);
        else if (fp.o != null) outName = Names.prettify(Names.path(fp.o));
        else outName = Names.prettify(Names.path(id));
        return new RecipeChange(id, fp.o, outName, Names.modName(Names.namespace(id)));
    }

    private static void setDiff(List<String> prev, List<String> cur, List<String> added, List<String> removed) {
        Set<String> p = new TreeSet<>(prev == null ? List.of() : prev);
        Set<String> c = new TreeSet<>(cur == null ? List.of() : cur);
        for (String s : c) if (!p.contains(s)) added.add(s);
        for (String s : p) if (!c.contains(s)) removed.add(s);
    }

    public static String summary(WorldReport r) {
        List<String> parts = new ArrayList<>();
        part(parts, r.recipesAdded.size(), "new recipe");
        part(parts, r.recipesChanged.size(), "changed recipe");
        part(parts, r.recipesRemoved.size(), "removed recipe");
        part(parts, r.biomesAdded.size(), "new biome");
        part(parts, r.biomesRemoved.size(), "removed biome");
        part(parts, r.structuresAdded.size(), "new structure");
        part(parts, r.structuresRemoved.size(), "removed structure");
        part(parts, r.enchantmentsAdded.size(), "new enchantment");
        part(parts, r.enchantmentsRemoved.size(), "removed enchantment");
        part(parts, r.dimensionsAdded.size(), "new dimension");
        part(parts, r.dimensionsRemoved.size(), "removed dimension");
        return parts.isEmpty() ? "nothing" : String.join(", ", parts);
    }

    private static void part(List<String> parts, int n, String what) {
        if (n > 0) parts.add(n + " " + what + (n == 1 ? "" : "s"));
    }

    public static String date(long millis) {
        return millis <= 0 ? "?" : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(millis));
    }
}
