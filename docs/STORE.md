# Store preparation

Version is defined once in `gradle.properties`: `APP_VERSION_NAME=1.0.0`, `APP_VERSION_CODE=1`. Android reads these directly. `iosApp/project.yml` has XcodeGen extract them to `Version.xcconfig`, then sets `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION`; Info.plist expands those settings. Run `xcodegen` again after changing versions. Increase the build number for each store upload.

## Android signing and artifacts

Create and securely back up a release keystore, outside this repository. Create `~/.android/zabankhan-release.properties` (or pass `-PzabankhanSigningProps=/absolute/path/release.properties`) containing:

```properties
storeFile=/absolute/path/zabankhan-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Never commit these credentials. With no properties file, release artifacts are unsigned. An existing but invalid signing configuration should fail the build rather than silently switch keys.

### GitHub Releases

The `Release` workflow in `.github/workflows/release.yml` builds a signed Play APK and Play AAB, and attaches them to a GitHub release with the Play R8 mapping and `SHA256SUMS`. Users can install the APK directly; the AAB is for Google Play submission. It does not upload to stores.

For iOS, GitHub automatically provides **Source code (zip)** and **Source code (tar.gz)** for the published version. Users can download the source and build with Xcode using the instructions below and their own Apple signing setup. No IPA is published: general iOS distribution requires TestFlight/App Store, or an Ad Hoc profile covering each registered device. Source archives are not installable apps.

Before running it, add these repository secrets under **Settings → Secrets and variables → Actions** using your existing release keystore:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | The keystore encoded with `base64 -i /path/to/zabankhan-release.jks \| pbcopy` on macOS |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password (`storePassword`) |
| `ANDROID_KEY_ALIAS` | Signing key alias (`keyAlias`) |
| `ANDROID_KEY_PASSWORD` | Signing key password (`keyPassword`) |

Missing secrets stop the workflow before building. Signing files are temporary and removed after the job.

For the first release, use `APP_VERSION_NAME=1.0.0` and `APP_VERSION_CODE=1`. For subsequent releases, update both values in `gradle.properties` and push those changes; use a higher build number for every store upload.

- **Manual:** open **Actions → Release → Run workflow**, select the branch containing the version to release, and run. **Create a draft release for review** defaults to enabled. Review the assets in **Releases**, then publish the draft. Uncheck the option to publish immediately. The workflow derives `v<APP_VERSION_NAME>` and creates the tag for the selected commit when the release is published.
- **Tag:** push a tag matching the committed version, for example `git tag v1.0.0` then `git push origin v1.0.0`. This builds and publishes the release automatically.

The tag must match the version in `gradle.properties`. An existing tag must point to the selected commit; an existing release (including a draft) stops the workflow to prevent overwriting its files. If a run fails after creating a draft, inspect that draft before deciding whether to delete it and retry. The workflow needs permission to write repository contents through its automatic `GITHUB_TOKEN`; no personal access token is needed.

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

Decision (2026-11-06): keep bundled ML Kit in `bazaar`. Audited `bazaarReleaseRuntimeClasspath`; every Google artifact comes through `com.google.mlkit:text-recognition:16.0.1`:

- `com.google.android.gms`: `play-services-base`, `play-services-basement`, `play-services-tasks`, `play-services-mlkit-text-recognition`, `play-services-mlkit-text-recognition-common`
- `com.google.mlkit`: `common`, `vision-common`, `vision-interfaces`, `text-recognition-bundled-common`
- `com.google.android.datatransport`: `transport-api`, `transport-backend-cct`, `transport-runtime`
- `com.google.firebase`: `firebase-components`, `firebase-encoders`, `firebase-encoders-json`, `firebase-annotations`

Recheck with `./gradlew :composeApp:dependencies --configuration bazaarReleaseRuntimeClasspath` after any dependency change. If Bazaar or Myket rejects the build for these libraries, replace OCR in the `bazaar` flavor only.

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

No camera, contacts, location, broad storage or exact-alarm permission is requested. Files use platform pickers. Cleartext HTTP is disabled: current production endpoints/custom LLM settings use HTTPS, Edge uses WSS; no LAN HTTP feature is exposed. The exported main activity handles launcher/share/process-text intents. App reminder receivers and the backup FileProvider are not exported. Dependency-exported scheduler/profile components require system/signature permissions. System backup is enabled; cache/no-backup locations are excluded by Android, and XML rules additionally exclude settings and encrypted credential preferences, archive copies and restore staging.

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

Before signing, register the App Group `group.com.ziaee.zabankhan` in the Apple Developer account and enable it for both bundle IDs: `com.ziaee.zabankhan` and `com.ziaee.zabankhan.share`.

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

## Google Drive backup setup

Drive is optional on Android `play` and iOS; `bazaar` has an unavailable auth implementation and does not depend on `play-services-auth`. Only `https://www.googleapis.com/auth/drive.appdata` is requested. The app uploads the existing local ZIP format to the hidden `appDataFolder`, retains the newest five matching backups and validates downloaded ZIPs before offering the existing replace-data confirmation. Disconnect revokes access; it does not delete existing backups. Automatic cloud upload is not enabled; the daily WorkManager job continues to create local backups only.

1. In [Google Cloud Console](https://console.cloud.google.com/), create/select a project. Under **APIs & Services → Library**, enable **Google Drive API**.
2. Configure **Google Auth Platform → Branding, Audience and Data Access** (formerly OAuth consent screen). Enter the app name, support email, developer contact and hosted privacy-policy URL. Choose the appropriate audience; while External is in Testing, add test users. Add only `https://www.googleapis.com/auth/drive.appdata`. Publish the consent configuration when ready, following any verification requirements shown by Google. Testing-mode refresh credentials may expire; test production authorization before release.
3. Under **Clients → Create client → Android**, create clients for package `com.ziaee.zabankhan` and debug package `com.ziaee.zabankhan.debug`. Register the SHA-1 for each signing certificate used with each package. Obtain debug SHA-1 with `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey` (standard debug password: `android`); obtain release SHA-1 with `keytool -list -v -keystore /path/to/release.jks -alias YOUR_ALIAS`. For Play-distributed builds, also register the **app signing certificate** SHA-1 from Play Console → App integrity; the upload certificate alone is insufficient. No Android client ID or secret is embedded: AuthorizationClient identifies the package/signature. The Play Drive section is enabled by the flavor; without Cloud registration, authorization fails with a user-facing error.
4. Create an **iOS** OAuth client for bundle ID `com.ziaee.zabankhan`. Set `ZABANKHAN_GOOGLE_CLIENT_ID` in the `iosApp` target build settings in `iosApp/project.yml` to the complete client ID, e.g. `123-abc.apps.googleusercontent.com`. Set `ZABANKHAN_GOOGLE_REDIRECT_SCHEME` to its reversed form, e.g. `com.googleusercontent.apps.123-abc`. The callback is `<reversed-client-id>:/oauth2redirect`. Run `xcodegen` after editing project.yml. Info.plist expands these into `ZabankhanGoogleClientId` and `CFBundleURLSchemes`. Both build settings are empty by default; an empty client ID hides the iOS Drive section. Do not add a client secret.
5. Test connect/cancel, upload, six uploads (only five remain), restore confirmation/cancel, restore into another installation, expired/revoked access, network failure and disconnect. The iOS refresh token uses Keychain service `com.ziaee.zabankhan.drive`, device-only and accessible while unlocked; access tokens are not persisted. A Keychain item may survive uninstall; disconnect before uninstall if access should be removed.

Reviewer gate: run the full build/test gate above, regenerate the Xcode project, and inspect `./gradlew :composeApp:dependencies --configuration bazaarReleaseRuntimeClasspath`; `play-services-auth` must be absent (the pre-existing ML Kit caveat above still applies). Also test authorization on signed Play/debug and iOS installations with configured Cloud clients.

Data safety/privacy labels: optional Drive transfers contain the same user content/settings as local backups and are sent to Google on explicit user action. Assess this against the current [Play Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469) and [Apple privacy disclosures](https://developer.apple.com/app-store/app-privacy-details/) for the shipped configuration; a user-owned app folder does not itself settle collection/sharing answers. Describe the optional transfer and Google's processing/retention. No developer server receives the backup.

Implementation references: [Android authorization](https://developer.android.com/identity/authorization), [Google native OAuth / PKCE](https://developers.google.com/identity/protocols/oauth2/native-app), [Drive application data](https://developers.google.com/workspace/drive/api/guides/appdata).

The dictionary panel loads the selected provider’s website in an embedded browser; the looked-up word is sent to that provider.
