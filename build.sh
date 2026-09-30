#!/bin/bash
# Manual APK build for ZyneLabs IPTV v3 (no Gradle) — Java + Media3 AARs
set -euo pipefail
export JAVA_HOME=$HOME/jdk17
export PATH=$JAVA_HOME/bin:$PATH

SDK=~/android-sdk
BT=$SDK/build-tools/34.0.0
AAPT2=$BT/aapt2
D8=$BT/d8
ZIPALIGN=$BT/zipalign
APKSIGNER=$BT/apksigner
ANDROID_JAR=$SDK/platforms/android-34/android.jar
KEYSTORE=~/workspace/shizuku-build/manual/debug.keystore

PROJ=~/workspace/zyne-iptv
M=$PROJ/app/src/main
LIBS=$PROJ/libs
OUT=/tmp/zyne-iptv-out
chmod -R u+w $OUT 2>/dev/null || true
rm -rf $OUT && mkdir -p $OUT/compiled $OUT/gen $OUT/classes $OUT/dex \
  $OUT/aar $OUT/static-lib $OUT/lib-classes $OUT/gen-lib

VER_CODE=18
VER_NAME="3.9.1"
APK_NAME="ZyneLabsIPTV-3.9.1.apk"

echo "=== [1/7] Extracting AARs/JARs ==="
CP="$ANDROID_JAR"
AAR_RES_ZIPS=""
> $OUT/aar_pkgs.txt
for aar in $LIBS/*.aar; do
  n=$(basename "$aar" .aar)
  d=$OUT/aar/$n
  mkdir -p "$d"
  unzip -q -o "$aar" -d "$d"
  if [ -f "$d/classes.jar" ]; then
    CP="$CP:$d/classes.jar"
    mkdir -p "$OUT/lib-classes/$n"
    (cd "$OUT/lib-classes/$n" && unzip -q -n "$d/classes.jar")
  fi
  for j in "$d"/libs/*.jar; do
    [ -f "$j" ] || continue
    CP="$CP:$j"
  done
  if [ -d "$d/res" ]; then
    # overlay-merge library resources into the app package (avoids the
    # proto-manifest that aapt2 emits when --static-lib is used on the app link)
    $AAPT2 compile --dir "$d/res" -o "$OUT/compiled/aar-$n.zip"
    AAR_RES_ZIPS="$AAR_RES_ZIPS -R $OUT/compiled/aar-$n.zip"
    pkg=$(grep -o 'package="[^"]*"' "$d/AndroidManifest.xml" | head -1 | cut -d'"' -f2)
    echo "$n|$pkg" >> $OUT/aar_pkgs.txt
    echo "aar res: $n ($pkg)"
  fi
done
# plain jars (skip the empty listenablefuture stub; guava has the real classes)
for j in $LIBS/*.jar; do
  case "$j" in *empty-to-avoid-conflict*) continue;; esac
  CP="$CP:$j"
  bn=$(basename "$j" .jar)
  mkdir -p "$OUT/lib-classes/plain-$bn"
  (cd "$OUT/lib-classes/plain-$bn" && unzip -q -n "$j")
done
echo "classpath entries: $(echo "$CP" | tr ':' '\n' | wc -l)"
# d8 cannot handle module-info.class / multi-release META-INF entries
find $OUT/lib-classes -path "*/META-INF/*" -delete 2>/dev/null || true

echo "=== [2/7] Compiling resources ==="
$AAPT2 compile --dir $M/res -o $OUT/compiled/res.zip

echo "=== [3/7] Linking ==="
# shellcheck disable=SC2086
$AAPT2 link -o $OUT/base.apk \
  -I "$ANDROID_JAR" \
  --manifest $M/AndroidManifest.xml \
  -R $OUT/compiled/res.zip \
  $AAR_RES_ZIPS \
  --auto-add-overlay \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code $VER_CODE \
  --version-name "$VER_NAME" \
  --java $OUT/gen \
  --output-text-symbols $OUT/R.txt
echo "R.java files: $(find $OUT/gen -name 'R.java' | wc -l)"

echo "=== [3b/7] Generating library R.java ==="
python3 $PROJ/gen_lib_r.py
echo "lib R.java files: $(find $OUT/gen-lib-r -name 'R.java' | wc -l)"

echo "=== [4/7] Compiling Java ==="
$JAVA_HOME/bin/javac -encoding UTF-8 -nowarn \
  -cp "$CP" \
  -d $OUT/classes \
  $(find $M/java -name '*.java') \
  $(find $OUT/gen $OUT/gen-lib-r -name 'R.java')
[ -f $OUT/classes/com/zynelabs/iptv/R.class ] || { echo "FATAL: R.class missing"; exit 1; }
echo "app classes: $(find $OUT/classes -name '*.class' | wc -l)"
echo "lib classes: $(find $OUT/lib-classes -name '*.class' | wc -l)"

echo "=== [5/7] Dexing ==="
$D8 --min-api 24 --lib "$ANDROID_JAR" --output $OUT/dex \
  $(find $OUT/classes $OUT/lib-classes -name '*.class')
ls $OUT/dex/

echo "=== [6/7] Packaging, aligning, signing ==="
cp $OUT/base.apk $OUT/unsigned.apk
(cd $OUT/dex && zip -q -j $OUT/unsigned.apk classes*.dex)
$ZIPALIGN -p 4 $OUT/unsigned.apk $OUT/aligned.apk
$APKSIGNER sign --ks $KEYSTORE --ks-pass pass:android --out $OUT/$APK_NAME $OUT/aligned.apk
$APKSIGNER verify --print-certs $OUT/$APK_NAME | head -3
$ZIPALIGN -c -p 4 $OUT/$APK_NAME && echo "zipalign OK"

echo "=== [7/7] Verifying ==="
cp $OUT/$APK_NAME ~/workspace/user/files/$APK_NAME
ls -la ~/workspace/user/files/$APK_NAME
unzip -l ~/workspace/user/files/$APK_NAME | grep -c "classes.*\.dex"
echo "BUILD DONE"
