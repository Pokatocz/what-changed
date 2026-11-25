#!/bin/bash
# verify.sh [suffix] : resolve every Minecraft/Mojang and loader reference of the built JARs against every target version.
# suffix "" = release JARs, "SELFTEST-" = test JARs.
T=$(cd "$(dirname "$0")" && pwd); W=$(dirname "$T")
cd "${MCJARS:-$HOME/mcjars}"
S=${1:-}
NEO=$W/loaders/neoforge/build/libs/whatchanged-neoforge-1.21.8-26.3-${S}${V:-1.2.0}.jar
FORGE=$W/loaders/forge/build/libs/whatchanged-forge-1.21.8-26.3-${S}${V:-1.2.0}.jar
F26=$W/loaders/fabric26/build/libs/whatchanged-fabric-26.1-26.3-${S}${V:-1.2.0}.jar
ALL="1.21.8 1.21.9 1.21.10 1.21.11 26.1 26.1.1 26.1.2 26.2 26.3"
mc() { local a=""; for v in $1; do a="$a mc$v=named-$v.jar,$(cat libs-$v.txt)"; done; echo $a; }
rc=0
echo "### NeoForge JAR: Minecraft refs";  python3 $T/refcheck.py $NEO net/minecraft/,com/mojang/ "mc1.21*:com/pokato/whatchanged/client26/,mc26*:com/pokato/whatchanged/client121/" -- $(mc "$ALL") || rc=1
echo "### Forge JAR: Minecraft refs";     python3 $T/refcheck.py $FORGE net/minecraft/,com/mojang/ "mc1.21*:com/pokato/whatchanged/client26/,mc26*:com/pokato/whatchanged/client121/" -- $(mc "$ALL") || rc=1
echo "### Fabric 26 JAR: Minecraft refs"; python3 $T/refcheck.py $F26 net/minecraft/,com/mojang/ - -- $(mc "26.1 26.1.1 26.1.2 26.2 26.3") || rc=1
a=""; for f in neo/cp-*.txt; do v=$(basename $f .txt); a="$a neo${v#cp-}=$(cat $f)"; done
echo "### NeoForge JAR: NeoForge refs";   python3 $T/refcheck.py $NEO net/neoforged/ - -- $a || rc=1
a=""; for v in $ALL; do a="$a forge$v=$(cat forge/cp-$v.txt)"; done
echo "### Forge JAR: Forge refs";         python3 $T/refcheck.py $FORGE net/minecraftforge/ - -- $a || rc=1
echo "### RESULT: $([ $rc = 0 ] && echo ALL OK || echo FAILURES)"
exit $rc
