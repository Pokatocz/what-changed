#!/bin/bash
# wct.sh <combo> : full production test of one What Changed? test JAR (release JAR + selftest classes).
#   s1 dedicated server (first run) -> mutate world snapshot -> s2 (server report) -> copy world to client
#   c1 client (first run: title + world) -> mutate pack + world snapshots -> c2 (title + screens + world + visual)
# Results: $WCT/logs/<combo>/summary.txt
# Env: WCT (work dir, default ~/wct; test JARs in $WCT/jars/<combo>.jar, Fabric API in $WCT/fabric-api/),
#      MCMAIN (portablemc main dir with the installed clients, default ~/mctest/main)
set -u
C=$1
WCT=${WCT:-$HOME/wct}; MCMAIN=${MCMAIN:-$HOME/mctest/main}; HERE=$(cd "$(dirname "$0")" && pwd)
T=$HOME/.pokato-tools
J17=$T/jdk-17/bin/java; J21=$T/jdk-21/bin/java; J25=$T/jdk-25/bin/java
case $C in
  fab120)   L=fabric;   MC=1.20.1; LV=0.20.1;            SPEC=fabric:1.20.1:0.20.1;  JAVA=$J17; FAPI=$WCT/fabric-api/fapi-1.20.1.jar ;;
  fab263)   L=fabric;   MC=26.3;   LV=0.20.1;            SPEC=fabric:26.3:0.20.1;    JAVA=$J25; FAPI=$WCT/fabric-api/fapi-26.3.jar ;;
  forge120) L=forge;    MC=1.20.1; LV=1.20.1-47.4.28;    SPEC=1.20.1-forge-47.4.28;  JAVA=$J17 ;;
  forge263) L=forge;    MC=26.3;   LV=26.3-66.1.9;       SPEC=26.3-forge-66.1.9;     JAVA=$J25 ;;
  neo1202)  L=neoforge; MC=1.20.2; LV=20.2.93;           SPEC=neoforge::20.2.93;     JAVA=$J17 ;;
  neo263)   L=neoforge; MC=26.3;   LV=26.3.0.115;        SPEC=neoforge::26.3.0.115;  JAVA=$J25 ;;
  fab1218)  L=fabric;   MC=1.21.8; LV=0.20.1;            SPEC=fabric:1.21.8:0.20.1;  JAVA=$J21; FAPI=$WCT/fabric-api/fapi-1.21.8.jar ;;
  forge1211) L=forge;   MC=1.21.1; LV=1.21.1-52.1.20;    SPEC=1.21.1-forge-52.1.20;  JAVA=$J21; INSTJAVA=$J17 ;;
  neo1201)  L=neolegacy; MC=1.20.1; LV=1.20.1-47.1.106;  SPEC=1.20.1-forge-47.1.106; JAVA=$J17 ;;
esac
INSTJAVA=${INSTJAVA:-$JAVA}
JAR=$WCT/jars/$C.jar
D=$WCT/run/$C; LOG=$WCT/logs/$C; MAIN=$MCMAIN
rm -rf $D $LOG; mkdir -p $D/client/mods $D/client/saves $D/server/mods $LOG
cp $JAR $D/client/mods/whatchanged-test.jar; cp $JAR $D/server/mods/whatchanged-test.jar
[ $L = fabric ] && { cp $FAPI $D/client/mods/fabric-api.jar; cp $FAPI $D/server/mods/fabric-api.jar; }
KIND=forge; [ $L = fabric ] && KIND=fabric
# test mods: installed at their new versions; mutate.py puts the old ones into the client's pack snapshot
python3 -I $HERE/mkdummy2.py $D/client/mods/dummy-hotfix.jar $KIND hotfixtest 1.0.0-hotfix
python3 -I $HERE/mkdummy2.py $D/client/mods/dummy-beta.jar $KIND betatest 2.0.0-beta.1
python3 -I $HERE/mkdummy2.py $D/client/mods/dummy-ulti.jar $KIND ultitest 1.5.0-ultimate
python3 -I $HERE/mkdummy2.py $D/client/mods/dummy-new.jar $KIND newmodtest 1.0.0
mkdir -p $D/client/config && printf "# rubytest\n[general]\nspawnRate = 5\nenableRubies = true\n" > $D/client/config/rubytest-common.toml
printf "onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\ntutorialStep:none\nnarrator:0\nsoundCategory_master:0.0\nrenderDistance:4\nsimulationDistance:5\npauseOnLostFocus:false\nguiScale:2\n" > $D/client/options.txt

# ---- dedicated server install
cd $D/server
echo "eula=true" > eula.txt
printf "online-mode=false\nlevel-name=world\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\nserver-port=$(( 25500 + RANDOM % 400 ))\n" > server.properties
case $L in
fabric)
  INST=$(curl -s https://meta.fabricmc.net/v2/versions/installer | python3 -c "import json,sys;print(json.load(sys.stdin)[0]['version'])")
  curl -s -o server.jar https://meta.fabricmc.net/v2/versions/loader/$MC/$LV/$INST/server/jar
  SRV=( $JAVA -Xmx2G -jar server.jar nogui ) ;;
forge)
  curl -s -A "Mozilla/5.0" -o inst.jar https://maven.minecraftforge.net/net/minecraftforge/forge/$LV/forge-$LV-installer.jar
  $INSTJAVA -jar inst.jar --installServer . > $LOG/install.log 2>&1
  SRV=( $JAVA -Xmx2G @libraries/net/minecraftforge/forge/$LV/unix_args.txt nogui ) ;;
neolegacy)
  curl -s -A "Mozilla/5.0" -o inst.jar https://maven.neoforged.net/releases/net/neoforged/forge/$LV/forge-$LV-installer.jar
  $INSTJAVA -jar inst.jar --installServer . > $LOG/install.log 2>&1
  ARGS=$(ls libraries/net/*/forge/$LV/unix_args.txt | head -1)
  SRV=( $JAVA -Xmx2G @$ARGS nogui ) ;;
neoforge)
  curl -s -A "Mozilla/5.0" -o inst.jar https://maven.neoforged.net/releases/net/neoforged/neoforge/$LV/neoforge-$LV-installer.jar
  $JAVA -jar inst.jar --installServer . > $LOG/install.log 2>&1
  SRV=( $JAVA -Xmx2G @libraries/net/neoforged/neoforge/$LV/unix_args.txt nogui ) ;;
esac
srv() { rm -rf $D/server/wctest-out; (cd $D/server && WCTEST_MODE=server timeout 900 "${SRV[@]}" > $LOG/$1.log 2>&1 < /dev/null)
        mkdir -p $LOG/out-$1; cp $D/server/wctest-out/result.txt $LOG/out-$1/ 2>/dev/null; }

# ---- client display + watcher (screenshots on *.req, real mouse moves on *.mouse)
DNUM=$(( 90 + RANDOM % 9 ))
Xvfb :$DNUM -screen 0 1280x720x24 +extension GLX +render -noreset > /dev/null 2>&1 &
XPID=$!
OUT=$D/client/wctest-out
( mkdir -p $OUT/shots; while true; do
    for f in $OUT/*.req; do [ -e "$f" ] || continue; n=$(basename "$f" .req); sleep 0.3; import -window root -display :$DNUM $OUT/shots/$n.png 2>/dev/null; rm -f "$f"; done
    for f in $OUT/*.mouse; do [ -e "$f" ] || continue; read -r mx my < "$f"; DISPLAY=:$DNUM xdotool mousemove $mx $my; sleep 0.3; rm -f "$f"; done
    sleep 0.2; done ) &
WPID=$!
mkdir -p /tmp/xdg-runtime
cli() {
  rm -f $OUT/result.txt
  (cd $D/client && DISPLAY=:$DNUM LIBGL_ALWAYS_SOFTWARE=1 SDL_VIDEO_FORCE_EGL=1 SDL_VIDEO_X11_FORCE_EGL=1 XDG_RUNTIME_DIR=/tmp/xdg-runtime \
    WCTEST_MODE=client WCTEST_STEPS=$2 timeout 1500 portablemc --main-dir $MAIN start "$SPEC" --mc-dir $D/client --jvm $JAVA \
    --resolution 854x480 -u WCTester > $LOG/$1.log 2>&1 < /dev/null)
  mkdir -p $LOG/out-$1/shots; cp $OUT/result.txt $LOG/out-$1/ 2>/dev/null; mv $OUT/shots/*.png $LOG/out-$1/shots/ 2>/dev/null
  cp $D/client/logs/latest.log $LOG/$1-latest.log 2>/dev/null
  mkdir -p $LOG/crash-$1; mv $D/client/crash-reports/* $LOG/crash-$1/ 2>/dev/null
}

srv s1
python3 -I $HERE/mutate.py world $D/server/world/whatchanged/world-snapshot.json >> $LOG/mutate.log 2>&1
srv s2
cp -r $D/server/world $D/client/saves/TestWorld
cli c1 title,world
python3 -I $HERE/mutate.py pack $D/client/config/whatchanged/pack-snapshot.json $D/client/config >> $LOG/mutate.log 2>&1
python3 -I $HERE/mutate.py world $D/client/saves/TestWorld/whatchanged/world-snapshot.json >> $LOG/mutate.log 2>&1
cli c2 title,screens,world
cli c3 visual

kill $WPID $XPID 2>/dev/null
{
echo "===== $C ($SPEC, $(basename $JAR), java $($JAVA -version 2>&1 | grep -v Picked | head -1))"
echo "--- mutate"; cat $LOG/mutate.log
for r in s1 s2 c1 c2 c3; do echo "--- $r"; grep -vE "^SHOT " $LOG/out-$r/result.txt 2>/dev/null | cut -c1-260; done
echo "--- errors"; grep -hE "Exception|ERROR|Caused by|whatchanged.*(failed|Could not)|\[What Changed\?\].*(failed|missing)" $LOG/s?.log $LOG/c?-latest.log $LOG/c?.log 2>/dev/null \
  | grep -vE "OpenAL|SoundSystem|[Rr]ealms|narrator|Narrator|GLFW error 65544|Failed to load resource pack|kqueue|DebugFile|Only supported on OSX|AppenderLoggingException|ClosedChannelException|lost connection|Failed to fetch user properties|Not a string$|authlib|InvalidCredentialsException|status=401|MinecraftClientHttpException" | sort -u | head -25
echo "--- crashes"; ls $LOG/crash-* 2>/dev/null | grep -v ":" | tr '\n' ' '; echo
echo "--- what changed lines"; grep -hE "\[What Changed\?\] (Modpack|World|Using|Ready|Server changelog|Minecraft)" $LOG/s?.log $LOG/c?-latest.log 2>/dev/null | cut -c1-230
echo "--- shots c1: $(ls $LOG/out-c1/shots 2>/dev/null | wc -l)  c2: $(ls $LOG/out-c2/shots 2>/dev/null | wc -l)  c3: $(ls $LOG/out-c3/shots 2>/dev/null | wc -l)"
} > $LOG/summary.txt
echo "$C finished"
