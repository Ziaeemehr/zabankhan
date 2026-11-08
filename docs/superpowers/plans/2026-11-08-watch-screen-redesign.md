# Watch Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve navigation, tool organization, and transcript focus on the video screen, then install the app on the connected Android device and iPad.

**Architecture:** Presentation-only changes in `WatchScreen.kt`; keep video controllers, transcript models, settings storage, and navigation callbacks unchanged. Reorganize the screen into an accessible title/header, responsive player region, action menu/toolbar, focused transcript rows, and the existing bottom playback bar.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3, Gradle, XcodeGen/Xcode.

**Spec:** `docs/superpowers/specs/2026-11-08-watch-screen-redesign.md`

## Global Constraints

- Common code must not use Java or Android APIs.
- Preserve both standard and compact player modes and all existing actions.
- Use touch targets of at least 48dp where practical.
- Do not add horizontally scrolling chips or menus.
- Build Play/Bazaar and iOS; install Android `.debug` and the iOS app only on the user-authorized connected devices.
- No commits.

## Review Focus

- Standard mode on narrow portrait screens: header and toolbar must not crowd the player; verify responsive layout on Android.
- Compact mode: back/title remain reachable and the transcript still receives adequate height.
- Long/multilingual titles and RTL: title truncates cleanly without changing the player's LTR controls.
- Empty/error captions and dictionary overlay: retain existing retry, selection, and BackHandler behavior.
- iPad presentation: no popover/layout overflow; keep controls usable in landscape and portrait.

---

### Task 1: Restructure the watch screen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/watch/WatchScreen.kt`
- Test: manual on-device journey after installation; no player/model behavior changes are planned.

**Interfaces:**
- Consumes existing `WatchScreen`, `VideoController`, `TranscriptSentence`, `WatchPlaybackBar`, and existing localized strings.
- Produces the same `WatchScreen` public signature and keeps the same controller/settings callbacks.

- [x] Read current player state and supported compact behavior before changing layout.
- [x] Add an accessible 48dp Back action and title header; title uses loaded caption title, then video ID/path filename fallback.
- [x] Use an available-width 16:9 player in standard mode and preserve the compact player layout.
- [x] Keep frequent subtitle/translation/shadow/export actions visible and place subtitle sizing and playback speed in a labeled dropdown.
- [x] Style the active transcript sentence with a tonal surface and spacing while retaining its timestamp, translation, click, selection, and auto-follow behavior.
- [x] Build Android Play/Bazaar, Android unit tests, iOS simulator tests, and iOS device framework; build and install the iPad app after correcting its Xcode JDK environment.
- [x] Open the standard player screen on Android with a local preview video and subtitle; verify the header, readable toolbar labels, active transcript, and player are visible. iPad app is installed and open at Home; remote touch navigation is unavailable, so the watch screen was not visually checked there. Compact mode and each control's interaction were not individually exercised.
- [x] Inspect the final diff and run `git diff --check`.

## Self-review

- All visible changes are presentation-only and mapped to Task 1.
- Android and iPad installation and viewing are explicit acceptance items; check device discovery before build completion.
- Full round-trip playback verification requires the user devices and usable video/subtitle content; if a device cannot be contacted, report the exact blocker and still complete build checks.
