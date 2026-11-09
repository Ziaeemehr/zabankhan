# Study Experience Improvements

## Goal

Reduce unnecessary cloud translation, fix reading playback/progress, make Leitner card text selectable, and keep PDFs out of backups by default while making the choice clear.

## Decisions

- Use one persisted AI-model permission shared by dictionary and video. Its initial value preserves current behavior for existing users; provide a compact, accessible switch in both places. Disabling it blocks LLM requests while cached results and the existing free-provider fallback remain available.
- A video translation request is user initiated. Merely loading or changing the visible subtitle must never trigger translation. Translate only the current sentence after the user taps the action; re-check the cloud permission at request time.
- Dictionary explanation/grammar requests must be explicit and scoped to the selected word or phrase. Do not batch-translate dictionary explanations automatically.
- Reader pause/resume must retain the current UTF-16 position in the current paragraph/sentence and continue from there.
- Reading progress must report completion after the final readable chunk/page, including empty trailing chunks/pages where applicable.
- PDF backup inclusion defaults to off for new installs and remains configurable. Explain that PDFs are omitted and show their approximate size beside the switch without repeatedly interrupting users. Honor the setting for both manual and automatic backup/restore manifests.
- The revealed side of a Leitner card supports selecting a word/phrase and opening the existing dictionary UI, reusing reader selection behavior and language direction.
- Keep the UI restrained: compact controls, existing material styling, no horizontal chip/menu rows.

## Acceptance

- AI-model-disabled requests do not call an LLM; cached results and existing free-provider fallback remain available.
- Subtitle visibility/loading alone causes zero translation calls; tapping translate requests the current sentence once, retry requests it again.
- Paused speech resumes from the captured spoken offset, and final reader progress reaches 100%.
- Selecting card-back text opens the dictionary for the selected text and can be dismissed back to the card.
- New installs exclude PDFs by default; toggling inclusion changes the generated backup contents and the user can see the choice and size.
- Run focused tests after each of three implementation rounds and the repository verification gate after the final review.
