# Grammar UI faithful port

Goal: implement the user's file-by-file French Reader grammar port specification, without redesign or commits.

Architecture: retain reference Compose layout/rendering, with LanguageDataPacks, LanguageSpec and container-owned KeyValueCache preferences. Keep direction local to text and wrap filters.

- [ ] Port model/search/history/posters and tests, parameterizing learning language.
- [ ] Copy GrammarScreens, GrammarText, GrammarMarkdown and poster UI; adapt resources and platform APIs only.
- [ ] Copy grammar palette into theme, English/Persian strings verbatim (language names parameterized), add French strings, wire existing navigation and related links.
- [ ] Verify requested Android/iOS build gate, review diff, smoke-test emulator-5554 debug package including English empty state, capture /tmp screenshots.

Review focus: inline highlight offsets under directional isolates, Persian aliases and accents, history language isolation, malformed poster entries, empty English pack and back navigation. Export PDF depends on reference Android PDF infrastructure; record any unavoidable omission explicitly.
