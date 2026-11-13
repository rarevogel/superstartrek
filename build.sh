#!/bin/sh
# Builds Super Star Trek without Gradle: aapt2 -> javac -> dx -> zip -> zipalign-free apksigner
set -e
cd "$(dirname "$0")"
T=$HOME/tools
AAPT2=$T/aapt/package/bin/x64/linux/aapt2
JAR=$T/android.jar
B=build
rm -rf $B/res $B/classes $B/apk $B/gen
mkdir -p $B/res $B/classes $B/apk $B/gen

$AAPT2 compile --dir res -o $B/res/res.zip
$AAPT2 link -o $B/apk/base.apk -I $JAR --manifest AndroidManifest.xml \
    -A assets --java $B/gen $B/res/res.zip --auto-add-overlay

javac -nowarn -encoding UTF-8 --release 8 -classpath $JAR -d $B/classes \
    $(find src $B/gen -name '*.java') 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
ls $B/classes/nl/rarevogel/startrek/MainActivity.class >/dev/null

java -jar $T/dx.jar --dex --min-sdk-version=21 --output=$B/apk/classes.dex $B/classes 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
(cd $B/apk && cp base.apk raw.apk && zip -q -0 raw.apk classes.dex)
python3 zipalign.py $B/apk/raw.apk $B/apk/unsigned.apk

if [ ! -f keystore.jks ]; then
  keytool -genkeypair -keystore keystore.jks -storepass startrek -keypass startrek -alias sst \
    -keyalg RSA -keysize 2048 -validity 20000 -dname "CN=rarevogel, O=Super Star Trek" 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
fi
java -jar $T/apksigner.jar sign --ks keystore.jks --ks-pass pass:startrek --key-pass pass:startrek \
    --min-sdk-version 21 --out SuperStarTrek.apk $B/apk/unsigned.apk 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
java -jar $T/apksigner.jar verify -v SuperStarTrek.apk 2>&1 | grep -v JAVA_TOOL_OPTIONS | head -6
ls -la SuperStarTrek.apk
