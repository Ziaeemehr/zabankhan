# Comm_diag_a2b1 · Farsi to French deck

## Intent

Create one downloadable, importable deck from *Communication en dialogues A2-B1*, geared to Persian-speaking learners recalling French. Name it `Comm_diag_a2b1` (or the app's equivalent display name). Reuse the existing book card set where its content fits, with Persian on the front and French on the back, French synonyms for useful vocabulary and expressions, and a French example sentence. Include each chapter's `Unité NN — <chapter title>` in searchable card text so Words search finds a chapter. Keep this as one deck. When the user starts review from Words, review only cards matching the current search and status filters, so searching a unit limits review to that unit.

## Approaches

- One deck with searchable unit labels and review of current Words results: matches the requested book deck, lets Words search find units, and fixes the review button to honor the current filters. Chosen; the user clarified this preference.
- Twenty-four chapter decks: enables chapter-specific review through the existing deck selector, but conflicts with the user's preference for one book deck.
- Add a separate chapter picker to review: supports one deck and direct chapter review but adds UI beyond the existing search filter; use search and carry the filtered results into review instead.

## Constraints and implementation

- The app's CSV importer supports `word,meaning,sentence,deck`; current Words search matches all three card text fields. Put Persian in `word`, French translation and up to two French synonyms in `meaning`, and a French example followed by the unit label on its own line in `sentence`. The label stays out of `meaning` so the card back stays clean; search still finds it.
- The CSV importer and dataset installer deduplicate cards by their front text within a deck. The reverse-direction source has 11 repeated Persian fronts, so merge those into one card while preserving every French equivalent, example, and unit label; this yields 1,343 unique importable cards from 1,354 source rows.
- Twenty-three source rows have no example sentence. Supply one short original French example for each; keep the source's French expression in meaning and append the unit label in sentence.
- The Words review button currently passes only the selected deck scope; ReviewViewModel loads the whole deck. Pass the currently filtered card IDs into Review through shared app state (not a navigation route argument, since the set can exceed 1,000 IDs) and use that subset for its due queue and overview. When the filtered result is empty, disable the review button instead of falling back to the whole deck. The due count shown beside the button should reflect those same filtered cards. Existing review entry points without search results retain their current whole-deck behavior.
- The app repository already contains the source deck (1,354 cards, units 01–24) as `comm-dial-a2b1-phrases`. Add this reverse-direction deck to `zabankhan-dict`'s downloadable dataset and mirror the pack/manifest entry in this app's bundled dataset. The app merges remote and bundled manifests, so the remote manifest can list just this new pack.
- Provide a CSV compatible with the current release so the user can import without rebuilding. Dataset availability in the published app depends on publishing the updated `zabankhan-dict` dataset; importing the CSV does not.
- Use concise, original Persian equivalents and French synonyms/examples; do not reproduce dialogues or long passages from the PDF.

## Scope

One new book deck and one CSV import file, plus the dataset manifest/resource entries. Update the Words-to-Review handoff and review scoping so review honors the active Words filters. No separate chapter picker.

## Builder calls

The builder may normalize unit labels and choose concise card-level equivalents/synonyms from the existing source cards. Cards with no reliable Persian equivalent or sentence should be omitted rather than guessed.
