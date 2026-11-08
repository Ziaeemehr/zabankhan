# Video and Dictionary Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore more room to the watch transcript and dictionary, expose the compact player control, and align watch shadowing controls with the compact French Reader reference.

**Architecture:** Keep the existing shared watch/player and dictionary state flows. Make a focused watch-screen layout change, add a compact-only presentation path for the embedded dictionary, and add a compact-control option to the shared shadowing panel so other callers retain their current presentation.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3, Compose resources.

**Spec:** `docs/superpowers/specs/2026-11-08-video-dictionary-layout-design.md`

## Global Constraints

- Shared Kotlin/Compose implementation; no platform-specific APIs in `commonMain`.
- Preserve all existing callbacks and behavior; no horizontal scrolling for controls.
- Only the dictionary embedded in the watch screen uses the compact arrangement.
- Do not add or run tests unless the user asks for tests.
- Build Android and iOS and manually inspect watch, compact player, dictionary, and shadowing views on connected devices.
- User authorized a scoped commit and push after verification; exclude unrelated dirty files.

## Review Focus

- Small portrait screens: verify the player and action row leave useful transcript/dictionary height.
- Compact and expanded player: verify the always-visible toggle reflects and changes the active mode.
- Persian and French UI: verify header direction, wrapping controls, and video controls remain usable.
- Embedded dictionary: verify word editing, saving, provider selection, and web dictionary navigation remain available in the compact arrangement.
- Shadowing states: verify idle, recording, recognizing, result, and error controls keep the same behavior and provide accessible labels.

---

### Task 1: Compact and expose the watch player controls

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/watch/WatchScreen.kt`

**Interfaces:**
- Consumes: existing `WatchScreen`, `AppSettings.watchCompactPlayer`, `setWatchCompactPlayer`, `WatchActionButton`, and localized watch resources.
- Produces: visible header compact/expand toggle; 35%-height standard player cap; icon-first action row; `A−` and `A+` subtitle size menu actions.

- [x] Add a visible 48dp compact/expand button beside the watch title, using the existing compact/full labels as its content description; remove the duplicate compact toggle from the overflow menu.
- [x] Cap standard player height at `maxHeight * .35f`; retain the existing 96dp compact player and width constraints.
- [x] Convert the four frequent toolbar actions to an icon-first row with 40dp targets, semantic labels, and wrapped layout; preserve enabled/selected state and callbacks.
- [x] Keep subtitle size in the overflow menu and show explicit `A−` and `A+` controls around the current size indicator; preserve the existing size values and settings writes.
- [x] Build `:composeApp:assemblePlayDebug` and visually inspect expanded/compact player states on the connected Android device.

### Task 2: Reduce embedded dictionary control height

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/ui/reader/DictionarySheet.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/watch/WatchScreen.kt`

**Interfaces:**
- Consumes: `DictionaryPane`, `ReferenceDictionaryPane`, existing dictionary provider/action callbacks, and the compact video viewport from Task 1.
- Produces: a watch-only compact dictionary control layout; standalone dictionary presentation remains unchanged.

- [x] Add an explicit compact-controls parameter to `DictionaryPane`/`ReferenceDictionaryPane` and pass it only from `WatchScreen`.
- [x] In compact mode, use a wrapped action/provider group with 40dp targets for save, edit, browser, listen, close, and provider choices; keep content descriptions and provider selection behavior.
- [x] In compact mode, keep the meaning editor, list chooser, and save action on one slim row where available width allows, wrapping safely on narrow screens.
- [x] Preserve existing word editing, lemma selection, translation, save/delete, and web dictionary behavior; leave the regular dictionary layout unchanged.
- [x] Build Android and manually inspect the embedded dictionary on the connected phone.

### Task 3: Use compact icon controls for watch shadowing

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/speech/SpeechUi.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/watch/WatchScreen.kt`

**Interfaces:**
- Consumes: existing `ShadowingPanel` state, actions, and watch replay callback.
- Produces: an optional compact-control presentation enabled only by `WatchScreen`; all other callers retain the existing text-button presentation.

- [x] Add `compactControls: Boolean = false` to `ShadowingPanel` and pass `true` only from `WatchScreen`.
- [x] In compact mode, present listen/replay, microphone/stop, retry, next, and finish as wrapped 40dp icon buttons with the existing localized labels as content descriptions/tooltips.
- [x] Keep the sentence, phase feedback, summary, recognition state transitions, and recording behavior unchanged.
- [x] Build Android and manually inspect shadowing idle, recording, result, and error states where device permissions/state allow.

### Final verification

- [x] Run `git diff --check`.
- [x] Build Android Play Debug and the iOS device framework; build the iPad app with JDK 17.
- [x] Install and open the final Android and iPad apps on the previously authorized devices; visually inspect the watch player and dictionary layouts where reachable.
- [x] Record any interaction/state that could not be exercised manually. iPad was installed and opened, but app-level screen navigation was unavailable; watch/dictionary/shadowing inspection was completed on Android. No automated tests are added or run under the project instruction.
