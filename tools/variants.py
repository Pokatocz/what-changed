#!/usr/bin/env python3
"""variants.py FORGE_1.20_JAR : the Forge 1.20 - 1.20.4 JAR also serves NeoForge 1.20.1 (a fork of Forge 47).
Adds META-INF/whatchanged-variant.txt to the Forge JAR and writes the NeoForge 1.20.1 copy next to it with its own
variant text, so the two release files differ (CurseForge and Modrinth refuse duplicate files)."""
import os
import sys
import zipfile

src = sys.argv[1]
name = os.path.basename(src)
NAME = 'META-INF/whatchanged-variant.txt'
VARIANTS = {
    name: 'Forge 1.20 - 1.20.4\n',
    name.replace('whatchanged-forge-1.20-1.20.4-', 'whatchanged-neoforge-1.20.1-'): 'NeoForge 1.20.1 (Forge 47 fork) - same code as the Forge 1.20 - 1.20.4 file\n',
}
with zipfile.ZipFile(src) as zi:
    entries = [(i, zi.read(i.filename)) for i in zi.infolist() if i.filename != NAME]
for out, text in VARIANTS.items():
    dst = os.path.join(os.path.dirname(src), out)
    with zipfile.ZipFile(dst + '.tmp', 'w') as zo:
        for info, data in entries:
            zo.writestr(info, data, compress_type=info.compress_type)
        zo.writestr(zipfile.ZipInfo(NAME, date_time=entries[-1][0].date_time), text, compress_type=zipfile.ZIP_STORED)
    os.replace(dst + '.tmp', dst)
    print(out)
