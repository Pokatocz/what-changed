#!/bin/bash
# Compiles the 26.x client layer (shared/client26/) at --release 21 against Minecraft 26.1, into shared/client26/build/classes.
# The Forge/NeoForge builds copy these classes into their single JAR; they are only loaded on 26.x.
set -e
cd "$(dirname "$0")/.."
JDK=${JDK25:-/opt/jdk25/jdk-25.0.4.1+1}
OUT=shared/client26/build/classes
rm -rf $OUT && mkdir -p $OUT
# common is compiled together (as sources) so the layer links against the same API, but only client26 classes are kept
TMP=$(mktemp -d)
$JDK/bin/javac --release 21 -nowarn -encoding UTF-8 -d $TMP -cp "$(cat tools/client26-cp.txt)" \
  $(find shared/common/src/main/java shared/client26/src/main/java -name '*.java')
mkdir -p $OUT/com/pokato/whatchanged && cp -r $TMP/com/pokato/whatchanged/client26 $OUT/com/pokato/whatchanged/
rm -rf $TMP
find $OUT -name '*.class' | wc -l
