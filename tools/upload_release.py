#!/usr/bin/env python3
"""Upload all nine JARs of a What Changed? release to CurseForge project 1739456.
usage: upload_release.py TOKEN DIR VERSION [only,these,files]
Game version ids are looked up by name through the CurseForge upload API (/api/game/versions)."""
import json
import subprocess
import sys

TOKEN, DIR, V = sys.argv[1], sys.argv[2], sys.argv[3]
ONLY = set(sys.argv[4].split(',')) if len(sys.argv) > 4 else None
PID = 1739456
API = 'https://minecraft.curseforge.com/api'


def get(path):
    r = subprocess.run(['curl', '-sS', '-m', '60', '-H', 'X-Api-Token: ' + TOKEN, API + path], capture_output=True, text=True)
    return json.loads(r.stdout)


types = {t['id']: t['slug'] for t in get('/game/version-types')}
versions = get('/game/versions')


def vid(name, kind):
    """kind: 'mc' (a Minecraft release), 'loader', 'java', 'env'."""
    for v in versions:
        slug = types.get(v['gameVersionTypeID'], '')
        ok = {'mc': slug.startswith('minecraft-'), 'loader': slug == 'modloader', 'java': slug == 'java',
              'env': slug == 'environment'}[kind]
        if ok and v['name'] == name:
            return v['id']
    raise SystemExit(f'no CurseForge game version {kind} {name!r}')


def mc(*names):
    return [vid(n, 'mc') for n in names]


LEGACY = ['1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6', '1.21', '1.21.1', '1.21.2', '1.21.3',
          '1.21.4', '1.21.5', '1.21.6', '1.21.7']
MODERN = ['1.21.8', '1.21.9', '1.21.10', '1.21.11']
V26 = ['26.1', '26.1.1', '26.1.2', '26.2', '26.3']
J17, J21, J25 = 'Java 17', 'Java 21', 'Java 25'


def extra(loader, *java):
    return [vid(loader, 'loader')] + [vid(j, 'java') for j in java] + [vid('Client', 'env'), vid('Server', 'env')]


FAPI = {"projects": [{"slug": "fabric-api", "type": "requiredDependency"}]}
FILES = [
    (f'whatchanged-fabric-1.20-1.21.7-{V}.jar', 'Fabric 1.20-1.21.7', mc(*LEGACY) + extra('Fabric', J17, J21), FAPI),
    (f'whatchanged-fabric-1.21.8-1.21.11-{V}.jar', 'Fabric 1.21.8-1.21.11', mc(*MODERN) + extra('Fabric', J21), FAPI),
    (f'whatchanged-fabric-26.1-26.3-{V}.jar', 'Fabric 26.1-26.3', mc(*V26) + extra('Fabric', J25), FAPI),
    (f'whatchanged-forge-1.20-1.20.4-{V}.jar', 'Forge 1.20-1.20.4', mc(*LEGACY[:5]) + extra('Forge', J17), None),
    (f'whatchanged-neoforge-1.20.1-{V}.jar', 'NeoForge 1.20.1', mc('1.20.1') + extra('NeoForge', J17), None),
    (f'whatchanged-forge-1.20.6-1.21.7-{V}.jar', 'Forge 1.20.6-1.21.7',
     mc('1.20.6', '1.21', '1.21.1', '1.21.3', '1.21.4', '1.21.5', '1.21.6', '1.21.7') + extra('Forge', J21), None),
    (f'whatchanged-forge-1.21.8-26.3-{V}.jar', 'Forge 1.21.8-26.3', mc(*MODERN, *V26) + extra('Forge', J21, J25), None),
    (f'whatchanged-neoforge-1.20.2-1.21.7-{V}.jar', 'NeoForge 1.20.2-1.21.7', mc(*LEGACY[2:]) + extra('NeoForge', J17, J21), None),
    (f'whatchanged-neoforge-1.21.8-26.3-{V}.jar', 'NeoForge 1.21.8-26.3', mc(*MODERN, *V26) + extra('NeoForge', J21, J25), None),
]
CL = open(DIR + '/changelog.md').read()
for fn, label, gv, rel in FILES:
    if ONLY and fn not in ONLY:
        continue
    meta = {"changelog": CL, "changelogType": "markdown", "displayName": f'What Changed? {V} ({label})',
            "gameVersions": gv, "releaseType": "release"}
    if rel:
        meta["relations"] = rel
    r = subprocess.run(["curl", "-sS", "-m", "120", "-H", "X-Api-Token: " + TOKEN, "-F", "metadata=" + json.dumps(meta),
                        "-F", "file=@" + DIR + "/" + fn, f"{API}/projects/{PID}/upload-file", "-w", " HTTP%{http_code}"],
                       capture_output=True, text=True)
    print(fn, r.stdout, r.stderr)
