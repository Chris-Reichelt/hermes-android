#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export GRADLE_USER_HOME="$ROOT/toolchain/gradle-home"
export ANDROID_HOME="$ROOT/toolchain/android-sdk"
export ANDROID_USER_HOME="$ROOT/toolchain/android-user"
cd "$ROOT"
if [ ! -f "$ROOT/toolchain/android-preview-debug.keystore" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$ROOT/toolchain/android-preview-debug.keystore" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
node node_modules/vitest/vitest.mjs run --config vitest.android.config.ts > tests-js.log 2>&1
node node_modules/typescript/bin/tsc --ignoreConfig --noEmit --skipLibCheck --target es2022 --module esnext --moduleResolution bundler --lib dom,es2022 --types node apps/desktop/src/android/bridge.ts > adapter-typecheck.log 2>&1
(cd apps/desktop && node ../../node_modules/vite/bin/vite.js build --configLoader runner) > renderer-build.log 2>&1
node tests/phone-ui.mjs > phone-ui.log 2>&1
node tests/download-browser.mjs > artifacts/download-renderer.log 2>&1
node tests/media-playback-browser.mjs > artifacts/media-playback-fixed.log 2>&1
NODE_OPTIONS=--no-experimental-webstorage node node_modules/vitest/vitest.mjs run --config vitest.speech.config.ts > artifacts/auto-speech-unit-fixed.log 2>&1
node tests/auto-speech-browser.mjs > artifacts/auto-speech-browser.log 2>&1
DIRECT=1 node tests/auto-speech-browser.mjs > artifacts/auto-speech-browser-direct.log 2>&1
python3 - <<'PY'
from pathlib import Path
import shutil
root=Path.cwd()
out=root/'android/app/src/main/assets'
if out.exists(): shutil.rmtree(out)
shutil.copytree(root/'apps/desktop/dist',out)
PY
(cd android && "$ROOT/toolchain/gradle-8.11.1/bin/gradle" --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug) > android-build.log 2>&1
PYTHONDONTWRITEBYTECODE=1 /home/will/.hermes/hermes-agent/.venv/bin/python "$ROOT/tests/run-real-auth.py" > auth-integration.log 2>&1
mkdir -p artifacts
cp android/app/build/outputs/apk/debug/app-debug.apk artifacts/hermes-android-debug.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose artifacts/hermes-android-debug.apk > artifacts/apk-verification.txt
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging artifacts/hermes-android-debug.apk > artifacts/apk-badging.txt
sha256sum artifacts/hermes-android-debug.apk > artifacts/SHA256SUMS
printf 'Built and verified %s/artifacts/hermes-android-debug.apk\n' "$ROOT"
