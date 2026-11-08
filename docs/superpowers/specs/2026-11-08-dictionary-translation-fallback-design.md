# Dictionary translation fallback

## Intent

When a selected word or phrase has no exact entry in the in-app dictionary, show a useful translation in the same dictionary pane instead of leaving only the no-entry message or hiding the result in the edit form.

## Goals

- Keep exact local dictionary entries as the first result.
- For a missing word or multiword phrase, show a translation inline in the selected dictionary pane.
- Use the configured cloud model first when available, then fall back to the existing Google translation path.
- Show which source produced the translation and a clear unavailable state if every source fails.
- Preserve the existing source selector and meaning-edit/save behavior.

## Non-goals

- Do not show duplicate machine translations when an exact local entry already has a meaning.
- Do not change dictionary data, providers, or saved vocabulary semantics.
- Do not add a new translation service or paid API.

## Approaches

1. **Inline automatic fallback (chosen):** reuse `TranslationRepository.lookupWord` for words and phrases and display its result where the local dictionary reports no exact entry. This matches the requested behavior and existing cloud-then-Google fallback chain.
2. **User-triggered translation:** add a separate translate button. This avoids automatic requests but requires another tap and leaves the missing-entry state unresolved until then.
3. **External dictionary only:** keep web dictionary providers as the fallback. This does not provide the requested Google/model translation inside the app.

## Design decisions

- Trigger the fallback only after the local dictionary has no exact entry with a meaning in the selected target language.
- Reuse the current selected text verbatim for both single words and phrases, with the containing sentence as context.
- The repository already tries the configured model first and continues through its existing free Google translation fallback if the model fails.
- Render the translated text and its source label in the no-entry area; keep the “not in dictionary” context visible.
- If all translation sources fail, show an explicit unavailable message and leave the normal edit/save controls available.
- Treat a missing answer about entries without a Persian gloss as the existing local-entry behavior: translate the entry’s English gloss as today, without adding a second phrase-level translation card.

## Constraints and verification

- Shared implementation in `commonMain`; no platform APIs.
- Preserve KMP and language-generic behavior.
- Verify Android and iOS builds and manually inspect word, phrase, model-enabled, and no-network states. Do not add or run tests unless requested.
