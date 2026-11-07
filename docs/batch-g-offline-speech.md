# Offline speech port

Source: read-only FrenchReaderSpike `shadowing/SpeechEngine.kt`, `MicRecorder.kt`,
`AudioPrep.kt`, `SharedModel.kt`, `VoskModelManager.kt`, `ui/CardShadowing.kt`,
`ui/ShadowingModelDialog.kt`, and the shadowing/cache settings and review sections.

Android uses `com.alphacephei:vosk-android:0.3.47` (Apache-2.0, same version as
French Reader). It includes native recognizer libraries and increases the APK
size for both Play and Bazaar (the cached AAR is 11.7 MiB compressed; libvosk.so
is 8.5 MiB uncompressed on arm64, before JNA/packaging); neither flavor needs
Google services. French
`vosk-model-small-fr-0.22` (~41 MB download) and English
`vosk-model-small-en-us-0.15` (~40 MB download) are Apache-2.0 models downloaded
separately into private, per-language storage. Expanded models take more space.
The dependency/model licenses must remain in release attribution.

Ktor and okio replace Java download/filesystem APIs; the existing checked
ZipArchive decoder replaces ZipInputStream. Imports and downloads validate in a
staging directory before replacing an installed model. Native model leases
serialize replacement/deletion against recognition. The cache retains active
playback leases and document pins while applying size, age, and clear settings.

On iOS Apple Speech replaces Vosk, with a settings status for each learning
language and requiresOnDeviceRecognition for the offline selection. There is no
Vosk download on iOS. Replay uses AVAudioFile/AVAudioPlayer in place of AudioTrack.
Android system-recognizer replay requires API 33 as in French Reader; Vosk replay
works on all supported Android versions. UI copies retain the reference layout;
model titles, locales, URLs and content direction come from LanguageSpec.

The legacy zabankhan auto_pronounce_cards flag meant front-only audio, so its
migration selects FRONT. French Reader's four autoplay states and back-side
sentence behavior are preserved. New pure-logic tests include English review
card targeting, autoplay migration, weighted pronunciation accuracy, model ZIP
validation, and cache pin/lease retention during age pruning and clearing.

Compose Resources cannot format `%.1f`; cache size is formatted in common Kotlin
and passed through `%s`. Its literal percent sign also replaces Android’s `%%`.
