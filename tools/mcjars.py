#!/usr/bin/env python3
"""mcjars.py VERSION... : prepares the per-version Minecraft jars that tools/build-shims.sh compiles against.

For each version, in $MCJARS (default ~/mcjars):
  named-<v>.jar         the client jar in Mojang names (26.x: the unobfuscated client jar as is)
  libsall-<v>.txt       ':'-separated library jars (downloaded to $MCJARS/libraries)
  maps/mi-<v>.tiny      official / named / intermediary (Fabric)            - obfuscated versions only
  maps/ms-<v>.tiny      official / named / srg (Forge MCPConfig, 1.20-1.20.4) - with --srg
  tiny-remapper.jar
Also writes tools/client26-cp.txt (the classpath tools/build-client26.sh uses) when 26.1 is in the list.

usage: mcjars.py [--srg 1.20.1,1.20.2,1.20.3] 1.20.1 1.20.2 1.20.3 1.20.5 1.21.2 1.21.5 1.21.6 26.1
"""
import json
import os
import re
import subprocess
import sys
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
MC = os.environ.get('MCJARS', os.path.expanduser('~/mcjars'))
JAVA = os.path.join(os.environ.get('JDK21', '/usr/lib/jvm/java-21-openjdk-amd64'), 'bin', 'java')
UA = {'User-Agent': 'Mozilla/5.0'}
TINY_REMAPPER = 'https://maven.fabricmc.net/net/fabricmc/tiny-remapper/0.11.1/tiny-remapper-0.11.1-fat.jar'


def fetch(url, dst):
    if os.path.exists(dst) and os.path.getsize(dst) > 0:
        return dst
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r, open(dst + '.part', 'wb') as f:
        f.write(r.read())
    os.replace(dst + '.part', dst)
    return dst


def text(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return r.read().decode('utf-8')


def remap(src, dst, mappings, frm, to, cp):
    subprocess.run([JAVA, '-jar', f'{MC}/tiny-remapper.jar', src, dst, mappings, frm, to] + cp,
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def mcp_config(v):
    meta = text('https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/maven-metadata.xml')
    found = [x for x in re.findall(r'<version>([^<]+)</version>', meta) if x.startswith(v + '-')]
    if not found:
        raise SystemExit(f'no MCPConfig for {v}')
    mv = found[-1]
    z = fetch(f'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/{mv}/mcp_config-{mv}.zip', f'{MC}/raw/mcp_config-{mv}.zip')
    dst = f'{MC}/raw/joined-{v}.tsrg'
    with zipfile.ZipFile(z) as zf, open(dst, 'wb') as f:
        f.write(zf.read('config/joined.tsrg'))
    return dst


def prepare(v, srg):
    manifest = json.loads(text('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
    entry = next(e for e in manifest['versions'] if e['id'] == v)
    vj = json.loads(text(entry['url']))
    client = fetch(vj['downloads']['client']['url'], f'{MC}/raw/client-{v}.jar')
    libs = []
    for lib in vj['libraries']:
        a = lib.get('downloads', {}).get('artifact')
        if a and 'natives' not in a['path']:
            libs.append(fetch(a['url'], f"{MC}/libraries/{a['path']}"))
    open(f'{MC}/libsall-{v}.txt', 'w').write(':'.join(libs))
    if 'client_mappings' not in vj['downloads']:            # 26.x: shipped unobfuscated
        if not os.path.exists(f'{MC}/named-{v}.jar'):
            os.link(client, f'{MC}/named-{v}.jar')
        return libs
    pg = fetch(vj['downloads']['client_mappings']['url'], f'{MC}/raw/client-{v}.txt')
    moj = f'{MC}/maps/moj-{v}.tiny'
    os.makedirs(f'{MC}/maps', exist_ok=True)
    if not os.path.exists(moj):
        subprocess.run([sys.executable, f'{HERE}/ingame/pg2tiny.py', pg, moj], check=True)
    if not os.path.exists(f'{MC}/named-{v}.jar'):
        remap(client, f'{MC}/named-{v}.jar', moj, 'official', 'named', libs)
    ij = fetch(f'https://maven.fabricmc.net/net/fabricmc/intermediary/{v}/intermediary-{v}-v2.jar', f'{MC}/raw/intermediary-{v}-v2.jar')
    it = f'{MC}/raw/intermediary-{v}.tiny'
    with zipfile.ZipFile(ij) as zf, open(it, 'wb') as f:
        f.write(zf.read('mappings/mappings.tiny'))
    if not os.path.exists(f'{MC}/maps/mi-{v}.tiny'):
        subprocess.run([sys.executable, f'{HERE}/mergemaps.py', moj, it, 'int', f'{MC}/maps/mi-{v}.tiny'], check=True)
    if srg and not os.path.exists(f'{MC}/maps/ms-{v}.tiny'):
        subprocess.run([sys.executable, f'{HERE}/mergemaps.py', moj, mcp_config(v), 'srg', f'{MC}/maps/ms-{v}.tiny'], check=True)
    return libs


def main():
    args = sys.argv[1:]
    srg = set()
    if args[:1] == ['--srg']:
        srg = set(args[1].split(','))
        args = args[2:]
    os.makedirs(MC, exist_ok=True)
    fetch(TINY_REMAPPER, f'{MC}/tiny-remapper.jar')
    for v in args:
        libs = prepare(v, v in srg)
        if v == '26.1':
            open(f'{HERE}/client26-cp.txt', 'w').write(':'.join([f'{MC}/named-{v}.jar'] + libs))
        print(f'{v}: ok' + (' (+srg)' if v in srg else ''))


if __name__ == '__main__':
    main()
