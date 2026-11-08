# Compact Video Dictionary Implementation Plan

> **For agentic workers:** Execute this plan in the current session.

**Goal:** Remove one stacked row from the embedded video dictionary so its lookup content has more vertical room.

**Architecture:** Merge provider selection icons into the existing compact word action FlowRow in `DictionarySheet.kt`. Keep semantic labels, wrapped layout, and the standalone controls used outside video. Do not change lookup or save behavior.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform.

**Spec:** `docs/superpowers/specs/2026-11-08-video-dictionary-layout-design.md`

## Global Constraints

- Shared code in `commonMain` must not use platform APIs.
- Controls wrap; no horizontally scrolling menus or chip rows.
- Preserve callbacks and behavior.
- Do not add or run tests unless requested.

## Review Focus

- Compact video dictionary: verify all action and provider icons wrap accessibly.
- Non-video dictionary: verify its existing provider row remains unchanged.

---

### Task 1: Merge compact dictionary action and provider rows

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/ui/reader/DictionarySheet.kt`

- [x] Replace compact provider selection buttons with one current-source dropdown in the existing word-action `FlowRow`; collapse duplicate save/edit actions while retaining provider selection behavior and semantics.
- [x] Keep the original separate provider row for non-compact dictionary presentations.
- [x] `git diff --check` and Android/iOS build gates passed; visually inspected the embedded dictionary on Android, and installed/launched the app on iPad.
