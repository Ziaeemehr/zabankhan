# Dictionary Definitions and Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep dictionary lookups useful when no local entry exists and add sourced French-to-French definitions for French entries.

**Architecture:** Reuse the existing translation repository for a labeled missing-entry fallback. Add a separate French definition field to the French dictionary shard format, populate it from French Wiktionary data in the content pipeline, and present it as a third dictionary view without treating it as a Persian/English translation.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Kotlin serialization, Python content tooling, existing Ktor translation repository.

**Spec:** `docs/superpowers/specs/2026-11-08-pen-toolbar-and-dictionary-fallback-design.md`

## Global Constraints

- Shared implementation in `commonMain`; no `java.*`, `android.*`, or `org.json`.
- Preserve language-aware lookup and current Persian/English preferences.
- French Wiktionary content must retain source attribution and comply with CC BY-SA share-alike requirements.
- Machine translations must be labeled and must not replace available dictionary definitions.
- Do not add or run tests unless the user asks to test or verify implementation.
- No commits.

## Review Focus

- Missing entries, blank translations, and failed/offline requests must not show stale results.
- Entries with English but no Persian gloss must not appear to have a French definition unless sourced French text exists.
- Multiple parts of speech for the same headword must keep their definitions grouped correctly.
- Old dictionary shards without the appended French field must continue decoding.
- Machine translation and sourced French definitions must have distinct attribution and save behavior.

---

### Task 1: Add a missing-entry translation fallback

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/dictionary/OwnDictionaryUi.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/ui/reader/DictionarySheet.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/own_dictionary.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-fa/own_dictionary.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-fr/own_dictionary.xml`

**Interfaces:**
- Consumes: `TranslationRepository.translateTextWithSource(text, source, target, sentenceContext)`, the current query and meaning target, and the existing `TranslationSourceIcon`.
- Produces: inline translation state in the no-local-entry branch, with loading/error states and source attribution.

- [ ] Trigger fallback only after dictionary lookup is complete and has no usable local definition.
- [ ] Reuse the selected query, learning language, meaning target, and containing sentence where available.
- [ ] Clear prior result when query or target changes; keep existing edit/save controls available.
- [ ] Label the result as machine translation and preserve the translation engine/source attribution.
- [ ] Check `git diff --check` and inspect localized strings in English, Persian, and French.

### Task 2: Extend the French dictionary data pipeline

**Files:**
- Modify: `content-tools/build_dictionary.py`
- Create: `content-tools/build_french_definitions.py`
- Modify: the French dictionary pack generator and shard publisher in the `zabankhan-dict` data repository, if available in the connected workspace.

**Interfaces:**
- Consumes: the current French word/POS key and a reproducible French Wiktionary extract.
- Produces: a French-definition list grouped by word and part of speech, merged into the French compact-entry payload at a new trailing field so old indexes and fields remain stable.

- [ ] Define a deterministic extraction that keeps French-language definitions, removes form-only/redirect senses, cleans markup, and preserves concise sense order and POS grouping.
- [ ] Merge definitions by normalized headword and POS without generating definitions by translating English or Persian glosses.
- [ ] Record source snapshot and CC BY-SA attribution metadata in the generated pack and make source links available to the UI.
- [ ] Regenerate representative shard output and measure its size before deciding whether format/version or shard boundaries must change.
- [ ] If the pack publisher is not available, leave generated assets unpublished and report the missing handoff explicitly.

### Task 3: Decode and display French definitions

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/dictionary/OwnDictionary.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/dictionary/OwnDictionaryUi.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/dictionary/DictionaryEntryCompact.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/own_dictionary.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-fa/own_dictionary.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-fr/own_dictionary.xml`

**Interfaces:**
- Consumes: optional trailing French definitions in dictionary entries and current `DictionaryLookup` results.
- Produces: a French mode alongside existing Persian/English meanings for French entries; choosing Persian or English retains existing translation behavior.

- [ ] Decode the optional new entry field, defaulting to an empty list for old shards.
- [ ] Add a French-only UI choice when looking up French entries; keep it independent of the app's Persian/English meaning-language setting.
- [ ] Render source definitions by part of speech with source attribution/link and existing selection/copy behavior.
- [ ] Keep the current Persian/English meanings, example translations, and saving semantics intact.
- [ ] Check `git diff --check` and trace both legacy shards and new-format entries through compact and full dictionary cards.

### Final verification

- [ ] Run `git diff --check`.
- [ ] Run `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :composeApp:assemblePlayDebug :composeApp:assembleBazaarDebug :composeApp:testPlayDebugUnitTest :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosArm64`.
- [ ] Manually inspect a known dictionary miss, a successful existing entry, French definitions with multiple POS, and old-shard compatibility when the Android/iOS app and refreshed dictionary pack are available.
