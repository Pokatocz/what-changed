package com.pokato.whatchanged.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.pokato.whatchanged.WhatChangedCommon;
import com.pokato.whatchanged.core.Model.ConfigChange;
import com.pokato.whatchanged.core.Model.ConfigFile;
import com.pokato.whatchanged.core.Model.KeyChange;
import com.pokato.whatchanged.core.Model.ModInfo;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Remembers the files in config/ and reports which were added, removed or changed - and for the usual formats
 * (TOML, JSON/JSON5, properties/INI/CFG, YAML) exactly which values changed.
 * Files are compared by their parsed key/value pairs, so comments, key order and formatting don't count as changes.
 */
public final class ConfigTracker {
    private static final long MAX_BYTES = 512 * 1024;
    private static final int MAX_FILES = 3000;
    private static final int MAX_KEYS = 4000;
    private static final int MAX_VALUE = 200;
    private static final int MAX_KEY_CHANGES = 100;
    private static final int MAX_TOTAL_KEYS = 150_000;
    private static final Set<String> TEXT = Set.of("toml", "json", "json5", "jsonc", "properties", "cfg", "conf", "ini",
            "txt", "yml", "yaml", "snbt", "hocon", "config", "settings");

    private ConfigTracker() {}

    // ------------------------------------------------------------------------------------------------ capture

    /** Snapshot of every text config file under dir (relative '/' paths), skipping the ignore globs. */
    public static Map<String, ConfigFile> capture(Path dir, List<String> ignoreGlobs) {
        Map<String, ConfigFile> out = new TreeMap<>();
        if (dir == null || !Files.isDirectory(dir)) return out;
        List<Pattern> ignore = globs(ignoreGlobs);
        Path root;
        try {
            root = dir.toRealPath(); // a symlinked config folder (some launchers) is walked too
        } catch (Throwable t) {
            root = dir;
        }
        final Path base = root;
        // collect first (sorted, so a pack with more than MAX_FILES configs always keeps the same ones), read after;
        // a folder we may not enter is skipped instead of aborting the whole scan
        TreeMap<String, Path> files = new TreeMap<>();
        try {
            Files.walkFileTree(base, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 8,
                    new java.nio.file.SimpleFileVisitor<>() {
                        @Override
                        public java.nio.file.FileVisitResult visitFile(Path p, java.nio.file.attribute.BasicFileAttributes attrs) {
                            if (attrs.isRegularFile() && attrs.size() <= MAX_BYTES) {
                                String rel = base.relativize(p).toString().replace('\\', '/');
                                if (TEXT.contains(ext(rel)) && !matches(ignore, rel)) files.put(rel, p);
                            }
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }

                        @Override
                        public java.nio.file.FileVisitResult visitFileFailed(Path p, java.io.IOException e) {
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                    });
        } catch (Throwable t) {
            WhatChangedCommon.LOGGER.warn("[What Changed?] Could not scan {}: {}", dir, t.toString());
        }
        int keys = 0;
        for (Map.Entry<String, Path> e : files.entrySet()) {
            if (out.size() >= MAX_FILES) break;
            try {
                String text = new String(Files.readAllBytes(e.getValue()), StandardCharsets.UTF_8);
                ConfigFile f = read(e.getKey(), text);
                if (f.k != null) {
                    keys += f.k.size();
                    if (keys > MAX_TOTAL_KEYS) f = new ConfigFile(f.h, null); // huge packs: hash only from here on
                }
                out.put(e.getKey(), f);
            } catch (Throwable ignored) {
                // unreadable / locked file: skip it this time
            }
        }
        return out;
    }

    static ConfigFile read(String path, String text) {
        Map<String, String> kv = null;
        try {
            kv = parse(path, text);
        } catch (Throwable ignored) {
        }
        if (kv != null && kv.size() > MAX_KEYS) kv = null;
        if (kv != null && !kv.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, String> e : kv.entrySet()) b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
            return new ConfigFile(hash(b.toString()), kv);
        }
        return new ConfigFile(hash(normalizeText(text)), null);
    }

    /** Flattened key -> value, or null when the format isn't understood. Keys sorted. */
    static Map<String, String> parse(String path, String text) {
        String ext = ext(path);
        Map<String, String> kv = switch (ext) {
            case "toml" -> toml(text);
            case "json", "json5", "jsonc" -> json(text);
            case "yml", "yaml" -> yaml(text);
            case "properties", "cfg", "conf", "ini", "config", "settings" -> keyValue(text, false);
            case "txt" -> keyValue(text, true);
            default -> null;
        };
        if (kv == null) return null;
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : kv.entrySet()) sorted.put(e.getKey(), mask(e.getKey(), clip(e.getValue())));
        return sorted;
    }

    // ------------------------------------------------------------------------------------------------ diff

    /**
     * Adds the config differences to out. The mod maps (current first) are used to say which mod a file belongs to.
     */
    @SafeVarargs
    public static List<ConfigChange> diff(Map<String, ConfigFile> prev, Map<String, ConfigFile> cur, List<String> ignoreKeys,
                                          Map<String, ModInfo>... mods) {
        List<ConfigChange> out = new ArrayList<>();
        if (prev == null || cur == null) return out;
        List<String> ignored = new ArrayList<>();
        if (ignoreKeys != null) for (String s : ignoreKeys) if (s != null && !s.isBlank()) ignored.add(s.toLowerCase(Locale.ROOT));
        Set<String> paths = new TreeSet<>(prev.keySet());
        paths.addAll(cur.keySet());
        for (String path : paths) {
            ConfigFile a = prev.get(path), b = cur.get(path);
            if (a == null) {
                out.add(new ConfigChange(path, owner(path, mods), ConfigChange.ADDED));
            } else if (b == null) {
                out.add(new ConfigChange(path, owner(path, mods), ConfigChange.REMOVED));
            } else if (a.h != null && !a.h.equals(b.h)) {
                ConfigChange c = new ConfigChange(path, owner(path, mods), ConfigChange.CHANGED);
                if (a.k != null && b.k != null) {
                    Set<String> keys = new TreeSet<>(a.k.keySet());
                    keys.addAll(b.k.keySet());
                    int n = 0;
                    for (String k : keys) {
                        String x = a.k.get(k), y = b.k.get(k);
                        if (x == null ? y == null : x.equals(y)) continue;
                        if (isIgnored(k, ignored)) continue;
                        if (++n <= MAX_KEY_CHANGES) c.keys.add(new KeyChange(k, x, y));
                        else c.moreKeys++;
                    }
                    if (n == 0) continue; // only comments / order / ignored keys changed
                }
                out.add(c);
            }
        }
        return out;
    }

    private static boolean isIgnored(String key, List<String> ignored) {
        String k = key.toLowerCase(Locale.ROOT);
        for (String s : ignored) if (k.contains(s)) return true;
        return false;
    }

    /** Which mod a config file belongs to: the mod whose id/name the file or its folder starts with. */
    @SafeVarargs
    static String owner(String path, Map<String, ModInfo>... mods) {
        String dir = path.contains("/") ? norm(path.substring(0, path.indexOf('/'))) : "";
        String file = path.substring(path.lastIndexOf('/') + 1);
        int dot = file.lastIndexOf('.');
        String base = norm(dot > 0 ? file.substring(0, dot) : file);
        String best = null;
        int bestScore = 0;
        for (Map<String, ModInfo> m : mods) {
            if (m == null) continue;
            for (Map.Entry<String, ModInfo> e : m.entrySet()) {
                String name = e.getValue() != null && e.getValue().name != null ? e.getValue().name : e.getKey();
                for (String token : new String[]{norm(e.getKey()), norm(name)}) {
                    if (token.length() < 3) continue;
                    int score = 0;
                    if (token.equals(base) || token.equals(dir)) score = 1000 + token.length();       // jei.toml, create/
                    else if (base.startsWith(token) || (!dir.isEmpty() && dir.startsWith(token))) score = 100 + token.length();
                    else if (dir.length() >= 4 && token.startsWith(dir)) score = dir.length();         // fabric/ -> Fabric API
                    if (score > bestScore) {
                        bestScore = score;
                        best = name;
                    }
                }
            }
        }
        return best != null ? best : "Other";
    }

    // ------------------------------------------------------------------------------------------------ parsers

    static Map<String, String> toml(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, Integer> arrays = new HashMap<>();
        String section = "";
        String pendingKey = null;
        StringBuilder pending = null;
        boolean tripleQuote = false;
        for (String raw : text.split("\r?\n")) {
            if (pending != null) {
                pending.append(tripleQuote ? "\n" : " ").append(tripleQuote ? raw : stripComment(raw).trim());
                String v = pending.toString();
                if (tripleQuote ? v.indexOf("\"\"\"", 3) >= 0 || v.indexOf("'''", 3) >= 0 : balanced(v)) {
                    out.put(pendingKey, tomlValue(v));
                    pending = null;
                }
                continue;
            }
            String t = stripComment(raw).trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("[[") && t.endsWith("]]")) {
                String name = unquoteKey(t.substring(2, t.length() - 2).trim());
                int i = arrays.merge(name, 1, Integer::sum) - 1;
                section = name + "[" + i + "]";
            } else if (t.startsWith("[") && t.endsWith("]")) {
                section = unquoteKey(t.substring(1, t.length() - 1).trim());
            } else {
                int eq = indexOutsideQuotes(t, '=');
                if (eq <= 0) continue;
                String key = unquoteKey(t.substring(0, eq).trim());
                String val = t.substring(eq + 1).trim();
                String full = section.isEmpty() ? key : section + "." + key;
                if ((val.startsWith("\"\"\"") || val.startsWith("'''")) && !(val.length() > 3 && (val.indexOf("\"\"\"", 3) >= 0 || val.indexOf("'''", 3) >= 0))) {
                    pendingKey = full;
                    pending = new StringBuilder(val);
                    tripleQuote = true;
                } else if (!balanced(val)) {
                    pendingKey = full;
                    pending = new StringBuilder(val);
                    tripleQuote = false;
                } else {
                    out.put(full, tomlValue(val));
                }
            }
        }
        return out;
    }

    private static String tomlValue(String v) {
        String s = v.trim();
        if (s.startsWith("[") || s.startsWith("{")) return squeeze(s);
        for (String q : new String[]{"\"\"\"", "'''"}) { // multi-line strings
            if (s.length() >= 6 && s.startsWith(q) && s.endsWith(q)) return s.substring(3, s.length() - 3).trim();
        }
        return unquoteValue(s);
    }

    static Map<String, String> json(String text) {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(true);
        JsonElement root = JsonParser.parseReader(reader);
        Map<String, String> out = new LinkedHashMap<>();
        flattenJson("", root, out, 0);
        return out;
    }

    private static void flattenJson(String prefix, JsonElement e, Map<String, String> out, int depth) {
        if (e == null || e.isJsonNull()) {
            out.put(prefix.isEmpty() ? "(root)" : prefix, "null");
        } else if (e.isJsonObject() && depth < 12) {
            if (e.getAsJsonObject().size() == 0) out.put(prefix.isEmpty() ? "(root)" : prefix, "{}");
            for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().entrySet()) {
                flattenJson(prefix.isEmpty() ? m.getKey() : prefix + "." + m.getKey(), m.getValue(), out, depth + 1);
            }
        } else if (e.isJsonPrimitive()) {
            out.put(prefix.isEmpty() ? "(root)" : prefix, e.getAsJsonPrimitive().isString() ? e.getAsString() : e.toString());
        } else {
            out.put(prefix.isEmpty() ? "(root)" : prefix, e.toString()); // arrays (and very deep objects) as one value
        }
    }

    /** properties / INI / old Forge .cfg ("category { S:key=value }"); txt only when it mostly looks like key=value. */
    static Map<String, String> keyValue(String text, boolean strict) {
        Map<String, String> out = new LinkedHashMap<>();
        Deque<String> blocks = new ArrayDeque<>();
        String section = "";
        int lines = 0, pairs = 0;
        String listKey = null;
        StringBuilder listVal = null;
        for (String raw : text.split("\r?\n")) {
            String t = raw.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith(";") || t.startsWith("//") || t.startsWith("!")) continue;
            lines++;
            if (listKey != null) { // old Forge .cfg list:  S:names <  a  b  >
                pairs++;
                if (t.equals(">")) {
                    out.put(listKey, listVal.toString());
                    listKey = null;
                } else {
                    if (listVal.length() > 0) listVal.append(", ");
                    listVal.append(t);
                }
                continue;
            }
            if (t.endsWith("<") && indexOutsideQuotes(t, '=') < 0) {
                listKey = fullKey(section, blocks, cfgKey(t.substring(0, t.length() - 1).trim()));
                listVal = new StringBuilder();
                pairs++;
                continue;
            }
            if (t.startsWith("[") && t.endsWith("]")) {
                section = t.substring(1, t.length() - 1).trim();
                pairs++;
                continue;
            }
            if (t.endsWith("{") && indexOutsideQuotes(t, '=') < 0) {
                blocks.push(unquoteKey(t.substring(0, t.length() - 1).trim()));
                pairs++;
                continue;
            }
            if (t.equals("}") || t.equals(">")) {
                if (!blocks.isEmpty()) blocks.pop();
                pairs++;
                continue;
            }
            int sep = indexOutsideQuotes(t, '=');
            if (sep <= 0) sep = indexOutsideQuotes(t, ':');
            if (sep <= 0) continue;
            pairs++;
            String key = cfgKey(t.substring(0, sep).trim());
            String val = t.substring(sep + 1).trim();
            out.put(fullKey(section, blocks, key), unquoteValue(val));
        }
        if (strict && (lines == 0 || pairs * 2 < lines)) return null;
        return out;
    }

    private static String fullKey(String section, Deque<String> blocks, String key) {
        StringBuilder full = new StringBuilder();
        if (!section.isEmpty()) full.append(section).append('.');
        List<String> path = new ArrayList<>(blocks);
        for (int i = path.size() - 1; i >= 0; i--) full.append(path.get(i)).append('.');
        return full.append(key).toString();
    }

    /** Old Forge .cfg keys carry a type prefix ("B:enabled", "I:count") - drop it, and quotes around the name. */
    private static String cfgKey(String key) {
        String k = key;
        if (k.length() > 2 && k.charAt(1) == ':' && "BIDSL".indexOf(k.charAt(0)) >= 0) k = k.substring(2);
        if (k.length() >= 2 && k.startsWith("\"") && k.endsWith("\"")) k = k.substring(1, k.length() - 1);
        return k;
    }

    /** Minimal YAML: nested "key: value" maps by indentation; "- item" lists become one comma-joined value. */
    static Map<String, String> yaml(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        Deque<int[]> indents = new ArrayDeque<>();
        Deque<String> keys = new ArrayDeque<>();
        Set<String> parents = new java.util.HashSet<>(); // "section:" lines, dropped unless a list filled them
        String lastKey = null;
        for (String raw : text.split("\r?\n")) {
            String noComment = stripComment(raw);
            if (noComment.trim().isEmpty() || noComment.trim().equals("---")) continue;
            int indent = 0;
            while (indent < noComment.length() && noComment.charAt(indent) == ' ') indent++;
            String t = noComment.trim();
            if (t.startsWith("- ") || t.equals("-")) {
                if (lastKey != null) {
                    String item = unquoteValue(t.substring(1).trim());
                    String prev = out.get(lastKey);
                    out.put(lastKey, prev == null || prev.isEmpty() ? item : prev + ", " + item);
                }
                continue;
            }
            int colon = indexOutsideQuotes(t, ':');
            if (colon <= 0) continue;
            while (!indents.isEmpty() && indents.peek()[0] >= indent) {
                indents.pop();
                keys.pop();
            }
            String key = unquoteKey(t.substring(0, colon).trim());
            String val = t.substring(colon + 1).trim();
            List<String> path = new ArrayList<>(keys);
            StringBuilder full = new StringBuilder();
            for (int i = path.size() - 1; i >= 0; i--) full.append(path.get(i)).append('.');
            full.append(key);
            lastKey = full.toString();
            if (val.isEmpty()) {
                indents.push(new int[]{indent});
                keys.push(key);
                parents.add(lastKey);
                out.put(lastKey, "");
            } else {
                out.put(lastKey, unquoteValue(val)); // an explicit "" stays a value
            }
        }
        for (String k : parents) if ("".equals(out.get(k))) out.remove(k);
        return out;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Removes a # comment that is not inside quotes. */
    private static String stripComment(String s) {
        boolean dq = false, sq = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !sq && (i == 0 || s.charAt(i - 1) != '\\')) dq = !dq;
            else if (c == '\'' && !dq) sq = !sq;
            else if (c == '#' && !dq && !sq) return s.substring(0, i);
        }
        return s;
    }

    private static int indexOutsideQuotes(String s, char target) {
        boolean dq = false, sq = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !sq) dq = !dq;
            else if (c == '\'' && !dq) sq = !sq;
            else if (c == target && !dq && !sq) return i;
        }
        return -1;
    }

    private static boolean balanced(String s) {
        int depth = 0;
        boolean dq = false, sq = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !sq && (i == 0 || s.charAt(i - 1) != '\\')) dq = !dq;
            else if (c == '\'' && !dq) sq = !sq;
            else if (!dq && !sq && (c == '[' || c == '{')) depth++;
            else if (!dq && !sq && (c == ']' || c == '}')) depth--;
        }
        return depth <= 0;
    }

    /** Removes whitespace outside quotes: "[1, 2]" and "[1,2]" are the same value. */
    private static String squeeze(String s) {
        StringBuilder b = new StringBuilder();
        boolean dq = false, sq = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !sq) dq = !dq;
            else if (c == '\'' && !dq) sq = !sq;
            if (!dq && !sq && Character.isWhitespace(c)) continue;
            b.append(c);
            if (!dq && !sq && c == ',') b.append(' ');
        }
        return b.toString().replace(", ]", "]").replace(", }", "}");
    }

    private static String unquoteKey(String k) {
        StringBuilder b = new StringBuilder();
        for (String part : k.split("\\.")) {
            String p = part.trim();
            if (p.length() >= 2 && (p.startsWith("\"") && p.endsWith("\"") || p.startsWith("'") && p.endsWith("'"))) {
                p = p.substring(1, p.length() - 1);
            }
            if (b.length() > 0) b.append('.');
            b.append(p);
        }
        return b.toString();
    }

    private static String unquoteValue(String v) {
        String s = v.trim();
        if (s.endsWith(",")) s = s.substring(0, s.length() - 1).trim();
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * Tokens, passwords, webhooks...: never stored or shown, only a short hash so a change is still noticed.
     * "token" only at the end of the key ("discord.token", "accessToken"), so "tokenCost" or "maxTokens" stay visible.
     */
    private static final Pattern SECRET = Pattern.compile(
            "passw|secret|apikey|webhook|credential|privatekey|authkey|accesskey|token$|bearer");

    static boolean isSecret(String key) {
        return SECRET.matcher(norm(key)).find();
    }

    private static String mask(String key, String v) {
        if (v == null || v.isEmpty() || !isSecret(key)) return v;
        return "(hidden #" + hash(v).substring(0, 6) + ")";
    }

    private static String clip(String v) {
        if (v == null) return "";
        if (v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0 || v.indexOf('\t') >= 0) {
            v = v.replaceAll("\\s*[\\r\\n]+\\s*", " ⏎ ").replace('\t', ' '); // multi-line strings shown on one line
        }
        if (v.length() <= MAX_VALUE) return v;
        return v.substring(0, MAX_VALUE - 20) + "…#" + hash(v).substring(0, 8); // long values: still detects changes
    }

    private static String normalizeText(String text) {
        StringBuilder b = new StringBuilder();
        for (String raw : text.split("\r?\n")) {
            String t = raw.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("//") || t.startsWith(";")) continue;
            b.append(t).append('\n');
        }
        return b.toString();
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", d[i]));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String ext(String path) {
        String f = path.substring(path.lastIndexOf('/') + 1);
        int dot = f.lastIndexOf('.');
        return dot < 0 ? "" : f.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static List<Pattern> globs(List<String> globs) {
        List<Pattern> out = new ArrayList<>();
        if (globs == null) return out;
        for (String g : globs) {
            if (g == null || g.isBlank()) continue;
            StringBuilder re = new StringBuilder();
            String s = g.trim().replace('\\', '/');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '*' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                    boolean slash = i + 2 < s.length() && s.charAt(i + 2) == '/';
                    re.append(slash ? "(?:.*/)?" : ".*");
                    i += slash ? 2 : 1;
                } else if (c == '*') re.append("[^/]*");
                else if (c == '?') re.append("[^/]");
                else re.append(Pattern.quote(String.valueOf(c)));
            }
            out.add(Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE));
        }
        return out;
    }

    private static boolean matches(List<Pattern> patterns, String path) {
        for (Pattern p : patterns) if (p.matcher(path).matches()) return true;
        return false;
    }
}
