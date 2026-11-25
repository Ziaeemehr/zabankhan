# Resource discovery MVP

The initial implementation is an offline maintainer tool, not a runtime Telegram client.

Run `python3 tools/learning/scan_resources.py /path/to/export --output /tmp/resources-private.json`.
Keep raw exports and private discovery output outside Git. Message IDs deduplicate overlapping exports, not different posts containing the same issue. Issue normalization is a later step.

Public catalog entries require stable IDs, target language, type, series title, estimated CEFR levels, locale-keyed descriptions, provenance, rights status and review status. Only verified authorized URLs may enable download. Unknown rights disable downloads. No commercial PDF or cover is bundled.

Initial candidates are draft metadata only; do not ship until source, level, localization and applicable QA reviews are complete. Runtime browser, import integration and Android/iOS verification remain pending.
