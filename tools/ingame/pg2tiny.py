#!/usr/bin/env python3
"""pg2tiny.py client.txt out.tiny : Mojang ProGuard mappings -> tiny v2 with namespaces official, named."""
import re
import sys

PRIM = {'byte': 'B', 'char': 'C', 'double': 'D', 'float': 'F', 'int': 'I', 'long': 'J', 'short': 'S', 'boolean': 'Z', 'void': 'V'}
classes = []   # (named, obf, members)
cur = None
for line in open(sys.argv[1], encoding='utf-8'):
    if line.startswith('#') or not line.strip():
        continue
    if not line.startswith(' '):
        named, obf = line.rstrip().rstrip(':').split(' -> ')
        cur = (named.replace('.', '/'), obf.replace('.', '/'), [])
        classes.append(cur)
    else:
        cur[2].append(line.strip())
n2o = {n: o for n, o, _ in classes}


def desc(t, mapping):
    dim = t.count('[]')
    t = t.replace('[]', '')
    if t in PRIM:
        d = PRIM[t]
    else:
        t = t.replace('.', '/')
        d = 'L' + mapping.get(t, t) + ';'
    return '[' * dim + d


out = ['tiny\t2\t0\tofficial\tnamed']
for named, obf, members in classes:
    out.append('c\t%s\t%s' % (obf, named))
    for m in members:
        left, mobf = m.split(' -> ')
        left = re.sub(r'^\d+:\d+:', '', left)
        typ, rest = left.split(' ', 1)
        if '(' in rest:
            name = rest[:rest.index('(')]
            args = rest[rest.index('(') + 1:rest.rindex(')')]
            a = ''.join(desc(x, n2o) for x in args.split(',')) if args else ''
            out.append('\tm\t(%s)%s\t%s\t%s' % (a, desc(typ, n2o), mobf, name))
        else:
            out.append('\tf\t%s\t%s\t%s' % (desc(typ, n2o), mobf, rest))
open(sys.argv[2], 'w', encoding='utf-8').write('\n'.join(out) + '\n')
print(sys.argv[2], len(classes), 'classes')
