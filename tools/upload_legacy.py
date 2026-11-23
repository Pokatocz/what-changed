#!/usr/bin/env python3
"""Upload the 1.20 - 1.21.7 JARs of What Changed? 1.1.0 to CurseForge project 1739456.
usage: upload_legacy.py TOKEN DIR [only,these,files]"""
import json
import subprocess
import sys

TOKEN, DIR = sys.argv[1], sys.argv[2]
ONLY = set(sys.argv[3].split(',')) if len(sys.argv) > 3 else None
PID = 1739456
MC = {'1.20': 9971, '1.20.1': 9990, '1.20.2': 10236, '1.20.3': 10395, '1.20.4': 10407, '1.20.5': 11163, '1.20.6': 11198,
      '1.21': 11457, '1.21.1': 11779, '1.21.2': 12079, '1.21.3': 12084, '1.21.4': 12281, '1.21.5': 12934,
      '1.21.6': 13422, '1.21.7': 13506}
FORGE, FABRIC, NEOFORGE = 7498, 7499, 10150
JAVA17, JAVA21 = 8326, 11135
CLIENT, SERVER = 9638, 9639
ENV = [CLIENT, SERVER]


def mc(*names):
    return [MC[n] for n in names]


ALL = list(MC)
CL = """What Changed? 1.1.0 for Minecraft 1.20 - 1.21.7 (same features as the 1.21.8+ files).

- Automatic modpack changelog: mods, items, mobs, recipes, biomes, structures, enchantments and dimensions that were added, removed or changed
- Searchable screen (key J or /whatchanged), title-screen button, toast and chat summary
- Export changelog as Markdown for modpack pages
- Server support: chat summary for players, server-side changelog, /whatchanged commands for ops"""
FAPI = {"projects": [{"slug": "fabric-api", "type": "requiredDependency"}]}
FILES = [
    ('whatchanged-fabric-1.20-1.21.7-1.1.0.jar', 'What Changed? 1.1.0 (Fabric 1.20-1.21.7)',
     mc(*ALL) + [FABRIC, JAVA17, JAVA21] + ENV, FAPI),
    ('whatchanged-forge-1.20-1.20.4-1.1.0.jar', 'What Changed? 1.1.0 (Forge 1.20-1.20.4)',
     mc('1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4') + [FORGE, JAVA17] + ENV, None),
    # NeoForge for 1.20.1 is a Forge 47 fork and runs the Forge file - separate copy so it is only offered for 1.20.1
    ('whatchanged-neoforge-1.20.1-1.1.0.jar', 'What Changed? 1.1.0 (NeoForge 1.20.1)', mc('1.20.1') + [NEOFORGE, JAVA17] + ENV, None),
    ('whatchanged-forge-1.20.6-1.21.7-1.1.0.jar', 'What Changed? 1.1.0 (Forge 1.20.6-1.21.7)',
     mc('1.20.6', '1.21', '1.21.1', '1.21.3', '1.21.4', '1.21.5', '1.21.6', '1.21.7') + [FORGE, JAVA21] + ENV, None),
    ('whatchanged-neoforge-1.20.2-1.21.7-1.1.0.jar', 'What Changed? 1.1.0 (NeoForge 1.20.2-1.21.7)',
     mc(*ALL[2:]) + [NEOFORGE, JAVA17, JAVA21] + ENV, None),
]
for fn, name, gv, rel in FILES:
    if ONLY and fn not in ONLY:
        continue
    meta = {"changelog": CL, "changelogType": "markdown", "displayName": name, "gameVersions": gv, "releaseType": "release"}
    if rel:
        meta["relations"] = rel
    r = subprocess.run(["curl", "-sS", "-m", "120", "-H", "X-Api-Token: " + TOKEN, "-F", "metadata=" + json.dumps(meta),
                        "-F", "file=@" + DIR + "/" + fn, f"https://minecraft.curseforge.com/api/projects/{PID}/upload-file",
                        "-w", " HTTP%{http_code}"], capture_output=True, text=True)
    print(fn, r.stdout, r.stderr)
