package com.pokato.whatchanged.core;

import com.pokato.whatchanged.core.Model.ConfigChange;
import com.pokato.whatchanged.core.Model.KeyChange;
import com.pokato.whatchanged.core.Model.ModChange;
import com.pokato.whatchanged.core.Model.Named;
import com.pokato.whatchanged.core.Model.PackReport;
import com.pokato.whatchanged.core.Model.RecipeChange;
import com.pokato.whatchanged.core.Model.WorldReport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns reports into an English Markdown changelog that can be pasted on CurseForge / Modrinth / Discord. */
public final class Markdown {
    private Markdown() {}

    public static String full(PackReport pack, WorldReport world, boolean hideRecipesForNewItems) {
        StringBuilder b = new StringBuilder();
        b.append("# Modpack changelog\n\n");
        b.append("_Generated automatically by What Changed? on ")
                .append(WorldTracker.date(System.currentTimeMillis())).append("._\n\n");
        if (pack != null && !pack.isEmpty()) b.append(pack(pack));
        else b.append("No mod, item, mob or config changes detected.\n\n");
        if (world != null && !world.isEmpty()) b.append(world(world, newItemIds(pack), hideRecipesForNewItems));
        return b.toString();
    }

    public static Set<String> newItemIds(PackReport pack) {
        Set<String> ids = new HashSet<>();
        if (pack != null) for (Named n : pack.itemsAdded) ids.add(n.id);
        return ids;
    }

    public static String pack(PackReport r) {
        StringBuilder b = new StringBuilder();
        b.append("Changes since ").append(WorldTracker.date(r.previous))
                .append(" (detected ").append(WorldTracker.date(r.detected)).append(")");
        b.append("\n\n");
        boolean top = false;
        if (r.mcOld != null && !r.mcOld.equals(r.mcNew)) {
            String url = Loaders.minecraftUrl(r.mcNew);
            b.append("- **Minecraft** ").append(r.mcOld).append(" → ").append(r.mcNew)
                    .append(VersionCompare.isDowngrade(r.mcOld, r.mcNew) ? " (downgraded)" : "");
            if (url != null) b.append(" · [what's new](").append(url).append(")");
            b.append("\n");
            top = true;
        }
        if (r.loaderChanged()) {
            String url = Loaders.changelogUrl(r.loaderName, r.loaderNew, r.mcNew);
            b.append("- **Mod loader** ").append(Loaders.describe(r))
                    .append(r.loaderSwitched() ? " (switched)" : r.loaderDowngraded() ? " (downgraded)" : "");
            if (url != null) b.append(" · [changelog](").append(url).append(")");
            b.append("\n");
            top = true;
        }
        if (top) b.append("\n");
        b.append("## Mods\n\n");
        mods(b, "Added", r.modsAdded, false);
        mods(b, "Updated", r.modsUpdated, true);
        mods(b, "Downgraded", r.modsDowngraded, true);
        mods(b, "Removed", r.modsRemoved, false);
        if (r.modsAdded.isEmpty() && r.modsUpdated.isEmpty() && r.modsDowngraded.isEmpty() && r.modsRemoved.isEmpty()) {
            b.append("No mod changes.\n\n");
        }

        if (!r.itemsAdded.isEmpty() || !r.itemsRemoved.isEmpty()) {
            b.append("## Items\n\n");
            named(b, "New items", r.itemsAdded);
            if (!r.itemsRemoved.isEmpty()) b.append("> Removed items disappear from inventories, chests and the world.\n\n");
            named(b, "Removed items", r.itemsRemoved);
        }
        if (!r.entitiesAdded.isEmpty() || !r.entitiesRemoved.isEmpty()) {
            b.append("## Mobs & entities\n\n");
            named(b, "New", r.entitiesAdded);
            named(b, "Removed", r.entitiesRemoved);
        }
        if (!r.configs.isEmpty()) configs(b, r.configs);
        return b.toString();
    }

    private static void configs(StringBuilder b, List<ConfigChange> list) {
        b.append("## Config changes\n\n");
        String[] titles = {"Changed", "Added", "Removed"};
        int[] kinds = {ConfigChange.CHANGED, ConfigChange.ADDED, ConfigChange.REMOVED};
        for (int i = 0; i < 3; i++) {
            List<ConfigChange> part = new ArrayList<>();
            for (ConfigChange c : list) if (c.kind == kinds[i]) part.add(c);
            if (part.isEmpty()) continue;
            b.append("**").append(titles[i]).append(" (").append(part.size()).append(")**\n\n");
            String currentMod = null;
            for (ConfigChange c : part) {
                String mod = nz(c.mod);
                if (!mod.equals(currentMod)) {
                    if (currentMod != null) b.append("\n");
                    currentMod = mod;
                    b.append("*").append(mod.isEmpty() ? "Other" : txt(mod)).append("*\n\n");
                }
                b.append("- `").append(c.path).append("`");
                if (c.kind == ConfigChange.CHANGED && c.keys.isEmpty()) b.append(" (content changed)");
                b.append("\n");
                for (KeyChange k : c.keys) {
                    b.append("  - `").append(k.key).append("`: ").append(code(k.oldValue)).append(" → ").append(code(k.newValue)).append("\n");
                }
                if (c.moreKeys > 0) b.append("  - … and ").append(c.moreKeys).append(" more\n");
            }
            b.append("\n");
        }
    }

    /** Names without § formatting codes. */
    private static String txt(String s) {
        return s == null ? "" : s.replaceAll("§.?", "");
    }

    /** A config value as inline code; "-" when the key did not exist. */
    private static String code(String v) {
        if (v == null) return "-";
        String s = v.replace("`", "'").replace('§', '&');
        return s.isEmpty() ? "`\"\"`" : "`" + s + "`";
    }

    public static String world(WorldReport r, Set<String> newItemIds, boolean hideRecipesForNewItems) {
        StringBuilder b = new StringBuilder();
        b.append("## World: ").append(r.world == null ? "?" : r.world).append("\n\n");
        b.append("Changes since ").append(WorldTracker.date(r.previous)).append(": ")
                .append(WorldTracker.summary(r)).append(".\n\n");
        if (r.recipesUncomparable) {
            b.append("> Game version changed (").append(r.mcOld).append(" → ").append(r.mcNew)
                    .append("), so changed recipes could not be compared this time.\n\n");
        }
        List<RecipeChange> added = r.recipesAdded;
        int hidden = 0;
        if (hideRecipesForNewItems && newItemIds != null && !newItemIds.isEmpty()) {
            added = new ArrayList<>();
            for (RecipeChange c : r.recipesAdded) {
                if (c.out != null && newItemIds.contains(c.out)) hidden++;
                else added.add(c);
            }
        }
        recipes(b, "Changed recipes", r.recipesChanged);
        recipes(b, "New recipes", added);
        if (hidden > 0) b.append("_+ ").append(hidden).append(" new recipes for the new items listed above._\n\n");
        recipes(b, "Removed recipes", r.recipesRemoved);
        ids(b, "New biomes", r.biomesAdded, "biome");
        ids(b, "Removed biomes", r.biomesRemoved, "biome");
        ids(b, "New structures", r.structuresAdded, null);
        ids(b, "Removed structures", r.structuresRemoved, null);
        ids(b, "New enchantments", r.enchantmentsAdded, "enchantment");
        ids(b, "Removed enchantments", r.enchantmentsRemoved, "enchantment");
        ids(b, "New dimensions", r.dimensionsAdded, null);
        ids(b, "Removed dimensions", r.dimensionsRemoved, null);
        if (!r.biomesAdded.isEmpty() || !r.structuresAdded.isEmpty()) {
            b.append("> New biomes and structures only generate in chunks you haven't explored yet.\n\n");
        }
        return b.toString();
    }

    private static void mods(StringBuilder b, String title, List<ModChange> list, boolean versions) {
        if (list.isEmpty()) return;
        b.append("**").append(title).append(" (").append(list.size()).append(")**\n\n");
        for (ModChange m : list) {
            b.append("- ");
            if (m.url != null) b.append("[").append(txt(m.name)).append("](").append(m.url).append(")");
            else b.append(txt(m.name));
            if (versions) b.append(" ").append(nz(m.oldVersion)).append(" → ").append(nz(m.newVersion));
            else b.append(" ").append(nz(m.newVersion != null ? m.newVersion : m.oldVersion));
            b.append("\n");
        }
        b.append("\n");
    }

    private static void named(StringBuilder b, String title, List<Named> list) {
        if (list.isEmpty()) return;
        b.append("**").append(title).append(" (").append(list.size()).append(")**\n\n");
        Map<String, Integer> perMod = new LinkedHashMap<>();
        for (Named n : list) perMod.merge(n.mod, 1, Integer::sum);
        String currentMod = null;
        for (Named n : list) {
            if (!n.mod.equals(currentMod)) {
                if (currentMod != null) b.append("\n");
                currentMod = n.mod;
                b.append("*").append(txt(currentMod)).append(" (").append(perMod.get(currentMod)).append(")*\n\n");
            }
            b.append("- ").append(txt(n.name)).append(" `").append(n.id).append("`\n");
        }
        b.append("\n");
    }

    private static void recipes(StringBuilder b, String title, List<RecipeChange> list) {
        if (list.isEmpty()) return;
        b.append("**").append(title).append(" (").append(list.size()).append(")**\n\n");
        Map<String, Integer> perMod = new LinkedHashMap<>();
        for (RecipeChange c : list) perMod.merge(nz(c.mod), 1, Integer::sum);
        String currentMod = null;
        for (RecipeChange c : list) {
            String mod = nz(c.mod);
            if (!mod.equals(currentMod)) {
                if (currentMod != null) b.append("\n");
                currentMod = mod;
                b.append("*").append(mod.isEmpty() ? "?" : mod).append(" (").append(perMod.get(mod)).append(")*\n\n");
            }
            b.append("- ").append(c.outName).append(" `").append(c.id).append("`");
            if (c.crafted) b.append(" ★ crafted before");
            b.append("\n");
        }
        b.append("\n");
    }

    private static void ids(StringBuilder b, String title, List<String> list, String langPrefix) {
        if (list.isEmpty()) return;
        b.append("**").append(title).append(" (").append(list.size()).append(")**\n\n");
        for (String id : list) {
            b.append("- ").append(Names.translatedOrPretty(langPrefix, id)).append(" `").append(id).append("`\n");
        }
        b.append("\n");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
