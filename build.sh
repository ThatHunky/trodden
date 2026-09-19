#!/bin/bash
# Builds Trodden: JDK 25 against paper-api 26.2 from the Matsuri server's libraries/, plus HuskClaims
# and the other claim-plugin jars in libs/ (not in git). The jar is only written if TestMain passed.
#
# This is the maintainer's local fast path — it needs the Matsuri server's libraries/ (or SERVER
# pointed at some other Paper install with the same layout) and does not claim to be portable.
# ./gradlew build is the portable way to build this plugin anywhere.
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="${SERVER:-/home/thathunky/games/servers/matsuri}"
JDK=/usr/lib/jvm/temurin-25-jdk-amd64/bin
CP="$(find "$SERVER/libraries" -name '*.jar' | tr '\n' ':')$SERVER/plugins/HuskClaims-1.5.10.jar:$(find "$HERE/libs" -name '*.jar' 2>/dev/null | tr '\n' ':')"

rm -rf "$HERE/build"
rm -f "$HERE/Trodden-1.0.0.jar"
mkdir -p "$HERE/build/classes" "$HERE/build/test"
"$JDK/javac" --release 25 -encoding UTF-8 -cp "$CP" -d "$HERE/build/classes" \
    $(find "$HERE/src/main/java" -name '*.java')
"$JDK/javac" --release 25 -encoding UTF-8 -cp "$HERE/build/classes:$CP" -d "$HERE/build/test" \
    $(find "$HERE/src/test/java" -name '*.java')
"$JDK/java" -cp "$HERE/build/classes:$HERE/build/test:$CP" dev.thathunky.trodden.TestMain

cp "$HERE/plugin.yml" "$HERE/config.yml" "$HERE/build/classes/"
cp -r "$HERE/lang" "$HERE/build/classes/"
"$JDK/jar" --create --file "$HERE/Trodden-1.0.0.jar" -C "$HERE/build/classes" .
echo "built: $HERE/Trodden-1.0.0.jar"
