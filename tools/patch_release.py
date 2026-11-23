#!/usr/bin/env python3
"""patch_release.py OLD_DIR OLD_VERSION NEW_VERSION OUT_DIR : a patch release without the full build.
Copies every whatchanged-*-OLD_VERSION.jar from OLD_DIR to OUT_DIR as *-NEW_VERSION.jar, with
  - com/pokato/whatchanged/core/VersionCompare.class recompiled from common/ for the class version each JAR uses
    (Java 17 / 21 / 25, JDKs from $JAVA21_HOME and $JAVA25_HOME), and
  - the version in fabric.mod.json / mods.toml / neoforge.mods.toml set to NEW_VERSION.
Every other entry stays byte-identical. Only use it when VersionCompare (pure Java, no Minecraft classes) is the only
changed class – anything else needs build-all.sh. Used for 1.3.1 (1.3.0 JARs from CurseForge, file ids 9336789-9336800)."""
import os
import re
import subprocess
import sys
import tempfile
import zipfile

OLD_DIR, OLD_V, NEW_V, OUT = sys.argv[1:5]
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(HERE, 'common/src/main/java/com/pokato/whatchanged/core/VersionCompare.java')
ENTRY = 'com/pokato/whatchanged/core/VersionCompare.class'
JDK = {17: os.environ.get('JAVA21_HOME', ''), 21: os.environ.get('JAVA21_HOME', ''), 25: os.environ.get('JAVA25_HOME', '')}
MAJOR = {61: 17, 65: 21, 69: 25}
os.makedirs(OUT, exist_ok=True)
tmp = tempfile.mkdtemp()
compiled = {}


def compiled_class(release):
    if release not in compiled:
        d = os.path.join(tmp, str(release))
        javac = os.path.join(JDK[release], 'bin', 'javac') if JDK[release] else 'javac'
        subprocess.run([javac, '--release', str(release), '-encoding', 'UTF-8', '-d', d, SRC], check=True)
        extra = [f for _, _, fs in os.walk(d) for f in fs if f != 'VersionCompare.class']
        if extra:
            raise SystemExit('VersionCompare now compiles to more than one class %s – add them or use build-all.sh' % extra)
        compiled[release] = open(os.path.join(d, ENTRY), 'rb').read()
    return compiled[release]


for fn in sorted(os.listdir(OLD_DIR)):
    if not (fn.startswith('whatchanged-') and fn.endswith('-%s.jar' % OLD_V)):
        continue
    zi = zipfile.ZipFile(os.path.join(OLD_DIR, fn))
    major = int.from_bytes(zi.read(ENTRY)[6:8], 'big')
    new_cls = compiled_class(MAJOR[major])
    dst = os.path.join(OUT, fn[:-len(OLD_V) - 4] + NEW_V + '.jar')
    with zipfile.ZipFile(dst, 'w') as zo:
        for info in zi.infolist():
            data = zi.read(info.filename)
            if info.filename == ENTRY:
                data = new_cls
            elif info.filename in ('fabric.mod.json', 'META-INF/mods.toml', 'META-INF/neoforge.mods.toml'):
                t = data.decode('utf-8')
                q = re.escape(OLD_V)
                t2 = re.sub(r'("version"\s*:\s*")' + q + '"', r'\g<1>' + NEW_V + '"', t)
                t2 = re.sub(r'(?m)^(version\s*=\s*")' + q + '"', r'\g<1>' + NEW_V + '"', t2)
                if t2 == t:
                    raise SystemExit('no version %s in %s/%s' % (OLD_V, fn, info.filename))
                data = t2.encode('utf-8')
            zo.writestr(info, data, compress_type=info.compress_type)
    print('%s (Java %d class)' % (os.path.basename(dst), MAJOR[major]))
