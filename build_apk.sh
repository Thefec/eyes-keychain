#!/usr/bin/env bash
# Göz anahtarlık APK derleyici. Repo kökünden: bash build_apk.sh  → Goz.apk
set -u
ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app/EyesKeychainApp"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

command -v java >/dev/null || { echo "Java yok: apt-get install -y openjdk-17-jdk"; exit 1; }
GRADLE="$(command -v gradle || echo /opt/gradle/bin/gradle)"
[ -x "$GRADLE" ] || { echo "Gradle yok (8.x gerekli)"; exit 1; }

if [ ! -x "$SDKM" ]; then
  echo ">> Android komut satırı araçları indiriliyor (dl.google.com)"
  mkdir -p "$ANDROID_HOME/cmdline-tools" && cd "$ANDROID_HOME" || exit 1
  curl -fsS -o cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip || { echo "ENGEL: dl.google.com"; exit 1; }
  unzip -q -o cmdtools.zip && rm -rf cmdline-tools/latest && mv cmdline-tools/cmdline-tools cmdline-tools/latest 2>/dev/null || { mkdir -p tmp && unzip -q -o cmdtools.zip -d tmp && mv tmp/cmdline-tools cmdline-tools/latest && rm -rf tmp; }
fi
if [ ! -d "$ANDROID_HOME/platforms/android-35" ]; then
  echo ">> SDK platform 35 + build-tools kuruluyor"
  yes | "$SDKM" --licenses >/dev/null 2>&1
  "$SDKM" "platforms;android-35" "build-tools;35.0.0" "platform-tools" >/dev/null || { echo "ENGEL: dl.google.com (SDK paketleri)"; exit 1; }
fi
echo "sdk.dir=$ANDROID_HOME" > "$APP/local.properties"

cd "$APP" || exit 1
LOG="$ROOT/build.log"
for i in 1 2 3 4 5 6; do
  echo ">> Derleniyor (deneme $i)"
  "$GRADLE" --no-daemon -q --max-workers=1 assembleRelease >"$LOG" 2>&1 && break
  if grep -q "429" "$LOG"; then echo "   Maven 429, $((30*i)) sn bekleniyor"; sleep $((30*i)); continue; fi
  grep -E "^e: |What went wrong|Could not GET" -A2 "$LOG" | head -30; exit 1
done
APK="$APP/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "APK oluşmadı, $LOG dosyasına bak"; exit 1; }
cp "$APK" "$ROOT/Goz.apk" && echo ">> Tamam: $ROOT/Goz.apk"
