**Name:** What Changed? - Modpack Update Changelog
**Slug:** what-changed
**Summary:** Automatic modpack changelog: after every update see exactly which mods, mod loader, configs, items, mobs, recipes, biomes and structures were added, removed or changed.
**Categories:** Utility & QoL, Map and Information
**Loaders / versions (9 files of the same release):**
- `whatchanged-fabric-1.20-1.21.7-1.2.0.jar` → Fabric · 1.20 – 1.21.7 · requires Fabric API
- `whatchanged-fabric-1.21.8-1.21.11-1.2.0.jar` → Fabric · 1.21.8 – 1.21.11 · requires Fabric API
- `whatchanged-fabric-26.1-26.3-1.2.0.jar` → Fabric · 26.1 – 26.3 · requires Fabric API
- `whatchanged-forge-1.20-1.20.4-1.2.0.jar` → Forge · 1.20 – 1.20.4
- `whatchanged-forge-1.20.6-1.21.7-1.2.0.jar` → Forge · 1.20.6 – 1.21.7
- `whatchanged-forge-1.21.8-26.3-1.2.0.jar` → Forge · 1.21.8 – 26.3
- `whatchanged-neoforge-1.20.1-1.2.0.jar` → NeoForge · 1.20.1 (the Forge 1.20 – 1.20.4 file; NeoForge 1.20.1 is a Forge fork)
- `whatchanged-neoforge-1.20.2-1.21.7-1.2.0.jar` → NeoForge · 1.20.2 – 1.21.7
- `whatchanged-neoforge-1.21.8-26.3-1.2.0.jar` → NeoForge · 1.21.8 – 26.3
**Environment:** Client and server (works client-only; install on the server too for server-side recipe/worldgen tracking)

---

# What Changed?

**The automatic changelog for every modpack update.**

Your modpack just updated and you have no idea what's new? **What Changed?** remembers your modpack between launches and shows you exactly what changed. No changelog to write, no setup - it works with any mods.

## What it detects

- **Mods** - added, removed, updated and **downgraded** (old → new version)
- **Mod loader & Minecraft** - Fabric Loader / Forge / NeoForge and Minecraft version changes (updated, downgraded, or a switch such as Forge → NeoForge), with a link to the loader's official changelog
- **Config files** - which mods' configs were added, removed or changed, and exactly which values changed (`spawnRate: 12 → 20`). Comments and formatting are ignored, tokens and passwords are never shown
- **Items** - every new item and every removed item. Removed items vanish from chests, so you know before you lose them
- **Mobs & entities** - new and removed
- **Recipes** (per world) - new, changed and removed recipes, including modded and datapack recipes
- **Your crafts** - changed or removed recipes for items you have crafted before are marked with ★
- **Biomes, structures, enchantments & dimensions** (per world) - new and removed

## How it works

1. Install it and play - the first launch quietly saves a snapshot.
2. Update your modpack.
3. On the next launch a **"What changed?"** button and a toast appear on the title screen, and you get a one-line summary in chat when you join a world (click it to open the details).
4. Press **J** any time (or type `/whatchanged`) to open the full, searchable list with tabs: **Mods**, **Items**, **Mobs**, **Configs**, **This world**.

Too many notifications? The **Notify** button in the screen switches between **All** (toast, chat and title button), **Quiet** (title button only) and **Off** (just J / `/whatchanged`).

Several updates before you look? They add up - the list always shows everything since you last looked.

## For modpack makers

Click **Export changelog**: a ready-to-paste Markdown changelog (with links to the updated mods' pages, the loader changelog and every changed config value) is copied to your clipboard and saved to `config/whatchanged/changelogs/`. Paste it on your CurseForge or Modrinth page, or on Discord. Add What Changed? to your pack and your players always know what an update brought.

## Servers

Install it on the server too and it tracks the server's recipes, biomes, structures, enchantments and dimensions. Players get a short summary in chat when they join, a Markdown changelog is written to the server's `config/whatchanged/changelogs/`, and ops can use `/whatchanged`, `/whatchanged export` and `/whatchanged reset`.

## Loaders & versions

Fabric, NeoForge and Forge, every Minecraft version from **1.20 to 26.3** (Forge 1.20 – 1.20.4 · 1.20.6 – 26.3, NeoForge 1.20.1 – 26.3). Fabric needs Fabric API. Download the file for your Minecraft version and loader - the CurseForge app picks it automatically.

## Details

- Works with any mods, zero configuration; fully translatable (lang file)
- Lightweight: one check per launch (typically well under a second), file writes happen in the background
- Everything is stored locally in `config/whatchanged/` and in each world's `whatchanged/` folder
- `/whatchanged reset` (client) forgets the history and starts from the current state
- Config: `config/whatchanged/config.json` - notifications (also switchable in the screen), title button position, version-only mod updates, hiding recipes of new items, config tracking (`trackConfigs`, `configIgnore` globs, `configIgnoreKeys`, `notifyConfigChanges`)
- Changes that are only in config files don't pop up a notification by default (many mods rewrite their configs while you play) - they wait in the Configs tab. Modpack makers who ship config-only updates can set `notifyConfigChanges` to `true`
- Keybind **J** (change it in Controls → What Changed?). The title-screen button, the chat link and `/whatchanged` work without the key.
