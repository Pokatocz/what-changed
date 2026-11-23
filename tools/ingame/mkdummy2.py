import json, sys, zipfile
out, kind, modid, version = sys.argv[1:5]
with zipfile.ZipFile(out, 'w') as z:
    if kind == 'fabric':
        z.writestr('fabric.mod.json', json.dumps({"schemaVersion": 1, "id": modid, "version": version, "name": modid, "environment": "*"}))
    else:
        toml = ('modLoader="lowcodefml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="%s"\nversion="%s"\ndisplayName="%s"\n'
                % (modid, version, modid))
        z.writestr('META-INF/mods.toml', toml)
        z.writestr('META-INF/neoforge.mods.toml', toml)
        z.writestr('pack.mcmeta', json.dumps({"pack": {"description": modid, "pack_format": 15}}))
