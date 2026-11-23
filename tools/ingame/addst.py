import sys, zipfile
rel, st, out = sys.argv[1:4]
zi, zs = zipfile.ZipFile(rel), zipfile.ZipFile(st)
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zo:
    for i in zi.infolist(): zo.writestr(i, zi.read(i.filename))
    n = 0
    for i in zs.infolist():
        if i.filename.startswith('com/pokato/whatchanged/selftest/') and i.filename.endswith('.class'):
            zo.writestr(i.filename, zs.read(i.filename)); n += 1
print(out, '+', n, 'test classes')
