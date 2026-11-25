# Resource discovery MVP

The initial implementation is an offline maintainer tool, not a runtime Telegram client.

Run `python3 tools/learning/scan_resources.py /path/to/export --output /tmp/resources-private.json`.
Keep raw exports and private discovery output outside Git. Message IDs deduplicate overlapping exports, not different posts containing the same issue. Issue normalization is a later step.

Public catalog entries require stable IDs, target language, type, series title, estimated CEFR levels, locale-keyed descriptions, provenance, rights status and review status. Only verified authorized URLs may enable download. Unknown rights disable downloads. No commercial PDF or cover is bundled.

Initial candidates are draft metadata only; do not ship until source, level, localization and applicable QA reviews are complete. Runtime browser, import integration and Android/iOS verification remain pending.

## Version 1 draft catalog

The offline catalog is a JSON object with `schemaVersion: 1` and an `entries` array. Each entry carries a stable `id`, target `language`, `type`, `audience`, `illustrated` flag, `series`, estimated CEFR `levels`, locale-keyed `descriptions`, `provenance`, `rights`, `generation` and `status`. Persian descriptions are required. Optional instructional locales use the same entry ID. The runtime reads this metadata from local JSON.

Validate against the draft fixtures with:

```sh
python3 -m unittest discover -s tools/learning -v
python3 tools/learning/validate_catalog.py tools/learning/fixtures/resource-catalog.draft.json
```

Use `--release` on a proposed public catalog. Every entry must be approved and carry an official HTTPS URL, dated rights evidence and owner approval for its current content hash. LLM-assisted descriptions also need passing blind, adversarial and pedagogical review records from three distinct reviewers. Compute the digest with `content_hash(entry)` from the validator. It hashes sorted, compact UTF-8 JSON of all entry fields except the status and review records.

The fixtures contain draft candidates for illustrated children's and teen magazines. Rights, Persian descriptions and CEFR estimates still need review. The fixtures live under `tools/learning` and stay outside application resources.
