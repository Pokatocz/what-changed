#!/usr/bin/env python3
"""Merge Mojang (official->named tiny v2) with Fabric intermediary (tiny v2) or Forge MCPConfig SRG (tsrg2)
into one tiny v2 file with namespaces: official named <target>.

usage: mergemaps.py MOJANG.tiny TARGET_FILE (int|srg) OUT.tiny

srg: Forge 1.17+ runs with Mojang class names and SRG member names (m_1234_ / f_1234_), so the target
namespace keeps the named class names and only takes member names from the tsrg.
"""
import sys


def read_tiny(path):
    """-> classes {obf: name}, methods {(cls_obf, name_obf, desc_obf): name}, fields {(cls_obf, name_obf): (desc, name)}"""
    classes, methods, fields = {}, {}, {}
    cur = None
    with open(path, encoding='utf-8') as f:
        header = f.readline().rstrip('\n').split('\t')
        for line in f:
            p = line.rstrip('\n').split('\t')
            if p[0] == 'c':
                cur = p[1]
                classes[cur] = p[2]
            elif len(p) >= 5 and p[0] == '' and p[1] == 'm':
                methods[(cur, p[3], p[2])] = p[4]
            elif len(p) >= 5 and p[0] == '' and p[1] == 'f':
                fields[(cur, p[3])] = (p[2], p[4])
    return classes, methods, fields


def read_tsrg2(path):
    methods, fields = {}, {}
    cur = None
    with open(path, encoding='utf-8') as f:
        f.readline()
        for line in f:
            line = line.rstrip('\n')
            if not line or line.startswith('\t\t'):
                continue
            if not line.startswith('\t'):
                cur = line.split(' ')[0]
                continue
            p = line[1:].split(' ')
            if len(p) >= 3 and p[1].startswith('('):
                methods[(cur, p[0], p[1])] = p[2]
            elif len(p) >= 2:
                fields[(cur, p[0])] = p[1]
    return methods, fields


def main():
    moj, target, kind, out = sys.argv[1:5]
    mc, mm, mf = read_tiny(moj)
    if kind == 'int':
        tc, tm, tf = read_tiny(target)
        tf = {k: v[1] for k, v in tf.items()}
    else:
        tm, tf = read_tsrg2(target)
        tc = None
    lines = ['tiny\t2\t0\tofficial\tnamed\t' + ('intermediary' if kind == 'int' else 'srg')]
    by_class_m, by_class_f = {}, {}
    for k in mm:
        by_class_m.setdefault(k[0], []).append(k)
    for k in mf:
        by_class_f.setdefault(k[0], []).append(k)
    for obf, named in mc.items():
        t = tc.get(obf, named) if tc is not None else named
        lines.append(f'c\t{obf}\t{named}\t{t}')
        for k in by_class_m.get(obf, []):
            n = mm[k]
            tn = tm.get(k, n)
            lines.append(f'\tm\t{k[2]}\t{k[1]}\t{n}\t{tn}')
        for k in by_class_f.get(obf, []):
            desc, n = mf[k]
            tn = tf.get(k, n)
            lines.append(f'\tf\t{desc}\t{k[1]}\t{n}\t{tn}')
    with open(out, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')


main()
