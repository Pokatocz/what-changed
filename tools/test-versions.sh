#!/bin/bash
# test-versions.sh : compiles VersionCompare and runs tools/VersionCompareTest.java (plain JDK, no Minecraft).
set -e
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)
javac -d "$OUT" common/src/main/java/com/pokato/whatchanged/core/VersionCompare.java
java -cp "$OUT" tools/VersionCompareTest.java
rm -rf "$OUT"
