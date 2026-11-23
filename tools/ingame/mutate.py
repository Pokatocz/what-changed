#!/usr/bin/env python3
"""mutate.py world <world-snapshot.json> | pack <pack-snapshot.json> <config dir>
Turns the saved snapshot into an "older" one, so the next start has a known set of changes to report.
Prints what it did (the expected report)."""
import json
import os
import sys

mode, path = sys.argv[1], sys.argv[2]
s = json.load(open(path, encoding='utf-8'))


def older(v):
    p = v.split('.')
    for i in range(len(p) - 1, -1, -1):
        if p[i].isdigit() and int(p[i]) > 0:
            p[i] = str(int(p[i]) - 1)
            return '.'.join(p)
    return '0.0.1-old'


if mode == 'world':
    rec = sorted(s['recipes'])
    for k in rec[:10]:
        del s['recipes'][k]                                   # -> 10 new recipes
    for k in rec[10:13]:
        s['recipes'][k]['h'] = 'deadbeef' + s['recipes'][k]['h'][8:]   # -> 3 changed
    s['recipes']['oldmod:ruby_sword'] = {'h': '0' * 16, 'o': 'oldmod:ruby_sword'}   # -> 2 removed
    s['recipes']['oldmod:ruby_block'] = {'h': '1' * 16, 'o': 'oldmod:ruby_block'}
    nb = min(3, len(s['biomes']))
    s['biomes'] = s['biomes'][nb:] + ['oldmod:ruby_hills']    # -> 3 new, 1 removed biome
    ns = min(2, len(s['structures']))
    s['structures'] = s['structures'][ns:]                   # -> 2 new structures
    ne = 1 if s.get('enchantments') else 0
    s['enchantments'] = s.get('enchantments', [])[ne:]       # -> 1 new enchantment
    print('world: recipes +10 ~3 -2, biomes +%d -1, structures +%d, enchantments +%d' % (nb, ns, ne))
else:
    cfg = sys.argv[3]
    mods = s['mods']
    for mid, old in (('hotfixtest', '1.0.0'), ('betatest', '2.0.0'), ('ultitest', '1.5.0')):
        mods[mid]['version'] = old                            # -> 2 updated, 1 downgraded
    del mods['newmodtest']                                    # -> 1 added mod
    mods['oldmod'] = {'name': 'Old Ruby Mod', 'version': '3.2.1', 'url': None}   # -> 1 removed mod
    lid = next(m for m in ('fabricloader', 'neoforge', 'forge') if m in mods)
    lold = older(mods[lid]['version'])
    print('pack: loader %s %s -> %s' % (lid, lold, mods[lid]['version']))
    mods[lid]['version'] = lold                               # -> loader updated
    items = sorted(k for k in s['items'] if k.startswith('minecraft:') and k != 'minecraft:air')
    for k in items[:12]:
        del s['items'][k]                                     # -> 12 new items
    s['items']['oldmod:ruby'] = 'Ruby'                         # -> 2 removed items
    s['items']['oldmod:ruby_block'] = 'Block of Ruby'
    ents = sorted(s['entities'])
    for k in ents[:4]:
        del s['entities'][k]                                  # -> 4 new mobs
    s['entities']['oldmod:ruby_golem'] = 'Ruby Golem'          # -> 1 removed mob
    # configs: one changed on disk, one new file on disk, one removed (only in the snapshot)
    p = os.path.join(cfg, 'rubytest-common.toml')
    t = open(p).read().replace('spawnRate = 5', 'spawnRate = 8')
    open(p, 'w').write(t)
    open(os.path.join(cfg, 'newtest-client.json'), 'w').write('{"showHud": true, "scale": 1.5}\n')
    if s.get('configs') is not None:
        s['configs']['oldtest.cfg'] = {'h': 'abc', 'k': {'enabled': 'true'}}
    print('pack: mods +1 -1 upd=2 down=1, items +12 -2, mobs +4 -1, configs ~rubytest-common.toml(spawnRate 5->8) +newtest-client.json -oldtest.cfg')
json.dump(s, open(path, 'w', encoding='utf-8'), indent=1)
