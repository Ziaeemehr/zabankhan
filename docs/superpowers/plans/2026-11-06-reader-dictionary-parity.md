# Reader and dictionary reference port

Goal: copy FrenchReaderSpike reader and dictionary presentation and interactions into Compose Multiplatform, retaining language-aware content and platform services.
Spec: user's second-pass checklist (2026-11-06). FrenchReaderSpike remains read-only. No commits.

- [x] Copy reference reading palette, font scale and configurable background/highlight preferences; preserve through DataStore.
- [ ] Copy ReadingScreen, structured Markdown rendering, selection toolbar, highlight helpers; wire language-aware document offsets and dictionaries.
- [ ] Copy DictionarySheet and MeaningOptionPills, preserve DictionaryPane watch API, exact-context saving/deletion.
- [ ] Adapt timed playback seek, audio loading states and pinned synthesis cache to existing KMP speech services.
- [ ] Verify new pure logic with common tests, run JDK17 Android and iOS gates, smoke-test emulator-5554 debug package including English learning content.
- [ ] Review copied source against checklist; record every platform/service exception in final report.

Review focus: Markdown display-to-source offsets, adjustable selection and word taps, identical words in different saved contexts, English learning-language behavior, playback/find/follow ownership.

Implementation split: reader rendering/selection; dictionary controls; playback services; parent owns theme/settings/resources, integration and independent verification. Graph tools unavailable: targeted source reads used. `codex exec` initialization denied by environment; direct file edits used. Gradle home cloned to writable /tmp to avoid changing global cache.
