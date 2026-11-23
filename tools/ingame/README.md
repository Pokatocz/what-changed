# In-game test of the release JARs (used for 1.3.1, 22 Nov 2026)

Tests the **real release JARs**: each test JAR is a release JAR plus the `selftest/` classes (the mod finds
`com.pokato.whatchanged.selftest.SelfTest` by reflection and runs it when `WCTEST_MODE` is set). Paths in the scripts
come from the environment (`WCT` work dir, `MCMAIN` portablemc main dir, `MCJARS` mapped Minecraft jars).

## Setup
1. JDK 17, 21, 25 (in `~/.pokato-tools/jdk-17`, `jdk-21`, `jdk-25`), `portablemc` (pip), Xvfb,
   ImageMagick `import`, `xdotool`.
2. Clients in one portablemc main dir (`$MCMAIN`): Fabric/NeoForge with `portablemc start <spec> --dry`;
   Forge with the official installer `--installClient <main dir>` (run the 1.20.1 and 1.21.1 installers with **Java 17**,
   newer JDKs write jars with other hashes and the installer fails).
3. Minecraft names for compiling the selftest: `pg2tiny.py client.txt moj.tiny` (Mojang ProGuard mappings → tiny),
   tiny-remapper `official → named`; for Fabric ≤ 1.21.11 merge with intermediary, for Forge 1.20.1 with MCPConfig
   `joined.tsrg` (`../mergemaps.py`). 26.x is not obfuscated.
4. Compile `common/` + `selftest/` against the named jar (`cp.py <version>` prints the library classpath), keep the
   `selftest` classes, remap them to the runtime names (intermediary for Fabric ≤ 1.21.11, SRG for Forge 1.20.1 and the
   NeoForge 1.20.1 fork, named for the rest) and `addst.py release.jar selftest.jar test.jar`.

## Run
`wct.sh <combo>` (combos: fab120 fab1218 fab263 forge120 forge1211 forge263 neo1201 neo1202 neo263):
dedicated server 2× (world snapshot faked older by `mutate.py world`), then the client 3×:
c1 first run + world, c2 after `mutate.py pack/world` (title button, toast, chat, every tab, search, notification
switch, export, world tab, `/whatchanged export`), c3 the visual test (long/unicode names, tooltips, toasts).
Test mods `1.0.0 → 1.0.0-hotfix`, `2.0.0 → 2.0.0-beta.1`, `1.5.0 → 1.5.0-ultimate` check the version comparison.
`dragtest.py <combo>` drags the scrollbar with the real mouse. Results: `logs/<combo>/summary.txt`, screenshots in
`logs/<combo>/out-c*/shots/`.
