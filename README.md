# Zabankhan · زبان‌خوان

A Kotlin Multiplatform + Compose Multiplatform language reader for Android and iOS, succeeding French Reader. Learn French or English with Persian or English meanings.

- Read imported text, Markdown, EPUB and PDFs with word lookup, translation, highlights and OCR.
- Save vocabulary and review it with a Leitner schedule; track study activity.
- Watch YouTube or local videos with tappable subtitles; practice listening and pronunciation.
- Browse news and encyclopedia content, import Anki/CSV vocabulary, and make local backups.

## Build

Use JDK 17, the Android SDK, and (for iOS) macOS with Xcode and XcodeGen.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :composeApp:assemblePlayDebug :composeApp:assembleBazaarDebug
./gradlew :composeApp:testPlayDebugUnitTest :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosArm64
./gradlew :composeApp:bundlePlayRelease :composeApp:assembleBazaarRelease
cd iosApp
xcodegen
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Release -destination 'generic/platform=iOS' -derivedDataPath build/rel CODE_SIGNING_ALLOWED=NO build
```

Android debug packages have the `.debug` suffix. `bazaar` needs no installed Google Play services app; bundled ML Kit still brings Google support libraries and diagnostics (see store notes). Release builds are unsigned unless a signing properties file is supplied. Versions come from `APP_VERSION_NAME` and `APP_VERSION_CODE` in `gradle.properties`; regenerate the Xcode project after configuration changes.

See [store preparation](docs/STORE.md) for signing, artifacts and submission steps, and the [privacy policy](PRIVACY.md) for online services and backups. No account or developer server is required.
