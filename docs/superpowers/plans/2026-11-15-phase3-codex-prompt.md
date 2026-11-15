Work in this repo (zabankhan-dict). Python stdlib only. Do not publish, push, commit or touch the app repo.

Context: Persian meanings were generated per English sense with gpt-6-luna and auto-checked. They live in
/Users/ziaee/git/zabankhan/content-tools/out/fr-dict.work.sqlite (read-only for you):
- table `entry(id, word, pos, en, ...)`: `en` is a JSON list of English glosses; sense index = position in that list.
- table `sense_fa(entry_id, sense, fa, en_short, confidence, model, prompt_version, created, "check", status)`.
  Only rows with status = 'passed' are usable.
The real pack is dist/fr-v2.sqlite: `entry(id, word, pos, ...)` and `sense(id, entry_id, position, en, fa, tags, synonyms)`; its ids differ from the work DB.

Task:
1. New `tools/import_sense_fa.py WORK.sqlite PACK.sqlite`:
   - For each passed row, find the pack sense by (word, pos, normalized English gloss) — normalize = lowercase, collapse spaces, strip trailing punctuation. Fall back to (word, pos, position) only when the gloss at that position matches after normalization; otherwise skip.
   - Fill `sense.fa` only when it is empty (never overwrite).
   - Record provenance in `meta`: key `fa_source` = "gpt-6-luna generated per sense, automatically checked (not human-reviewed); prompt sense-fa-v1".
   - Print: passed rows, matched, filled, skipped (with 10 skipped examples), and pack senses with fa before/after.
2. Check `tools/publish.py` and `tools/validate.py` still accept the pack (persian count in index.json must reflect filled senses). Fix only if something breaks.
3. Add a test in `tests/` for the matcher (exact gloss, normalized gloss, position fallback, no overwrite) with tiny in-memory DBs. Run `python3 -m unittest discover tests`.
4. Run the import on a COPY: `cp dist/fr-v2.sqlite /tmp/fr-v2.fa.sqlite`, then run the importer on the copy and `tools/validate.py` on it. Do not modify dist/fr-v2.sqlite.

Report: changed files, test output, and the importer's printed numbers.
