package com.pokato.whatchanged.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Plain data classes, persisted with Gson. Public fields on purpose. */
public final class Model {
    /** Bump when a snapshot's layout changes; older files are then treated as "no previous snapshot". */
    public static final int FORMAT = 2;

    private Model() {}

    public static final class ModInfo {
        public String name;
        public String version;
        public String url;

        public ModInfo() {}

        public ModInfo(String name, String version, String url) {
            this.name = name;
            this.version = version;
            this.url = url;
        }
    }

    /** Everything we remember about the modpack between launches (client side). */
    public static final class PackSnapshot {
        public int format = FORMAT;
        public long time;
        public String minecraft;
        public Map<String, ModInfo> mods = new TreeMap<>();
        public Map<String, String> items = new TreeMap<>();
        public Map<String, String> entities = new TreeMap<>();
        /** Mod loader display name ("Forge", "NeoForge", "Fabric Loader") - its version lives in mods. */
        public String loader;
        /** config/ files (path relative to config/, '/' separated). Null in snapshots made before 1.2.0. */
        public Map<String, ConfigFile> configs;
    }

    /** One config file: hash of its normalized content, plus its flattened key/value pairs when the format is known. */
    public static final class ConfigFile {
        public String h;
        public Map<String, String> k;

        public ConfigFile() {}

        public ConfigFile(String h, Map<String, String> k) {
            this.h = h;
            this.k = k;
        }
    }

    public static final class KeyChange {
        public String key;
        public String oldValue;
        public String newValue;

        public KeyChange() {}

        public KeyChange(String key, String oldValue, String newValue) {
            this.key = key;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

    /** A config file that was added, removed or changed (with the changed values when the format is known). */
    public static final class ConfigChange {
        public static final int CHANGED = 0, ADDED = 1, REMOVED = 2;
        public String path;
        public String mod;
        public int kind;
        public List<KeyChange> keys = new ArrayList<>();
        /** Changed values not stored (the list is capped). */
        public int moreKeys;

        public ConfigChange() {}

        public ConfigChange(String path, String mod, int kind) {
            this.path = path;
            this.mod = mod;
            this.kind = kind;
        }
    }

    /** A registry entry (item, mob, ...) with a readable name and the mod it belongs to. */
    public static final class Named {
        public String id;
        public String name;
        public String mod;

        public Named() {}

        public Named(String id, String name, String mod) {
            this.id = id;
            this.name = name;
            this.mod = mod;
        }
    }

    public static final class ModChange {
        public String id;
        public String name;
        public String oldVersion;
        public String newVersion;
        public String url;

        public ModChange() {}

        public ModChange(String id, String name, String oldVersion, String newVersion, String url) {
            this.id = id;
            this.name = name;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.url = url;
        }
    }

    /** Result of comparing two pack snapshots. Kept until the next real change. */
    public static final class PackReport {
        public long detected;
        public long previous;
        public String mcOld;
        public String mcNew;
        public List<ModChange> modsAdded = new ArrayList<>();
        public List<ModChange> modsRemoved = new ArrayList<>();
        public List<ModChange> modsUpdated = new ArrayList<>();
        public List<ModChange> modsDowngraded = new ArrayList<>();
        /** Mod loader version change (null when unchanged). loaderOldName is set when the loader itself was switched. */
        public String loaderName;
        public String loaderOldName;
        public String loaderOld;
        public String loaderNew;
        public List<ConfigChange> configs = new ArrayList<>();
        public List<Named> itemsAdded = new ArrayList<>();
        public List<Named> itemsRemoved = new ArrayList<>();
        public List<Named> entitiesAdded = new ArrayList<>();
        public List<Named> entitiesRemoved = new ArrayList<>();
        public volatile boolean seen;

        public boolean isEmpty() {
            return modsAdded.isEmpty() && modsRemoved.isEmpty() && modsUpdated.isEmpty() && modsDowngraded.isEmpty()
                    && itemsAdded.isEmpty() && itemsRemoved.isEmpty()
                    && entitiesAdded.isEmpty() && entitiesRemoved.isEmpty()
                    && !loaderChanged() && configs.isEmpty();
        }

        /** Nothing but config files changed. */
        public boolean onlyConfigs() {
            return !configs.isEmpty() && modsAdded.isEmpty() && modsRemoved.isEmpty() && modsUpdated.isEmpty()
                    && modsDowngraded.isEmpty() && itemsAdded.isEmpty() && itemsRemoved.isEmpty()
                    && entitiesAdded.isEmpty() && entitiesRemoved.isEmpty() && !loaderChanged()
                    && (mcOld == null || mcOld.equals(mcNew));
        }

        public boolean loaderChanged() {
            return loaderOld != null && loaderNew != null && (!loaderOld.equals(loaderNew) || loaderSwitched());
        }

        /** A different mod loader than before (Forge -> NeoForge, ...). */
        public boolean loaderSwitched() {
            return loaderOldName != null && loaderName != null && !loaderOldName.equals(loaderName);
        }

        /** The loader went to an older version (never for a switch - the numbers aren't comparable). */
        public boolean loaderDowngraded() {
            return loaderChanged() && !loaderSwitched() && VersionCompare.isDowngrade(loaderOld, loaderNew);
        }

        public int added() {
            return modsAdded.size() + itemsAdded.size() + entitiesAdded.size();
        }

        public int removed() {
            return modsRemoved.size() + itemsRemoved.size() + entitiesRemoved.size();
        }

        /** Same set of changes as another report (used to keep the original detection time). */
        public boolean sameChangesAs(PackReport o) {
            return o != null && Diffs.sameMods(modsAdded, o.modsAdded) && Diffs.sameMods(modsRemoved, o.modsRemoved)
                    && Diffs.sameMods(modsUpdated, o.modsUpdated) && Diffs.sameMods(modsDowngraded, o.modsDowngraded)
                    && Diffs.eq(loaderOld, o.loaderOld) && Diffs.eq(loaderNew, o.loaderNew) && Diffs.eq(loaderOldName, o.loaderOldName)
                    && Diffs.sameConfigs(configs, o.configs) && Diffs.sameNamed(itemsAdded, o.itemsAdded)
                    && Diffs.sameNamed(itemsRemoved, o.itemsRemoved) && Diffs.sameNamed(entitiesAdded, o.entitiesAdded)
                    && Diffs.sameNamed(entitiesRemoved, o.entitiesRemoved);
        }
    }

    /** Recipe fingerprint: content hash + output item id (may be null). */
    public static final class RecipeFp {
        public String h;
        public String o;

        public RecipeFp() {}

        public RecipeFp(String h, String o) {
            this.h = h;
            this.o = o;
        }
    }

    /** Per-world snapshot (recipes and worldgen registries depend on the world's datapacks). */
    public static final class WorldSnapshot {
        public int format = FORMAT;
        public long time;
        public String minecraft;
        public Map<String, RecipeFp> recipes = new TreeMap<>();
        public List<String> biomes = new ArrayList<>();
        public List<String> structures = new ArrayList<>();
        public List<String> enchantments = new ArrayList<>();
        public List<String> dimensions = new ArrayList<>();
    }

    public static final class RecipeChange {
        public String id;
        public String out;
        public String outName;
        public String mod;
        public boolean crafted;

        public RecipeChange() {}

        public RecipeChange(String id, String out, String outName, String mod) {
            this.id = id;
            this.out = out;
            this.outName = outName;
            this.mod = mod;
        }
    }

    public static final class WorldReport {
        public long detected;
        public long previous;
        public String world;
        public String mcOld;
        public String mcNew;
        /** True when the game version changed: recipe encodings differ, so "changed" recipes are not reported. */
        public boolean recipesUncomparable;
        public List<RecipeChange> recipesAdded = new ArrayList<>();
        public List<RecipeChange> recipesRemoved = new ArrayList<>();
        public List<RecipeChange> recipesChanged = new ArrayList<>();
        public List<String> biomesAdded = new ArrayList<>();
        public List<String> biomesRemoved = new ArrayList<>();
        public List<String> structuresAdded = new ArrayList<>();
        public List<String> structuresRemoved = new ArrayList<>();
        public List<String> enchantmentsAdded = new ArrayList<>();
        public List<String> enchantmentsRemoved = new ArrayList<>();
        public List<String> dimensionsAdded = new ArrayList<>();
        public List<String> dimensionsRemoved = new ArrayList<>();
        public volatile boolean craftedChecked;
        public volatile boolean seen;
        public Set<String> notified = new HashSet<>();

        public boolean isEmpty() {
            return recipesAdded.isEmpty() && recipesRemoved.isEmpty() && recipesChanged.isEmpty()
                    && biomesAdded.isEmpty() && biomesRemoved.isEmpty()
                    && structuresAdded.isEmpty() && structuresRemoved.isEmpty()
                    && enchantmentsAdded.isEmpty() && enchantmentsRemoved.isEmpty()
                    && dimensionsAdded.isEmpty() && dimensionsRemoved.isEmpty();
        }

        public int craftedCount() {
            int n = 0;
            for (RecipeChange c : recipesChanged) if (c.crafted) n++;
            for (RecipeChange c : recipesRemoved) if (c.crafted) n++;
            return n;
        }

        public boolean sameChangesAs(WorldReport o) {
            return o != null && Diffs.sameRecipes(recipesAdded, o.recipesAdded)
                    && Diffs.sameRecipes(recipesRemoved, o.recipesRemoved)
                    && Diffs.sameRecipes(recipesChanged, o.recipesChanged)
                    && biomesAdded.equals(o.biomesAdded) && biomesRemoved.equals(o.biomesRemoved)
                    && structuresAdded.equals(o.structuresAdded) && structuresRemoved.equals(o.structuresRemoved)
                    && enchantmentsAdded.equals(o.enchantmentsAdded) && enchantmentsRemoved.equals(o.enchantmentsRemoved)
                    && dimensionsAdded.equals(o.dimensionsAdded) && dimensionsRemoved.equals(o.dimensionsRemoved);
        }
    }

    static final class Diffs {
        static boolean sameMods(List<ModChange> a, List<ModChange> b) {
            if (b == null || a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) {
                ModChange x = a.get(i), y = b.get(i);
                if (!eq(x.id, y.id) || !eq(x.oldVersion, y.oldVersion) || !eq(x.newVersion, y.newVersion)) return false;
            }
            return true;
        }

        static boolean sameConfigs(List<ConfigChange> a, List<ConfigChange> b) {
            if (b == null || a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) {
                ConfigChange x = a.get(i), y = b.get(i);
                if (!eq(x.path, y.path) || x.kind != y.kind || x.keys.size() != y.keys.size() || x.moreKeys != y.moreKeys) return false;
                for (int j = 0; j < x.keys.size(); j++) {
                    KeyChange p = x.keys.get(j), q = y.keys.get(j);
                    if (!eq(p.key, q.key) || !eq(p.oldValue, q.oldValue) || !eq(p.newValue, q.newValue)) return false;
                }
            }
            return true;
        }

        static boolean sameNamed(List<Named> a, List<Named> b) {
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) if (!eq(a.get(i).id, b.get(i).id)) return false;
            return true;
        }

        static boolean sameRecipes(List<RecipeChange> a, List<RecipeChange> b) {
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) if (!eq(a.get(i).id, b.get(i).id)) return false;
            return true;
        }

        static boolean eq(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }
    }
}
