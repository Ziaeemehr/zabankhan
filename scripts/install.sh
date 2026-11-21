#!/bin/zsh
# Build and install zabankhan on a device.
# Usage: scripts/install.sh <android|ipad|mac> <debug|release> [play|bazaar]
#   android: flavor defaults to play. Pick the phone with ANDROID_SERIAL if several are connected.
#   ipad:    DEVICE_ID overrides the iPad id.
#   mac:     runs the iPad app on this Apple Silicon Mac (Designed for iPad), installed in ~/Applications.
# iPad: free Apple ID profiles expire after 7 days, so reinstall weekly.
# Reinstalling over an existing app keeps its data. Do not delete the app first.
set -euo pipefail

usage() {
  cat >&2 <<'HELP'
Usage: scripts/install.sh <android|ipad|mac> <debug|release> [play|bazaar]

Targets:
  android   Install on the connected Android phone (adb).
  ipad      Build with Xcode and install on the iPad.
  mac       Build the iPad app for this Mac and install it in ~/Applications.

Builds:
  debug     Package com.ziaee.zabankhan.debug (iOS: .dev). Separate app and data.
  release   Package com.ziaee.zabankhan, minified, signed with the release key.

Android flavors (ignored for ipad and mac):
  play      Default. Uses Google Play services (Google Drive backup).
  bazaar    No Google services at all, for Cafe Bazaar / Myket.
            Same package name as play: installing one replaces the other.

Environment:
  ANDROID_SERIAL  Use this adb device as is (skips Wi-Fi connect).
  PHONE_IP        Phone Wi-Fi IP (default 192.168.1.71). Android installs over Wi-Fi, port 5555.
  DEVICE_ID       iPad id (default 00008122-0019308201B9801C). Works over Wi-Fi
                  once the iPad is paired and "Connect via network" is on in Xcode.

Examples:
  scripts/install.sh android debug
  scripts/install.sh android release bazaar
  scripts/install.sh ipad release
  scripts/install.sh mac debug
HELP
  exit 1
}
[[ ${1:-} == -h || ${1:-} == --help ]] && usage
[[ $# -ge 2 ]] || usage
TARGET=$1 BUILD=$2 FLAVOR=${3:-play}
[[ $BUILD == debug || $BUILD == release ]] || usage
[[ $FLAVOR == play || $FLAVOR == bazaar ]] || usage

export JAVA_HOME=/opt/homebrew/opt/openjdk@17
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

case $TARGET in
  android)
    # Release needs ~/.android/zabankhan-release.properties; without it the APK is unsigned and install fails.
    cd "$ROOT"
    if [[ -z ${ANDROID_SERIAL:-} ]]; then
      PHONE="${PHONE_IP:-192.168.1.71}:5555"
      # USB attached: switch it to Wi-Fi mode (needed once after each phone reboot).
      adb devices | grep -qv -e '^List' -e ':5555' -e '^$' && { adb -d tcpip 5555 || true; sleep 2; }
      if ! adb connect "$PHONE" | grep -q connected; then
        # A sandboxed adb server reports "No route to host"; a fresh server fixes it.
        adb kill-server; adb start-server
        adb connect "$PHONE" | grep -q connected || { echo "Cannot reach $PHONE. Plug in USB once, or set PHONE_IP." >&2; exit 1; }
      fi
      export ANDROID_SERIAL=$PHONE
    fi
    ./gradlew ":composeApp:install${(C)FLAVOR}${(C)BUILD}"
    ;;
  ipad)
    DEVICE_ID="${DEVICE_ID:-00008122-0019308201B9801C}"
    CONFIG=${(C)BUILD}
    cd "$ROOT/iosApp"
    xcodegen generate
    xcodebuild \
      -project iosApp.xcodeproj \
      -scheme iosApp \
      -configuration "$CONFIG" \
      -destination "platform=iOS,id=$DEVICE_ID" \
      -derivedDataPath "build/ipad-$BUILD" \
      -allowProvisioningUpdates \
      build
    xcrun devicectl device install app \
      --device "$DEVICE_ID" \
      "build/ipad-$BUILD/Build/Products/$CONFIG-iphoneos/Zabankhan.app"
    echo "Installed. Next reinstall due: $(date -v+6d '+%Y-%m-%d')"
    ;;
  mac)
    CONFIG=${(C)BUILD}
    APP_NAME=Zabankhan; [[ $BUILD == debug ]] && APP_NAME="Zabankhan Dev"
    DEST="$HOME/Applications/$APP_NAME.app"
    cd "$ROOT/iosApp"
    xcodegen generate
    xcodebuild \
      -project iosApp.xcodeproj \
      -scheme iosApp \
      -configuration "$CONFIG" \
      -destination "platform=macOS,variant=Designed for iPad" \
      -derivedDataPath "build/mac-$BUILD" \
      -allowProvisioningUpdates \
      build || exit 1
    APP_PATH="build/mac-$BUILD/Build/Products/$CONFIG-iphoneos/Zabankhan.app"
    if [[ ! -d "$APP_PATH" ]]; then
      echo "Build did not produce $APP_PATH" >&2
      exit 1
    fi
    # macOS opens an iOS app only inside a Wrapper bundle, the same layout Xcode uses.
    pkill -f -- "${DEST//./\\.}/" || true
    rm -rf "$DEST"
    mkdir -p "$DEST/Wrapper"
    cp -R "$APP_PATH" "$DEST/Wrapper/"
    ln -s Wrapper/Zabankhan.app "$DEST/WrappedBundle"
    open "$DEST"
    echo "Installed $DEST. Next reinstall due: $(date -v+6d '+%Y-%m-%d')"
    ;;
  *) usage ;;
esac
