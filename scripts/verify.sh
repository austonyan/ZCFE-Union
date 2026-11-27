#!/usr/bin/env bash
# 一键复跑本项目学习通功能的验收项。需要：JDK、Android SDK（apksigner）、已登录的 gh、7890 代理。
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
export HTTPS_PROXY=${HTTPS_PROXY:-http://127.0.0.1:7890}
export HTTP_PROXY=${HTTP_PROXY:-http://127.0.0.1:7890}
SDK=${ANDROID_SDK_ROOT:-"/d/Program Files (x86)/Android SDK"}
APK=${APK:-"郑财校园-release-v0.2.55.apk"}

echo "== 1. 单元测试 =="
./gradlew.bat :app:testDebugUnitTest --console=plain -q || exit 1
python - <<'PY'
import glob, xml.etree.ElementTree as ET
tests = fails = 0
for f in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    r = ET.parse(f).getroot()
    tests += int(r.get('tests', 0)); fails += int(r.get('failures', 0)) + int(r.get('errors', 0))
print(f"unit tests: {tests}, failed: {fails}")
raise SystemExit(1 if fails else 0)
PY

echo "== 2. APK 签名与版本 =="
"$SDK/build-tools/36.0.0/apksigner.bat" verify --verbose "$APK" | grep -E "v2 scheme"
sha256sum "$APK" | awk '{print "sha256:", toupper($1)}'

echo "== 3. 远端与 Release =="
gh api repos/austonyan/ZCFE-Union/commits/master --jq '"remote: " + .sha'
git rev-parse --short HEAD | awk '{print "local:  " $1}'
gh release view v0.2.55 --json assets --jq '"asset:  " + .assets[0].name + " (" + (.assets[0].size|tostring) + " bytes)"'

echo "== 4. 设备（可选）=="
ADB=$(command -v adb 2>/dev/null || true)
[ -z "$ADB" ] && [ -x "$SDK/platform-tools/adb.exe" ] && ADB="$SDK/platform-tools/adb.exe"
if [ -n "$ADB" ]; then
  "$ADB" -s 127.0.0.1:16384 shell "dumpsys package com.union.campus | grep versionName" 2>/dev/null || true
  "$ADB" -s 127.0.0.1:16384 shell "dumpsys jobscheduler" 2>/dev/null | grep -A6 "com.union.campus/androidx.work" | grep "Minimum latency" || true
else
  echo "adb 不在 PATH，跳过设备检查"
fi
