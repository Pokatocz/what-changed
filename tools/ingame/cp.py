import json, os, sys
v = sys.argv[1]; M = os.environ.get('MCMAIN', os.path.expanduser('~/mctest/main'))
j = json.load(open(f'{M}/versions/{v}/{v}.json'))
out = []
for lib in j['libraries']:
    a = lib.get('downloads', {}).get('artifact')
    if a and 'natives' not in a['path']:
        p = f"{M}/libraries/{a['path']}"
        if os.path.exists(p): out.append(p)
print(':'.join(out))
