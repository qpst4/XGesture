#!/usr/bin/env bash
# Verify release APK versionCode/versionName match app/build.gradle.kts.
# Usage:
#   verify-release-apk.sh [full|lite|all] [gradle-file]
#   verify-release-apk.sh <apk-path> [full|lite]
set -euo pipefail

GRADLE_FILE="${GRADLE_FILE:-app/build.gradle.kts}"
TARGET="all"
APK_PATH=""

if [[ $# -ge 1 ]]; then
  case "$1" in
    full|lite|all)
      TARGET="$1"
      GRADLE_FILE="${2:-$GRADLE_FILE}"
      ;;
    *)
      APK_PATH="$1"
      TARGET="${2:-auto}"
      GRADLE_FILE="${3:-$GRADLE_FILE}"
      ;;
  esac
fi

if [[ ! -f "$GRADLE_FILE" ]]; then
  echo "ERROR: Gradle file not found: $GRADLE_FILE" >&2
  exit 1
fi

expected_code="$(grep -E 'versionCode[[:space:]]*=' "$GRADLE_FILE" | head -1 | sed -E 's/.*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/')"
expected_name="$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE" | head -1 | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/')"

if [[ -z "$expected_code" || -z "$expected_name" ]]; then
  echo "ERROR: Failed to parse version from $GRADLE_FILE" >&2
  exit 1
fi

find_aapt() {
  local bases=(
    "${ANDROID_SDK_ROOT:-}/build-tools"
    "${ANDROID_HOME:-}/build-tools"
    "${HOME}/.android/sdk/build-tools"
    "${HOME}/Android/Sdk/build-tools"
  )
  local base aapt
  for base in "${bases[@]}"; do
    [[ -d "$base" ]] || continue
    aapt="$(find "$base" -name aapt -type f 2>/dev/null | sort -V | tail -1)"
    if [[ -n "$aapt" ]]; then
      echo "$aapt"
      return 0
    fi
  done
  return 1
}

AAPT="$(find_aapt || true)"
if [[ -z "$AAPT" ]]; then
  echo "ERROR: aapt not found. Set ANDROID_SDK_ROOT or install Android build-tools." >&2
  exit 1
fi

detect_variant() {
  local path="$1"
  if [[ "$path" == *"-lite.apk" ]]; then
    echo "lite"
  elif [[ "$path" == *"-full.apk" ]]; then
    echo "full"
  else
    echo "unknown"
  fi
}

verify_one_apk() {
  local apk="$1"
  local variant="$2"

  if [[ ! -f "$apk" ]]; then
    echo "ERROR: APK not found: $apk" >&2
    exit 1
  fi

  if [[ "$variant" == "auto" ]]; then
    variant="$(detect_variant "$apk")"
  fi
  if [[ "$variant" != "full" && "$variant" != "lite" ]]; then
    echo "ERROR: Unknown APK variant for: $apk (expected -full.apk or -lite.apk suffix)" >&2
    exit 1
  fi

  echo "==> Verifying $variant APK: $apk"

  badging="$("$AAPT" dump badging "$apk")"
  actual_code="$(printf '%s\n' "$badging" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -1)"
  actual_name="$(printf '%s\n' "$badging" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"

  if [[ -z "$actual_code" || -z "$actual_name" ]]; then
    echo "ERROR: Failed to parse APK version from: $apk" >&2
    exit 1
  fi

  echo "Expected: versionCode=$expected_code versionName=$expected_name"
  echo "Actual:   versionCode=$actual_code versionName=$actual_name"

  if [[ "$actual_code" != "$expected_code" || "$actual_name" != "$expected_name" ]]; then
    echo "ERROR: Release APK version mismatch." >&2
    exit 1
  fi

  echo "OK: Release APK version matches $GRADLE_FILE"

  apk_bytes="$(wc -c < "$apk" | tr -d ' ')"
  echo "APK size: $apk_bytes bytes"

  if ! command -v unzip >/dev/null 2>&1; then
    echo "ERROR: unzip not found; required to verify bundled native engine assets." >&2
    exit 1
  fi

  required_packs=(ocr-engine translate-engine)
  if [[ "$variant" == "full" ]]; then
    for pack in "${required_packs[@]}"; do
      asset_path="assets/bundled-native-engine/${pack}.zip"
      if ! unzip -l "$apk" "$asset_path" >/dev/null 2>&1; then
        echo "ERROR: Missing bundled asset in full APK: $asset_path" >&2
        exit 1
      fi
      echo "OK: Found $asset_path"
    done
    echo "OK: Full release APK bundled native engine assets verified"
    local min_bytes="${MIN_RELEASE_FULL_APK_BYTES:-35000000}"
    if [[ "$apk_bytes" -lt "$min_bytes" ]]; then
      echo "ERROR: Full release APK too small (min ${min_bytes} bytes); bundled native engine packs may be missing or empty." >&2
      exit 1
    fi
  else
    for pack in "${required_packs[@]}"; do
      asset_path="assets/bundled-native-engine/${pack}.zip"
      if unzip -l "$apk" "$asset_path" >/dev/null 2>&1; then
        echo "ERROR: Lite release APK must not bundle native engine asset: $asset_path" >&2
        exit 1
      fi
    done
    echo "OK: Lite release APK has no bundled native engine assets"
    local max_bytes="${MAX_RELEASE_LITE_APK_BYTES:-36700160}" # 35 MiB（2026-10-20 由 32 MiB 上调：图标库三个字体使 lite 实测 33,648,752 B）
    if [[ "$apk_bytes" -gt "$max_bytes" ]]; then
      echo "ERROR: Lite release APK too large (max ${max_bytes} bytes)." >&2
      exit 1
    fi
  fi
}

if [[ -n "$APK_PATH" ]]; then
  verify_one_apk "$APK_PATH" "$TARGET"
  exit 0
fi

if [[ "$TARGET" == "all" || "$TARGET" == "full" ]]; then
  verify_one_apk "app/build/outputs/apk/full/release/xgesture-${expected_name}-full.apk" "full"
fi
if [[ "$TARGET" == "all" || "$TARGET" == "lite" ]]; then
  verify_one_apk "app/build/outputs/apk/lite/release/xgesture-${expected_name}-lite.apk" "lite"
fi
