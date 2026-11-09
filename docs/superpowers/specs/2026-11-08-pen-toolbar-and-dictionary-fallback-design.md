# Floating Pen Toolbar and Dictionary Fallback Design

## Intent

Improve PDF reading and vocabulary lookup by making the floating pen toolbar easier to control and by avoiding an empty dictionary result when the local dictionary has no meaning for a query.

## Goals

- Tapping outside the expanded pen tools collapses the expanded tools while keeping the floating toolbar available.
- Let the reader set pen thickness continuously with a slider.
- Let the reader switch the floating toolbar between horizontal and vertical layouts, while keeping it movable near screen edges.
- When the selected dictionary has no usable meaning, show a Google translation for the selected word and language pair.
- Clearly label the fallback as machine translation and preserve dictionary meanings as the primary result when present.
- For French entries, offer French-language definitions so learners can look up words in a French-to-French dictionary.
- Prefer concise standard French definitions and preserve their lexicographic sense structure.

## Non-goals

- Changing PDF ink storage or stroke rendering.
- Replacing the existing dictionary datasets or translation engines.
- Showing machine translation alongside every successful dictionary result.
- Presenting a French-to-French definition as a Persian or English equivalent.
- Rewriting sourced definitions as “simplified” text without distinguishing that rewrite from the source definition.

## Constraints

- Implement in the existing Kotlin Multiplatform Compose UI and use the current translation repository.
- Preserve the floating toolbar's existing drag behavior and PDF interaction.
- Dictionary lookup remains language-aware and uses the currently selected meaning language.
- French definitions are a distinct target mode, independent of the app's Persian/English meaning-language preference.
- Reused French Wiktionary content requires attribution and compliance with its share-alike license; dictionary provenance must be available to users.
- Keep controls wrapped or in dropdowns; do not add horizontally scrolling tool rows.

## Approaches

### Pen toolbar orientation

1. Automatically rotate the toolbar when dragged near a screen edge. This reduces overlap at edges but can change layout unexpectedly while moving.
2. Add an explicit horizontal/vertical orientation control. This adds one control and gives the reader predictable placement.

Use the explicit orientation control. The toolbar remains floating and draggable, with its selected orientation retained for the open PDF.

### Pen thickness

1. Keep the three existing preset widths. This is simple but does not provide the requested fine adjustment.
2. Replace the presets with a slider over a bounded continuous range. This provides direct control and continues to store the width in the existing normalized units.

Use a slider in the expanded toolbar. Preserve the current default width and apply the selected width to pen strokes; highlighter rendering continues to use the existing width multiplier.

### Tap outside expanded tools

1. Consume outside taps to collapse the expanded tools. This is straightforward but can interfere with PDF taps, selection, and drawing.
2. Observe outside taps without consuming them, collapse the expanded tools, and allow the PDF to receive the same gesture.

Use non-consuming observation so existing PDF interactions continue.

### Dictionary fallback

1. Automatically show Google translation only when the local dictionary has no usable meaning. This prevents an empty result while keeping dictionary data primary.
2. Always show Google translation as an additional option, even when dictionary meanings exist. This adds potentially redundant results and more visual noise.

Use automatic fallback only when no usable dictionary meaning is found. Reuse the selected source and target languages and the existing translation repository. Mark the result as machine-translated using the existing source attribution UI. If translation fails or is blank, retain the existing empty/error state.

### French-to-French definitions

1. Translate an English or Persian gloss back into French. This can produce a French phrase but remains a translation and does not provide a standard monolingual dictionary definition.
2. Add French definitions from a French-language lexicographic dataset. Wiktionnaire provides French headwords and French definitions and publishes reuse terms requiring attribution and share-alike. This better matches the requested French-to-French experience, with licensing and data preparation work.
3. Link to a commercial French dictionary. This can provide editorial definitions but requires an appropriate data or service license and may not work offline.

Use French Wiktionary definitions as a separate French definition mode, rather than translating the existing Persian/English gloss. Select concise standard senses where possible and keep the source wording intact; source data does not guarantee learner-level simplification. Show the definition source and required attribution. Do not silently substitute machine-generated paraphrases for dictionary text. If no French definition is available, keep the current Persian/English meaning and the Google fallback behavior.

## Interaction details

- The toolbar's expand arrow opens or closes the expanded tools.
- A tap outside the expanded portion closes that portion but does not minimize or dismiss the floating toolbar.
- The orientation control switches the whole toolbar between horizontal and vertical arrangements. Dragging remains available from the existing move handle.
- The thickness slider appears in the expanded tools and reflects the current pen width.
- Dictionary lookup still tries local exact/form/lemma resolution first. Only a result with no usable local meaning triggers Google translation.
- A fallback result can be saved with the vocabulary item as its meaning, with its machine-translation attribution visible before saving.
- French-learning entries can switch to a French-to-French view that displays French definitions and their part-of-speech/sense grouping when supplied by the source.
- The UI distinguishes source definitions from machine translation and any Persian/English meanings.
- French definitions include attribution to Wiktionary contributors and a visible source link; the dataset and distribution comply with the source license.

## Acceptance criteria

- The expanded pen tools close after tapping outside, and that tap still reaches the PDF content.
- Pen thickness can be set between the existing thin and thick presets, and a new stroke uses the chosen width.
- The toolbar can be switched to vertical, moved, and used in both orientations without clipping at screen bounds.
- A missing local dictionary meaning triggers a Google translation for the selected language pair and shows machine-translation attribution.
- Existing dictionary meanings remain primary and do not trigger the fallback.
- A failed fallback does not display stale results from a previous query.

## Open implementation choices

- Set practical minimum and maximum slider values from the current `.0025f` and `.007f` presets; the implementer may choose intermediate bounds that preserve current stroke appearance.
- Persist toolbar orientation for the lifetime of the PDF screen using the existing per-path saveable state pattern.
- Prepare and validate the French-definition dataset from a reproducible source snapshot; preserve entry identifiers, source links, and license metadata.
- The French-definition dataset is larger than the current language-specific shards may be; choose the index/shard packaging after measuring a representative data extract.

## Source notes

- The current app dictionary downloads its own versioned language shards from `ziaeemehr.github.io/zabankhan-dict`; the French schema currently exposes English and Persian senses, but no French definition field.
- The [French Wiktionnaire reuse guidance](https://fr.wiktionary.org/wiki/Wiktionnaire:R%C3%A9utilisation_du_contenu_du_Wiktionnaire) states that its content is reusable under CC BY-SA, with attribution and share-alike requirements. The [Wiktionnaire offline help](https://fr.wiktionary.org/wiki/Aide:Consulter_le_Wiktionnaire_hors-connexion) points to Wikimedia database dumps as an upstream source for a reproducible French-definition extraction.
- Google translation remains a separate fallback and is not treated as a monolingual French definition.
