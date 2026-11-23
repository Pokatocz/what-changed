package com.pokato.whatchanged.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pokato.whatchanged.WhatChangedCommon;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small, crash-proof JSON file helper. A broken file never breaks the game. */
public final class Json {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WhatChanged IO");
        t.setDaemon(true);
        return t;
    });

    static {
        // a snapshot queued right before the player quits must still reach the disk
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    // drain what is queued; later writes (a world closing during shutdown) are still accepted
                    IO.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            }, "WhatChanged IO flush"));
        } catch (Throwable ignored) {
        }
    }

    private Json() {}

    public static <T> T read(Path path, Class<T> type) {
        if (path == null || !Files.isRegularFile(path)) return null;
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, type);
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] Could not read {} ({}), ignoring it.", path, t.toString());
            return null;
        }
    }

    /** Writes atomically (temp file + move) so a crash mid-write cannot corrupt the snapshot. */
    public static void write(Path path, Object value, boolean pretty) {
        try {
            writeText(path, (pretty ? GSON : COMPACT).toJson(value));
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] Could not write {} ({}).", path, t.toString());
        }
    }

    /** Serializes now (on the caller's thread), writes on a background thread - keeps the game thread smooth. */
    public static void writeAsync(Path path, Object value, boolean pretty) {
        try {
            String text = (pretty ? GSON : COMPACT).toJson(value);
            IO.execute(() -> writeText(path, text));
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] Could not serialize {} ({}).", path, t.toString());
        }
    }

    /** Runs on the IO thread, so it stays in order with earlier queued writes of the same file. */
    public static void delete(Path path) {
        if (path == null) return;
        try {
            IO.execute(() -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Throwable t) {
                    WhatChangedCommon.LOGGER.warn("[What Changed?] Could not delete {} ({}).", path, t.toString());
                }
            });
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] Could not delete {} ({}).", path, t.toString());
        }
    }

    public static void writeText(Path path, String text) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                w.write(text);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicNotSupported) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] Could not write {} ({}).", path, t.toString());
        }
    }

    /** Same JSON with object keys sorted recursively, so hashing ignores key order. */
    public static JsonElement canonical(JsonElement e) {
        if (e == null) return null;
        if (e.isJsonObject()) {
            TreeMap<String, JsonElement> sorted = new TreeMap<>();
            for (var entry : e.getAsJsonObject().entrySet()) sorted.put(entry.getKey(), canonical(entry.getValue()));
            JsonObject o = new JsonObject();
            sorted.forEach(o::add);
            return o;
        }
        if (e.isJsonArray()) {
            JsonArray a = new JsonArray();
            for (JsonElement x : e.getAsJsonArray()) a.add(canonical(x));
            return a;
        }
        return e;
    }
}
