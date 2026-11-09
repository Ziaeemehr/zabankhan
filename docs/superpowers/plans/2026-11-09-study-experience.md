# Study Experience Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce unnecessary cloud use and fix reader, card, and PDF-backup behavior.

**Architecture:** Add one persisted permission at the settings/repository boundary and make remote work explicit in the screen action. Keep reading/audio, card selection, and PDF inclusion changes in their existing feature modules, with behavior pinned by common tests.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, DataStore Preferences, kotlinx.coroutines, commonTest.

**Spec:** `docs/superpowers/specs/2026-11-09-study-experience.md`

## Global Constraints

- Common code must avoid `java.*`, `android.*`, `org.json`, and unsupported Kotlin/Native regex classes `\p{M}`/`\p{N}`.
- Preserve language direction from selected content; no horizontally scrolling menus or chip rows.
- Use JDK 17 for Gradle verification.
- Preserve pre-existing uncommitted statistics and study-log changes.

## Review Focus

- Cloud is disabled during an in-flight request: the UI must not launch subsequent requests until explicitly re-enabled.
- Empty/short video cues: no network work before the explicit action.
- Speech stops between sentence boundaries and within a sentence: resume at the captured offset without skipping or repeating completed text.
- Final chunk/page plus trailing whitespace/empty blocks: completion clamps to 100% without claiming completion early.
- Old backups lacking PDF metadata and new installs: restore behavior remains compatible, while new backups omit PDFs by default.

---

### Round 1: Cloud translation controls and video intent

**Files:** `data/Prefs.kt`, `translate/TranslationRepository.kt`, `app/AppContainer.kt`, dictionary UI in `ui/reader/DictionarySheet.kt`, `watch/WatchTranslation.kt`, `watch/WatchScreen.kt`, locale resources, focused translation/watch tests.

- [x] Add common tests proving disabled cloud permission prevents LLM invocation while cached/local translations still resolve.
- [x] Make subtitle translation state idle until explicit user request; test no call on visibility, one call on request, retry behavior.
- [x] Persist shared cloud permission and expose compact switches at dictionary and video translation controls.
- [x] Ensure explanatory translations are only requested for explicit selected content; avoid automatic bulk explanation translation.
- [x] Run focused common tests and inspect the complete round diff; fix issues before proceeding.

### Round 2: Reader playback, progress, and review-card dictionary

**Files:** `speech/ReaderPlayback.kt`, `speech/SpeechEngine.kt` or platform TTS adapters as needed, `ui/reader/ReadingScreen.kt`, `progress/Progress.kt`/reader progress helpers, `ui/ReviewScreen.kt`, tests.

- [x] Add regression tests for pause/resume offset and final-chunk completion.
- [x] Preserve current playback offset through pause and resume, including repeat mode.
- [x] Correct final reading progress calculation for both speech and page position.
- [x] Add selectable text to the revealed card face and route selection into the existing dictionary pane.
- [x] Run focused tests and review selection/playback lifecycle interactions.

### Round 3: PDF backup preference and comprehensive review

**Files:** `backup/BackupPrefs.kt`, `backup/LocalBackup.kt`, `backup/AutoBackup.kt`, `backup/BackupSettings.kt`, locale resources, backup tests.

- [x] Add tests showing PDF backup defaults off and the preference controls backup contents.
- [x] Change the default to off while retaining explicit stored user choices and compatible restore handling.
- [x] Add concise explanatory copy and PDF size beside the existing switch.
- [x] Run backup-focused tests, inspect all three rounds' combined diff, and correct regressions.
- [x] Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17; ./gradlew :composeApp:assemblePlayDebug :composeApp:assembleBazaarDebug :composeApp:testPlayDebugUnitTest :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosArm64`.
