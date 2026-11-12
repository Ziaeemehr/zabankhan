# Comm_diag_a2b1 Farsi to French Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one downloadable and current-release-importable Farsi-to-French deck from *Communication en dialogues A2-B1*, and make Words review honor the active search and status filters.

**Architecture:** Convert the existing 1,354-card source deck into Persian-front/French-back cards, carrying its 24 unit labels into searchable meaning text so sentence audio does not read them; publish the same pack in the app's bundled dataset and `zabankhan-dict/packs`. Ship a CSV with the deck for current-release import. Pass the filtered card IDs from Words through shared app state alongside the existing scoped-review route so Review calculates and queues only those cards.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlinx.serialization JSON, CSV importer, Python standard library for generated dataset files.

**Spec:** `docs/superpowers/specs/2026-11-11-comm-diag-farsi-french-design.md`

## Global Constraints

- Shared code must not use `java.*`, `android.*`, or `org.json`.
- Keep one book deck; use unit labels searchable in Words instead of creating 24 decks.
- CSV columns must be `word,meaning,sentence,deck` for compatibility with the current release.
- Keep each card concise and use original Persian equivalents; do not reproduce dialogue passages.
- Preserve existing unrelated modifications in both repositories; do not commit.

## Review Focus

- Search plus status filters yield the same review subset as the visible Words list; test matching, no matches, and status-only filtering.
- Existing whole-deck review remains whole-deck when no filtered ID set is supplied; test null and empty subset semantics separately.
- Persian equivalents do not accidentally include the English gloss; validate representative entries from the first and last units.
- Unit labels remain present in `meaning` and searchable after both CSV import and dataset installation; examples in `sentence` contain only French example text and are safe to send to TTS. Validate all 24 labels and counts. Empty filter result disables review.
- The card pack is parseable by the existing dataset parser, and CSV rows are parseable by the current release importer; assert every generated card has a front, French meaning, unit label, and valid chapter.

---

### Task 1: Make Review use the active Words filters

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/ui/VocabListScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/ui/WordsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/ui/ReviewScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/ziaee/zabankhan/ui/ReviewScopeTest.kt`

**Interfaces:**
- Words supplies the IDs of its current `filtered` results along with `selectedScope` when opening scoped review.
- Change `VocabListScreen.onOpenReview` to `(Long, Set<Long>) -> Unit`, and `WordsScreen.reviewScope` to `((Long, Set<Long>) -> Unit)?`.
- The App holds the set in state (not in the route string; it can exceed 1,000 IDs) while the `review-scope/{listId}` route is active and passes it as `reviewEntryIds: Set<Long>?` to `ReviewScreen` and `ReviewViewModel`. `null` preserves current whole-deck behavior; a non-null set restricts review to those IDs.

- [x] **Step 1: Add a failing scope test** for the review-entry filter: null preserves all entries in the selected scope, a set selects only matching IDs, an empty set selects none, and an ID from another deck is excluded.
- [x] **Step 2: Run the focused test** `./gradlew :composeApp:testPlayDebugUnitTest --tests 'com.ziaee.zabankhan.ui.ReviewScopeTest'`; confirm it fails because the filter helper is absent.
- [x] **Step 3: Implement the pure entry-scope helper** in `ReviewScreen.kt` or the existing review-session module, and use it in `ReviewViewModel.scopedEntries()`.
- [x] **Step 4: Update Words review navigation** to pass `filtered.mapTo(mutableSetOf()) { it.id }`, keep the selected list scope, store the set while the scoped-review route is active, and supply it as `reviewEntryIds` to `ReviewScreen`.
- [x] **Step 5: Make the Words due count use `filtered`** so the count and review queue describe the same current search/status results. Disable the review button when `filtered` is empty; never fall back to whole-deck review in that case.
- [x] **Step 6: Run the focused test** `./gradlew :composeApp:testPlayDebugUnitTest --tests 'com.ziaee.zabankhan.ui.ReviewScopeTest'` and confirm it passes.

### Task 2: Build the reverse-direction deck and import file

**Files:**
- Create: `tools/build_comm_diag_a2b1.py`
- Create: `docs/flashcards/Comm_diag_a2b1.csv`
- Create: `composeApp/src/commonMain/composeResources/files/fr/dataset/decks/comm-diag-a2b1-fa-fr.json`
- Modify: `composeApp/src/commonMain/composeResources/files/fr/dataset/manifest.json`
- Test: `composeApp/src/iosTest/kotlin/com/ziaee/zabankhan/content/CommDiagDeckTest.kt` (not commonTest: `Res.readBytes` fails in Android unit tests, and test fixtures under commonTest composeResources broke resource generation before)
- Self-check: assert-based checks inside the generator

**Interfaces:**
- Generator reads `comm-dial-a2b1-phrases.json` and emits the app dataset JSON and `word,meaning,sentence,deck` CSV.
- Dataset ID: `comm-diag-a2b1-fa-fr`; display/deck name: `Comm_diag_a2b1 · FA→FR`.
- Each row has Persian `word`, French translation, up to two known French synonyms, and searchable `Unité NN — <chapter title>` metadata in `meaning`. `sentence` contains only the French example so sentence TTS does not read unit labels.

- [x] **Step 1: Add a failing data validation test** that loads the generated resource and asserts there are 1,343 unique Persian fronts, all 24 unit labels are represented in meaning, every card has a Persian front and French meaning, repeated source fronts preserve every equivalent/example/unit, sentence contains no unit label, and no `meaning` contains an English gloss or slash-delimited English translation.
- [x] **Step 2: Run the focused test** `./gradlew :composeApp:iosSimulatorArm64Test --tests 'com.ziaee.zabankhan.content.CommDiagDeckTest'`; confirm it fails before the pack exists.
- [x] **Step 3: Implement the generator** using the existing card order, `l` chapter IDs, title list from the supplied PDF's contents page, and the Persian side of existing bilingual meanings. Carry through reliable French alternatives and add synonyms only where the source gives a clear equivalent; do not guess. Merge cards with the same Persian front, preserving all equivalent phrases, examples, and unit labels. Supply short original examples for the 23 entries whose source cards have no sentence. Put unit labels into meaning (leave `l` out of generated rows to avoid app-appended duplicate labels); keep sentence as French example text only. Fail with asserts if any front/meaning/example/label is missing, if any duplicate Persian fronts remain, or if not all 24 units are present. Produce the CSV with the app's current column order and a single deck name.
- [x] **Step 4: Generate the resource and CSV** and register the resource pack in the bundled manifest with its exact generated card count and size.
- [x] **Step 5: Run the focused data test** and inspect representative cards from units 01, 12, and 24, plus the merged three-unit card, in both outputs.

### Task 3: Publish the downloadable dataset pack

**Files:**
- Create: `/Users/ziaee/git/zabankhan-dict/packs/fr/dataset/decks/*.json` (every bundled deck, the new one included)
- Create: `/Users/ziaee/git/zabankhan-dict/packs/fr/dataset/manifest.json`

**Interfaces:**
- Pack contents exactly match the generated JSON in Task 2.
- The remote manifest uses the same ID, display name, level `A2-B1`, kind `phrases`, relative file path, count, and byte size as the bundled manifest.

- [x] **Step 1: Create the remote pack and a minimal remote manifest** under `packs/fr/dataset/` with the new deck entry. `mergeDatasetManifests` combines remote and bundled records, so existing bundled decks and stories remain visible without copying them.
- [x] **Step 2: Confirm the remote pack matches the bundled one** and leave existing working-tree edits in `zabankhan-dict` untouched.
- [x] **Step 3: Validate the remote manifest shape and compare pack hashes** against the bundled resource; verify the declared count and byte size against the generated file.

### Task 4: Verify the complete change

**Files:** All files from Tasks 1–3.

- [x] **Step 1: Run the required project verification gate** with JDK 17: `./gradlew :composeApp:assemblePlayDebug :composeApp:assembleBazaarDebug :composeApp:testPlayDebugUnitTest :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosArm64`.
- [x] **Step 2: Review diffs and repository status** in both repositories; confirm no unrelated edits were modified, no commits were created, and record the CSV path and test/build results.
