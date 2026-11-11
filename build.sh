#!/system/bin/sh
set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
WS="/data/user/0/com.deepseek.harness.compat/files/星球卑一号实验室"
P="$WS/.probe/tx"
NODEBIN="/data/user/0/com.deepseek.harness.compat/files/payload/runtime/bin/node"
NODELIB="/data/user/0/com.deepseek.harness.compat/files/payload/runtime/lib"
JAVA="$P/lib/jvm/java-21-openjdk/bin/java"
JAVAC="$P/lib/jvm/java-21-openjdk/bin/javac"
KEYTOOL="$P/lib/jvm/java-21-openjdk/bin/keytool"
AAPT2="$P/bin/aapt2"
KOTLINC="$P/opt/kotlin/lib/kotlin-compiler.jar"
STDLIB="$P/opt/kotlin/lib/kotlin-stdlib.jar"
R8="$WS/.probe/lib/r8.jar"
AS="$WS/.probe/lib/apksig.jar"
AJ="$WS/.probe/lib/x/plat/android-34/android.jar"
KS="$ROOT/ncmd.keystore"
APP="$ROOT/app/src/main"
OUT="$ROOT/build"
VC=$(cat "$ROOT/version.txt" 2>/dev/null | head -1 | cut -d' ' -f1)
VN=$(cat "$ROOT/version.txt" 2>/dev/null | head -1 | cut -d' ' -f2)
[ -z "$VC" ] && VC=1
[ -z "$VN" ] && VN="1.0"
export LD_LIBRARY_PATH="$P/lib"
export JAVA_HOME="$P"
export TMPDIR="$WS/.probe/tmp"
mkdir -p "$TMPDIR"

die() { echo "FAILED: $1"; exit 1; }
echo "== 1) manifest =="
rm -rf "$OUT"; mkdir -p "$OUT"
sed -e 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.ncmd">|' \
    "$APP/AndroidManifest.xml" > "$OUT/AndroidManifest.xml" || die "sed"
grep -q 'package="com.ncmd"' "$OUT/AndroidManifest.xml" || die "no package"

echo "== 2) res =="
"$AAPT2" compile --dir "$APP/res" -o "$OUT/res.zip" 2>&1 | tail -5
[ -f "$OUT/res.zip" ] || die "aapt2 compile"

echo "== 3) assets bundle =="
LD_LIBRARY_PATH="$NODELIB" "$NODEBIN" "$ROOT/bundle.mjs" "$APP/assets/ui" "$OUT/assets/ui" || die "bundle"

echo "== 3) link =="
"$AAPT2" link -o "$OUT/base.apk" -I "$AJ" --manifest "$OUT/AndroidManifest.xml" \
  -A "$OUT/assets" --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "$VC" --version-name "$VN" "$OUT/res.zip" 2>&1 | tail -15
[ -f "$OUT/base.apk" ] || die "aapt2 link"

echo "== 4) kotlinc =="
rm -rf "$OUT/classes"; mkdir -p "$OUT/classes"
TERM=dumb "$JAVA" -Xmx1200m -cp "$KOTLINC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -jvm-target 17 -nowarn -classpath "$AJ:$STDLIB" -d "$OUT/classes" \
  $(find "$APP/java" -name "*.kt") 2>&1 | grep -vi "warning\|jansi\|lck" | tail -40
[ -n "$(find "$OUT/classes" -name '*.class' 2>/dev/null)" ] || die "kotlinc produced nothing"

echo "== 5) dex =="
rm -rf "$OUT/dex"; mkdir -p "$OUT/dex"
CLASSES=$(find "$OUT/classes" -name "*.class" | tr '\n' ' ')
"$JAVA" -Xmx1200m -cp "$R8" com.android.tools.r8.D8 --release --min-api 26 --lib "$AJ" \
  --output "$OUT/dex" $CLASSES "$STDLIB" 2>&1 | grep -v "^Warning: Unexpected error during rewriting" | tail -8
[ -f "$OUT/dex/classes.dex" ] || die "d8"

echo "== 6) pack =="
LD_LIBRARY_PATH="$NODELIB" "$NODEBIN" "$ROOT/ziptool.mjs" add "$OUT/base.apk" "$OUT/dex/classes.dex" classes.dex || die "zip add"

echo "== 7) sign =="
if [ ! -f "$KS" ]; then
  "$KEYTOOL" -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias ncmd -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=NCMD,OU=Local,O=Local,L=Local,ST=Local,C=CN" >/dev/null 2>&1 || die "keytool"
fi
[ -f "$OUT/Sign.class" ] || "$JAVAC" -nowarn -encoding UTF-8 -classpath "$AS" -d "$OUT" "$WS/.probe/sign/Sign.java" >/dev/null 2>&1
rm -f "$OUT/app-release.apk"
"$JAVA" -cp "$AS:$OUT" Sign "$KS" android ncmd "$OUT/base.apk" "$OUT/app-release.apk" 2>&1 | tail -6
[ -s "$OUT/app-release.apk" ] || die "sign"
echo "-- result --"
ls -l "$OUT/app-release.apk"
"$AAPT2" dump badging "$OUT/app-release.apk" 2>&1 | head -4
echo "APK_DONE"
