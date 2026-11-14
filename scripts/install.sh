#!/bin/zsh
# Build and install zabankhan on a device.
# Usage: scripts/install.sh <android|ipad> <debug|release> [play|bazaar]
#   android: flavor defaults to play. Pick the phone with ANDROID_SERIAL if several are connected.
#   ipad:    DEVICE_ID overrides the iPad id.
# iPad: free Apple ID profiles expire after 7 days, so reinstall weekly.
# Reinstalling over an existing app keeps its data. Do not delete the app first.
set -euo pipefail

usage() {
  cat >&2 <<'HELP'
Usage: scripts/install.sh <android|ipad> <debug|release> [play|bazaar]

Targets:
  android   Install on the connected Android phone (adb).
  ipad      Build with Xcode and install on the iPad.

Builds:
  debug     Package com.ziaee.zabankhan.debug (iOS: .dev). Separate app and data.
  release   Package com.ziaee.zabankhan, minified, signed with the release key.

Android flavors (ignored for ipad):
  play      Default. Uses Google Play services (Google Drive backup).
  bazaar    No Google services at all, for Cafe Bazaar / Myket.
            Same package name as play: installing one replaces the other.

Environment:
  ANDROID_SERIAL  Pick the phone when several are connected.
  DEVICE_ID       iPad id (default 00008122-0019308201B9801C).

Examples:
  scripts/install.sh android debug
  scripts/install.sh android release bazaar
  scripts/install.sh ipad release
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
  *) usage ;;
esac
