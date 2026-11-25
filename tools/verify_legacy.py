#!/usr/bin/env python3
"""Resolve every Minecraft reference of a 1.20 - 1.21.7 JAR against each target version, checking each version
shim only on the versions where it is selected (see CoreCompat / ClientCompat).

usage: verify_legacy.py JAR NAMES(int|srg|named) VERSION...
"""
import os
import subprocess
import sys

MC = os.environ.get('MCJARS', os.path.expanduser('~/mcjars'))
ORDER = ['1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6', '1.21', '1.21.1', '1.21.2', '1.21.3',
         '1.21.4', '1.21.5', '1.21.6', '1.21.7']
# shim package -> [first, last] version where it is used
RANGES = {
    'c120': ('1.20', '1.20.1'), 'c1202': ('1.20.2', '1.20.4'), 'c1205': ('1.20.5', '1.21.1'),
    'c1212': ('1.21.2', '1.21.4'), 'c1215': ('1.21.5', '1.21.7'),
    'g120': ('1.20', '1.20.1'), 'gscreen': ('1.20.2', '1.21.5'), 'g1202': ('1.20.2', '1.20.2'),
    'g1203': ('1.20.3', '1.21.1'), 'g1212': ('1.21.2', '1.21.5'), 'client121': ('1.21.6', '1.21.7'),
    'client26': ('99', '99'),
}


def idx(v):
    return ORDER.index(v)


def main():
    import os
    jar, names = os.path.abspath(sys.argv[1]), sys.argv[2]
    versions = sys.argv[3:]
    prefix = {'int': 'int', 'srg': 'srg', 'named': 'named'}[names]
    owners = 'net/minecraft/,com/mojang/'
    skips = []
    targets = []
    for v in versions:
        label = 'mc' + v
        for pkg, (a, b) in RANGES.items():
            if pkg == 'client26' or not (idx(a) <= idx(v) <= idx(b)):
                skips.append(f'{label}:com/pokato/whatchanged/{pkg}/')
        libs = open(f'{MC}/libs-{v}.txt').read().strip()
        targets.append(f'{label}={MC}/{prefix}-{v}.jar' + (',' + libs if libs else ''))
    cmd = ['python3', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'refcheck.py'), jar, owners, ','.join(skips), '--'] + targets
    r = subprocess.run(cmd, capture_output=True, text=True, cwd=MC)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    sys.exit(r.returncode)


main()
