# Store preparation

Version is defined once in `gradle.properties`: `APP_VERSION_NAME=0.1.0`, `APP_VERSION_CODE=1`. Android reads these directly. `iosApp/project.yml` has XcodeGen extract them to ignored `Version.xcconfig`, then sets `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION`; Info.plist expands those settings. Run `xcodegen` again after changing versions. Increase the build number for each store upload.

## Android signing and artifacts

Create and securely back up a release keystore, outside this repository. Create `~/.android/zabankhan-release.properties` (or pass `-PzabankhanSigningProps=/absolute/path/release.properties`) containing:

```properties
storeFile=/absolute/path/zabankhan-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Never commit these credentials. With no properties file, release artifacts are unsigned. An existing but invalid signing configuration should fail the build rather than silently switch keys.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :composeApp:bundlePlayRelease :composeApp:assembleBazaarRelease
```

Outputs under `composeApp/build/outputs/`:

- Google Play: `bundle/playRelease/composeApp-play-release.aab`.
- Cafe Bazaar/Myket: APK under `apk/bazaar/release/` (filename includes `unsigned` without signing).
- R8 mappings: `mapping/playRelease/mapping.txt` and `mapping/bazaarRelease/mapping.txt`. Archive per release.

Release shrinking keeps PDFBox, ML Kit reflection, Room implementations, generated serializers and JavaScript interface methods; networking/media libraries include consumer rules. JPEG2000 decoding/encoding is an optional, unbundled PDFBox codec; R8 suppresses only its two absent Gemalto classes. PDFs requiring that codec may not render their images correctly.

## Cafe Bazaar / Myket

Use the signed `bazaarRelease` APK. No Google sign-in, Drive or dynamically downloaded Play-services OCR is configured; OCR's model is bundled and works without the Play services app installed.

**Dependency caveat:** bundled ML Kit itself transitively includes `com.google.android.gms` support libraries and Google diagnostic transport. This is not a strictly GMS-free dependency graph. If the store requires zero GMS libraries, this remains a release blocker: a separately approved OCR replacement is needed. Do not remove transitive libraries blindly or represent this build as containing no Google code.

Audit the final merged manifest for each release, including dependencies. Current permissions:

| Permission | Reason |
|---|---|
| INTERNET | Translation, online voice, news/dictionaries, YouTube and SDK network requests |
| ACCESS_NETWORK_STATE | WorkManager network monitoring and SDK connectivity |
| RECORD_AUDIO | User-requested pronunciation/shadowing |
| POST_NOTIFICATIONS | Optional vocabulary reminder (runtime permission on Android 13+) |
| RECEIVE_BOOT_COMPLETED | Restore reminder/background scheduling after reboot |
| WAKE_LOCK | WorkManager background backup execution |
| FOREGROUND_SERVICE | WorkManager's bundled foreground execution support |
| `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX signature permission protecting internal dynamic receivers |

No camera, contacts, location, broad storage or exact-alarm permission is requested. Files use platform pickers. Cleartext HTTP is disabled: current production endpoints/custom LLM settings use HTTPS, Edge uses WSS; no LAN HTTP feature is exposed. The exported main activity handles launcher/share/process-text intents. App reminder receivers and the backup FileProvider are not exported. Dependency-exported scheduler/profile components require system/signature permissions. System backup is enabled; cache/no-backup locations are excluded by Android, and XML rules additionally exclude credential-bearing preferences, archive copies and restore staging.

## Google Play

Upload the signed Play AAB, configure Play App Signing, add screenshots, content rating, target audience, support contact and a public hosted URL for [PRIVACY.md](../PRIVACY.md).

Data safety must match the actual shipped dependencies and enabled services. Do **not** answer “no data collected” solely because the developer has no server.

| Question / category | Preparation answer |
|---|---|
| Accounts / account deletion | No app accounts; no account-deletion flow applies |
| Ads / tracking | No app advertising or cross-app tracking |
| App diagnostics, usage and identifiers | ML Kit reports device/app information, installation identifiers and performance/usage metrics to Google. Disclose relevant device identifiers, app interactions and diagnostics for analytics as specified by the SDK's current disclosure guide. |
| User content | Selected text/context goes to translation/LLM providers; text goes to online speech. Declare relevant other user-generated content for app functionality; optional LLM usage is user-selected. |
| Audio | Speech recognition may send audio to the installed platform service. Review the system-service exception and the exact recognition provider before answering. |
| Sharing | User-initiated dictionary, video and chosen-provider requests may qualify for the user-initiated sharing exception. This does not automatically exempt collection. |
| Ephemeral processing | Claim only when the receiving provider's retention policy actually supports it; do not assume free translation or LLM providers retain nothing. |
| Encryption in transit | App endpoints use HTTPS/WSS. Confirm embedded content and all providers before certifying the final form. |
| Deletion | Local data can be removed by uninstalling; exported backups must be deleted separately. Third-party retention follows that provider's policy; do not promise developer deletion of their records. |

References: [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure), [Play Data safety definitions and exceptions](https://support.google.com/googleplay/android-developer/answer/10787469). Recheck the live form at submission.

## App Store / TestFlight

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
cd iosApp
xcodegen
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Release -destination 'generic/platform=iOS' -derivedDataPath build/rel CODE_SIGNING_ALLOWED=NO build
```

This creates an unsigned device build, not an uploadable archive. A human must enroll in the paid Apple Developer Program for TestFlight, choose the signing team, create the App Store Connect app record for `com.ziaee.zabankhan`, configure signing/provisioning, archive in Xcode and distribute to App Store Connect. Complete export-compliance questions, age rating, screenshots, support/privacy URLs and review notes (including YouTube/caption behavior and provider configuration).

The initial privacy nutrition-label intent is **Data Not Collected**: no developer collection, analytics SDK or tracking in the iOS app, and the privacy manifest declares no collected data or tracking domains. Before submitting, verify the retention practices of translation, Edge, optional LLM and embedded YouTube services against [Apple's collection definition and optional disclosures](https://developer.apple.com/app-store/app-privacy-details/). Off-device retention by an integrated partner may require a different label; the absence of a developer server is insufficient proof. Android ML Kit diagnostics do not apply to iOS (Vision OCR).

Permission strings present in Info.plist:

- `NSMicrophoneUsageDescription`: pronunciation and shadowing.
- `NSSpeechRecognitionUsageDescription`: compare recognized speech to the learning sentence.
- Notifications use `UNUserNotificationCenter` runtime authorization for optional review reminders; iOS has no notification usage-description plist key.

`PrivacyInfo.xcprivacy` is copied through the `iosApp` source directory in project.yml. It declares UserDefaults `CA92.1` for app/framework preferences and file timestamps `C617.1` for local filesystem metadata used by Okio/Room/import/backup operations, plus `0A2A.1` for Compose filesystem wrappers, following [JetBrains’ privacy manifest guidance](https://kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html). The final linked executable calls `statfs`/`fstatfs` through bundled SQLite’s file opening/locking code, so Disk Space `E174.1` covers database file operations. The final executable has no `mach_absolute_time`/system-uptime import, so no System Boot Time reason is declared. Re-audit the final linked binary and dependency changes before upload. See [Apple's required-reason manifest guidance](https://developer.apple.com/documentation/technotes/tn3183-adding-required-reason-api-entries-to-your-privacy-manifest).

Icons include the universal 1024 px iOS asset and Android adaptive/fallback icons derived from `design/icon-crop.png`. Launch background follows the light/dark theme background. English and Persian `InfoPlist.strings` supply the display names.

## Verification before shipping

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :composeApp:assemblePlayDebug :composeApp:assembleBazaarDebug :composeApp:testPlayDebugUnitTest :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosArm64 --console=plain -q
```

`iosSimulatorArm64Test` executes on an iOS simulator. When simulator execution is prohibited, use `:composeApp:compileTestKotlinIosSimulatorArm64` to check compilation and record the runtime test as outstanding; that does not satisfy the full gate. Device-framework linking is still required. Do signed-device smoke testing and store validation before distribution; an unsigned build cannot establish signing or upload acceptance.
