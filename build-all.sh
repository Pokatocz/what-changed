#!/bin/bash
# build-all.sh [-Pselftest] : builds all eight loader JARs.
#   1. build-shims.sh   - version shims, compiled per Minecraft version (named / intermediary / SRG)
#   2. build-client26.sh - the 26.x GUI layer
#   3. the eight Gradle projects (each packs the shims / client26 classes it needs)
set -e
cd "$(dirname "$0")"
EXTRA="$*"
LOGD=${LOGD:-/tmp/wc-build}; mkdir -p $LOGD
J21=/usr/lib/jvm/java-21-openjdk-amd64
J25=/opt/jdk25/jdk-25.0.4.1+1
G8=gradle
G9=/opt/gradle-9.5.1/bin/gradle
run() { # run <name> <java home> <gradle> : gradle build in ./<name>, retrying on Maven Central 429s
  local name=$1 jh=$2 g=$3
  if (cd $name && JAVA_HOME=$jh GRADLE=$g ../tools/gradle-retry.sh $LOGD/$name.log build --no-daemon --max-workers=1 $EXTRA); then
    echo "$name OK"
  else
    echo "$name FAILED"; grep -E "error:|What went wrong" -A3 $LOGD/$name.log | head -20; return 1
  fi
}
./build-shims.sh > $LOGD/shims.log 2>&1 && echo "shims OK" || { echo "shims FAILED"; tail -20 $LOGD/shims.log; exit 1; }
./build-client26.sh > $LOGD/client26.log 2>&1 && echo "client26 OK" || { echo "client26 FAILED"; tail -20 $LOGD/client26.log; exit 1; }
# 1.20 - 1.21.7
run fabric120 $J21 $G8
run forge120 $J21 $G8
run forge1206 $J21 $G9
run neoforge120 $J21 $G9
# 1.21.8 - 26.3
run fabric121 $J21 $G8
run fabric26 $J25 $G9
run neoforge $J21 $G8
run forge $J21 $G9
ls -la */build/libs/*.jar
