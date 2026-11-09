# Floating Pen Toolbar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the PDF floating pen toolbar dismiss its expanded tools on outside taps, support adjustable stroke size, and switch between horizontal and vertical layouts.

**Architecture:** Keep toolbar state in `PdfReaderScreen` and presentation in `PdfInkToolbar`. Observe outside taps without consuming them, preserving PDF taps and drawing. Keep width normalized to page coordinates and retain the current `.004f` default.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3.

**Spec:** `docs/superpowers/specs/2026-11-08-pen-toolbar-and-dictionary-fallback-design.md`

## Global Constraints

- Shared code in `commonMain` must not use `java.*`, `android.*`, or `org.json`.
- Do not add or run tests unless the user asks to test or verify implementation.
- Keep toolbar controls wrapped; do not introduce horizontally scrolling controls.
- Preserve page-coordinate ink widths, toolbar dragging, minimize/restore, and stylus behavior.
- No commits.

## Review Focus

- Outside taps must collapse expanded controls without swallowing PDF taps, selection, or ink gestures.
- Horizontal and vertical layouts must remain inside screen bounds after orientation changes and dragging.
- Slider minimum, default, and maximum must preserve current thin/medium/thick stroke sizes.
- Pen, highlighter, and eraser controls must retain their existing state transitions.
- Minimized and restored states must preserve toolbar orientation and width.

---

### Task 1: Add non-consuming outside-tap collapse

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/pdf/PdfReaderScreen.kt`

**Interfaces:**
- Consumes: current `inkToolbarExpanded`, `toolbarFractionX/Y`, and toolbar size state.
- Produces: outside tap handling that sets `inkToolbarExpanded = false` without consuming the pointer event.

- [ ] Observe tap down/up in the PDF viewport while expanded and compare the pointer location against the toolbar's measured bounds.
- [ ] Collapse only the expanded section for taps outside those bounds; do not consume the event.
- [ ] Check `git diff --check` and review interaction ordering against the PDF's existing pointer handlers.

### Task 2: Add adjustable pen thickness

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/pdf/PdfReaderScreen.kt`

**Interfaces:**
- Consumes: existing normalized `penWidth` state, default `.004f`, and `onWidth(Float)` callback.
- Produces: one continuous slider with bounds `.0025f..0.007f`, default `.004f`.

- [ ] Replace the three preset-width buttons with a slider in the expanded tool area.
- [ ] Keep the existing `penWidth` value in saved state and keep highlighter's existing `3f` render multiplier.
- [ ] Check the changed layout and confirm width remains constrained to the specified range.

### Task 3: Add toolbar orientation toggle

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/pdf/PdfReaderScreen.kt`

**Interfaces:**
- Consumes: the toolbar's current expanded/minimized controls and drag calculations.
- Produces: saveable horizontal/vertical orientation state per PDF path and an orientation toggle in the main toolbar.

- [ ] Add saveable orientation state defaulting to horizontal.
- [ ] Arrange both the compact toolbar and expanded tools along the selected axis using wrapped controls.
- [ ] Re-measure and clamp toolbar position after an orientation switch so it remains in the viewport.
- [ ] Check that drag and minimize/restore continue working in both orientations.

### Final verification

- [ ] Run `git diff --check`.
- [ ] Run `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :composeApp:assemblePlayDebug :composeApp:assembleBazaarDebug :composeApp:testPlayDebugUnitTest :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosArm64`.
- [ ] Manually inspect outside-tap, slider, and both orientations on the PDF reader when a device is available.
