# Project rules (zabankhan)

- Reply in the language the user writes in (Persian question → Persian reply).
- Stack: Kotlin Multiplatform + Compose Multiplatform, single module `composeApp` (commonMain/androidMain/iosMain) + `iosApp` (Swift host, generated with XcodeGen from `iosApp/project.yml`).
- Build with JDK 17: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17`. Android: `./gradlew :composeApp:assemblePlayDebug`. Verify gate: `./gradlew :composeApp:assemblePlayDebug :composeApp:assembleBazaarDebug :composeApp:testPlayDebugUnitTest :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosArm64` (the device link catches Kotlin/Native codegen bugs the simulator tests miss, e.g. `object X : NSObject()`). Common code must avoid regex classes `\p{M}`/`\p{N}` (unsupported on Kotlin/Native).
- Android flavors: `play` (may use Google Play services) and `bazaar` (no GMS at all, for Cafe Bazaar/Myket).
- Port logic from `/Users/ziaee/git/FrenchReaderSpike` (read-only reference; never modify it). Generalize every French assumption through `LanguageSpec`; no hardcoded "fr".
- Shared code in commonMain must not use java.*, android.*, org.json; use kotlinx-datetime, kotlinx-serialization, Ktor, okio (already on the classpath via DataStore) for files.
- Write implementation code via `codex exec` when practical; verify diffs and build yourself.
- UI: never use horizontally scrolling menus or chip rows. Options wrap (`FlowRow`) or go in a dropdown.
- Mixed RTL/LTR: direction follows the content (Persian RTL, French LTR) without flipping the whole screen.
- Test on the emulator (`fr_test` AVD) with the `.debug` package; never touch `com.ziaee.frenchreader` on any device.
