Read docs/superpowers/plans/2026-11-15-dictionary-quality-plan.md (sections "تصمیم", "ارزیابی خودکار", phase 2, phase 3) and content-tools/pilot_meanings.py, content-tools/gen_persian_meanings.py, content-tools/build_dictionary.py.

Task: implement phase 2 (sense-level Persian generation) plus the Wiktionary Persian-translation extraction it needs. Python stdlib only. Do not touch the app (composeApp) or the pilot outputs.

1. New `content-tools/extract_fa_translations.py KAIKKI_EN_JSONL_GZ OUT_JSON`:
   stream the English Wiktionary kaikki dump; for every entry, collect `translations` items with `lang_code == "fa"`; write {"<english word lowercased>": [{"fa": "...", "sense": "<translation sense gloss or ''>"}]} . Dedupe.
2. Extend `content-tools/gen_persian_meanings.py` (no parallel script):
   - `--backend codex|local` (default local keeps current behaviour). Reuse the codex call from pilot_meanings.py (`ask_codex`: read-only, ephemeral, empty temp cwd, --output-schema, usage logged).
   - Work per sense: one item per element of the entry's `en` list, id `entryId:senseIndex`. Store results in a new table `sense_fa(entry_id, sense, fa, en_short, confidence, model, prompt_version, created, check, status)`; keep `entry.fa` filled with the joined high-confidence senses for the current app.
   - `--fa-translations OUT_JSON`: add to each item `wiktionary_fa`: Persian translations of the English words in that sense's gloss (match on the gloss's main word(s); include the translation sense text). Prompt: prefer these when they fit the sense, never copy one that does not.
   - Batch size flag, default 60 for codex (fixed ~16k-token overhead per call).
   - Resumable; never overwrite rows with status `passed` unless `--prompt-version` differs. Low confidence / null / failed validation -> status `uncertain`, not written to entry.fa.
   - Stop cleanly on a quota/rate-limit error (non-zero codex exit with "limit" in stderr) and print how to resume.
3. New `content-tools/check_meanings.py DICT.sqlite --backend local|codex`: judge every `sense_fa` row with status `generated` (input: word, pos, english gloss, fa) as ok/acceptable/wrong; ok/acceptable -> `passed`, wrong -> `retry` once via gen_persian_meanings, second wrong -> `uncertain`. Also `--sample 100 --export FILE.tsv` to write a blind sample for an external judge.
4. Small self-check: `content-tools/test_meanings.py` with asserts for id mapping, resumability (passed rows untouched), validation, and wiktionary_fa matching, using a tiny in-memory sqlite and a fake backend. Run it.

Do not download data, do not run codex or the local model. Report changed files and test output only.
