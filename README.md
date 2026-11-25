# What Changed?

A Minecraft mod: the automatic changelog for every modpack update. It remembers your modpack between launches and
shows exactly which mods, mod loader, configs, items, mobs, recipes, biomes and structures were added, removed or
changed – no changelog to write, no setup, works with any mods.

**Download:** [CurseForge – What Changed?](https://www.curseforge.com/minecraft/mc-mods/what-changed-modpack-update-changelog)

- **Loaders:** Fabric (with Fabric API), Forge, NeoForge
- **Minecraft:** 1.20 – 26.3 (9 jars, see the table below)
- **Mod id:** `whatchanged`

## Features

- Mods added, removed, updated and downgraded (old → new version); mod loader and Minecraft changes with a link to the
  loader's changelog
- Config files: added, removed and changed files with every changed value (`spawnRate: 12 → 20`); secrets are never shown
- New and removed items and mobs
- Per world: new, changed and removed recipes (★ for items you crafted before), biomes, structures, enchantments,
  dimensions – also on dedicated servers
- Title screen button, toast and chat summary; **J** or `/whatchanged` opens a searchable screen with tabs
- **Export changelog**: a ready-to-paste Markdown changelog for modpack makers
- Config: `config/whatchanged/config.json` (notifications, what to track, key, button)

## Building

| JAR | Loader | Minecraft | Built against |
|---|---|---|---|
| `whatchanged-fabric-1.20-1.21.7-<v>.jar` | Fabric + Fabric API | 1.20 – 1.21.7 | 1.20.1 → intermediary (Loom) + version shims remapped to intermediary, Java 17 |
| `whatchanged-forge-1.20-1.20.4-<v>.jar` | Forge 46 – 49 | 1.20 – 1.20.4 | Forge 47 (1.20.1), reobfuscated to SRG + SRG shims, Java 17 |
| `whatchanged-forge-1.20.6-1.21.7-<v>.jar` | Forge 50 – 57 | 1.20.6 – 1.21.7 | Forge 50 (1.20.6) + shims, Java 21 |
| `whatchanged-neoforge-1.20.2-1.21.7-<v>.jar` | NeoForge 20.2 – 21.7 | 1.20.2 – 1.21.7 | NeoForge 20.4 + shims, Java 17 |
| `whatchanged-fabric-1.21.8-1.21.11-<v>.jar` | Fabric + Fabric API | 1.21.8 – 1.21.11 | 1.21.8, Mojang mappings → intermediary (Loom remap) |
| `whatchanged-fabric-26.1-26.3-<v>.jar` | Fabric + Fabric API | 26.1 – 26.3 | 26.1, unobfuscated (Loom non-remap), Java 25 |
| `whatchanged-neoforge-1.21.8-26.3-<v>.jar` | NeoForge 21.8 – 26.3 | 1.21.8 – 26.3 | NeoForge 21.8.54 (Java 21) + client26 layer |
| `whatchanged-forge-1.21.8-26.3-<v>.jar` | Forge 58 – 66 | 1.21.8 – 26.3 | Forge 58.1.22 (Java 21) + client26 layer |

(Forge 1.20.5 and 1.21.2 do not exist. NeoForge for 1.20.1 is a fork of Forge 47: the ninth JAR,
`whatchanged-neoforge-1.20.1-<v>.jar`, is a copy of the Forge 1.20 – 1.20.4 JAR; `tools/variants.py` gives both their own
`META-INF/whatchanged-variant.txt` so the two files differ.)

```
./build-all.sh                # all nine (needs JDK 17, 21, 25, JDK 8 for Forge runs, Gradle 8.14 + 9.5.1, and the
                              # per-version Minecraft jars + mappings in $MCJARS: tools/mcjars.py makes them)
./build-all.sh -Pselftest     # test builds with the dev-only in-game self-test (…-SELFTEST-….jar)
```

The build and test scripts were written for the author's own machine: JDK and Gradle paths are set at the top of
`build-all.sh` and in the `gradle.properties` files, mapped Minecraft jars are expected in `$MCJARS`. The JARs end up in
`loaders/<project>/build/libs/`.

`tools/build-client26.sh` compiles `shared/client26/` against Minecraft 26.1 at **Java 21 bytecode**, so the single
Forge/NeoForge JAR stays loadable on 1.21.x while carrying the 26.x renderer layer.

## Layout

```
loaders/   the eight Gradle builds, one per JAR (thin loader entry points)
shared/    code used by several JARs: common logic, version shims, 1.21 / 26.x client layers
tools/     build helpers, version-compare test, in-game test harness and self-test, release scripts
docs/      CurseForge page text
build-all.sh
```

- `shared/common/` – all logic (trackers, diff, Markdown, commands, screen logic) in Mojang names, only APIs that are
  identical in 1.20 – 26.3 (it is compiled against every one of them). `platform/Platform` is the per-loader bridge.
- Version shims in `shared/`, one per API generation, picked at runtime by Minecraft version (`platform/Versions`):
  - `core/CoreCompat` (logical server): `shims/c120` 1.20 – 1.20.1, `c1202` 1.20.2 – 1.20.4, `c1205` 1.20.5 – 1.21.1,
    `c1212` 1.21.2 – 1.21.4, `c1215` 1.21.5 – 26.x – recipe list + serialization, click events, system messages.
  - `client/ClientCompat` (client): `shims/g120` 1.20 – 1.20.1, `g1202` 1.20.2, `g1203` 1.20.3 – 1.21.1,
    `g1212` 1.21.2 – 1.21.5 (all three share `gscreen`), `client121/` 1.21.6 – 1.21.11, `client26/` 26.x.
- `tools/build-shims.sh` compiles each shim against its own Minecraft version and remaps it to intermediary (Fabric)
  and SRG (Forge 1.20 – 1.20.4); `tools/build-client26.sh` does the same for the 26.x layer.
- Loader entry points: `shared/fabric-common/`, `loaders/fabric121/` (also used by the 1.20 build), `loaders/fabric26/`,
  `shared/forge-legacy/` (Forge 46 – 57, EventBus 6 and 7), `loaders/forge/`, `loaders/neoforge120/`,
  `loaders/neoforge/`. `fabric120`, `forge120` and `forge1206` only build (they reuse those entry points).
- `core/ConfigTracker` (1.2.0) – snapshot of `config/` (TOML, JSON/JSON5, properties/INI/old Forge CFG, YAML parsed to
  flat `key = value`; other text files by normalized hash), diffed per value; secret-looking keys are stored hashed.
  `core/VersionCompare` decides update vs downgrade, `core/Loaders` maps the loader to its mod id and changelog URL.
- `tools/selftest/` – dev-only automated in-game test, only compiled with `-Pselftest`.
- `tools/` also has the mapping merge, the Gradle retry wrapper, reference verification (`verify.sh`, `refcheck.py`,
  `verify_legacy.py`), the version-compare test (`test-versions.sh`), the in-game test harness (`ingame/`), `mcjars.py`
  (downloads and maps the Minecraft jars the shims compile against) and `variants.py` (the NeoForge 1.20.1 copy).

## Version differences handled

| Change | Since | Handling |
|---|---|---|
| Recipes carry their id → `RecipeHolder` (ResourceLocation id) → ResourceKey id; no recipe codec before 1.20.3 | 1.20.2 / 1.21.2 | core shims; ≤ 1.20.4 hashes the network serialization |
| `registryOrThrow` → `lookupOrThrow`; `Holder.getRegisteredName()` only from 1.20.5 | 1.21.2 | `registries()` + `registryKeySet()`, `builtInRegistryHolder().key()` (stable 1.20 – 26.x) |
| `ClickEvent(Action, String)` → `ClickEvent.RunCommand` | 1.21.5 | core shims |
| `ServerPlayer.sendSystemMessage` no longer inherited from `CommandSource` (new intermediary name) | 1.21.2 | core shims |
| Screen background not drawn by `Screen.render`; `mouseScrolled` 3 → 4 args | 1.20.2 | client shims |
| `SystemToastIds` → `SystemToastId`; `ToastComponent` → `ToastManager` | 1.20.3 / 1.21.2 | client shims |
| Immediate → deferred GUI rendering (`renderComponentTooltip` → `setComponentTooltipForNextFrame`) | 1.21.6 | client shims |
| Forge runtime names: SRG → Mojang | Forge 50 (1.20.6) | separate SRG JAR for 1.20 – 1.20.4 |
| Forge EventBus 6 → 7 (static `BUS`, `getBus(BusGroup)`); `TickEvent.ClientTickEvent` → `.Post` | Forge 56 (1.21.6) | `forge-legacy/ForgeBus` |
| NeoForge `TickEvent.ClientTickEvent` → `ClientTickEvent.Post`; `mods.toml` → `neoforge.mods.toml` | NeoForge 20.5 | reflection; both toml files shipped |
| Screen rendering `GuiGraphics` → `GuiGraphicsExtractor` | 26.1 | two thin screen subclasses + `Painter` |
| `Minecraft.setScreen/screen/getToastManager` → `Gui.setScreen/screen()/toastManager()` | 26.2 | reflection in `Client26Compat` |
| `ChatComponent.addMessage` → `addClientSystemMessage`; chat moved to `Gui.hud` | 26.1 / 26.2 | `Client121Compat` / reflection |
| `KeyMapping` category `String` → `Category`; `ResourceLocation` → `Identifier`; key codes renumbered | 1.21.9 / 1.21.11 / 26.3 | `KeyCompat` (signature-based, key by name `key.keyboard.j`) |
| `ResourceLocation` → `Identifier` everywhere | 1.21.11 | ids via `Holder.getRegisteredName()` / `ResourceKey.toString()` |
| `Recipe.CODEC` became a holder codec, `DIRECT_CODEC` added | 26.3 | reflection, falls back to `CODEC` |
| Item components unbound outside a world | 26.3 | names via description id, icons only when available |
| authlib `GameProfile.getName()` → `name()` | 1.21.9 | reflection |
| Forge mod-bus events: `getBus(BusGroup)` → static `BUS` | Forge 59 | reflection |
| Forge `ModList.get().getMods()` → static `ModList.getMods()` | Forge 62 (26.1) | reflection in `ForgePlatform` |
| Probing client classes on a Forge dedicated server aborts mod loading | all | `FMLEnvironment.dist` |
| Fabric API `KeyBindingHelper`/`ClientCommandManager`/`Screens.getButtons` → `KeyMappingHelper`/`ClientCommands`/`Screens.getWidgets` | 26.1 | separate fabric26 entry point |

## Verified

- `tools/verify_legacy.py` + `tools/verify.sh`: every Minecraft/Mojang, Fabric API, NeoForge and Forge reference of each JAR
  resolved against every target version in the names that version runs with (intermediary / SRG / Mojang),
  each shim only on the versions it serves; plus overrides (a `render`/`mouseScrolled` that stops overriding) and
  lambdas implementing game or loader callbacks (name and signature).
- Automated in-game runs: development runs for 1.21.8 – 26.3, production runs of the release JARs (real launcher client + real dedicated
  server, `tools/ingame/`) for every JAR.

## License

[MIT](LICENSE).
