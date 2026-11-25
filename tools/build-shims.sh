#!/bin/bash
# build-shims.sh : compiles every version shim against the Minecraft version it targets and remaps it.
#   shared/shims/build/named/<shim>         Mojang names  (NeoForge 1.20.2+, Forge 1.20.6+)
#   shared/shims/build/intermediary/<shim>  Fabric intermediary (Fabric 1.20 - 1.21.7)
#   shared/shims/build/srg/<shim>           Mojang class names + SRG members (Forge 1.20 - 1.20.4)
# Needs the per-version jars made by the mcjars tooling (named-<v>.jar, libsall-<v>.txt, maps/mi|ms-<v>.tiny).
set -e
cd "$(dirname "$0")/.."
MC=${MCJARS:-$HOME/mcjars}
JDK=${JDK21:-/usr/lib/jvm/java-21-openjdk-amd64}
OUT=shared/shims/build
rm -rf $OUT && mkdir -p $OUT/named $OUT/intermediary $OUT/srg $OUT/tmp

# name : compile-against : source dir : extra shim sources it links against : srg?
SPECS="
c120:1.20.1:shared/shims/c120::srg
c1202:1.20.2:shared/shims/c1202::srg
c1205:1.20.5:shared/shims/c1205::
c1212:1.21.2:shared/shims/c1212::
c1215:1.21.5:shared/shims/c1215::
g120:1.20.1:shared/shims/g120::srg
gscreen:1.20.2:shared/shims/gscreen::srg
g1202:1.20.2:shared/shims/g1202:shared/shims/gscreen:srg
g1203:1.20.3:shared/shims/g1203:shared/shims/gscreen:srg
g1212:1.21.2:shared/shims/g1212:shared/shims/gscreen:
client121:1.21.6:shared/client121::
"
COMMON=$(find shared/common/src/main/java -name '*.java')
for spec in $SPECS; do
  IFS=: read -r name v dir dep srg <<< "$spec"
  pkg=$name
  T=$OUT/tmp/$name; rm -rf $T; mkdir -p $T
  CP="$MC/named-$v.jar:$(cat $MC/libsall-$v.txt)"
  SRC="$COMMON $(find $dir/src/main/java -name '*.java')"
  [ -n "$dep" ] && SRC="$SRC $(find $dep/src/main/java -name '*.java')"
  $JDK/bin/javac --release 17 -nowarn -encoding UTF-8 -proc:none -Xlint:-deprecation -d $T -cp "$CP" $SRC
  mkdir -p $OUT/named/$name/com/pokato/whatchanged
  cp -r $T/com/pokato/whatchanged/$pkg $OUT/named/$name/com/pokato/whatchanged/
  # remap: everything else compiled here (common, the dependency shim) is classpath only
  (cd $OUT/named/$name && $JDK/bin/jar cf ../../tmp/$name-named.jar .)
  rm -rf $T/com/pokato/whatchanged/$pkg && (cd $T && $JDK/bin/jar cf ../$name-cp.jar .)
  RCP="$MC/named-$v.jar $(tr ':' ' ' < $MC/libsall-$v.txt) $OUT/tmp/$name-cp.jar"
  java -jar $MC/tiny-remapper.jar $OUT/tmp/$name-named.jar $OUT/tmp/$name-int.jar $MC/maps/mi-$v.tiny named intermediary $RCP > $OUT/tmp/$name-int.log 2>&1
  mkdir -p $OUT/intermediary/$name && (cd $OUT/intermediary/$name && unzip -q ../../tmp/$name-int.jar)
  if [ -n "$srg" ]; then
    java -jar $MC/tiny-remapper.jar $OUT/tmp/$name-named.jar $OUT/tmp/$name-srg.jar $MC/maps/ms-$v.tiny named srg $RCP > $OUT/tmp/$name-srg.log 2>&1
    mkdir -p $OUT/srg/$name && (cd $OUT/srg/$name && unzip -q ../../tmp/$name-srg.jar)
  fi
  echo "$name ($v): $(find $OUT/named/$name -name '*.class' | wc -l) classes${srg:+ +srg}"
done
rm -rf $OUT/intermediary/*/META-INF $OUT/srg/*/META-INF
