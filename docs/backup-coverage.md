# Backup coverage audit

Audited against `AppDatabase` version 5, `SettingsRepository`, `ProgressPreferences`, `BackupPrefs`, app file stores, `LocalBackup`, `AutoBackup`, and `DriveBackupClient` (2026-11-08).

## Room tables

The local ZIP and Google Drive ZIP contain the SQLite database file, so all listed tables and their rows are carried together. Restore opens the staged database with Room before swapping it into place; older supported database versions are handled through Room's migrations. `texts_fts` is a derived FTS index and its open callback rebuilds it when needed.

| Table | Contents | Included? | Restored? | Notes |
|---|---|---:|---:|---|
| `texts` | Library documents, imported text body/path, source metadata, folder, reading offset/chunk/time, voice/rate, pinned/read state, PDF/image paths | Yes | Yes | File paths rooted at the previous app files directory are rebased on restore. |
| `library_folders` | User folders and hierarchy | Yes | Yes | |
| `library_tags` | User tags | Yes | Yes | |
| `text_tags` | Text/tag associations | Yes | Yes | |
| `vocab` | Words, meanings, sentences, deck, learned state, Leitner box and next/last review times | Yes | Yes | Includes all SRS state. |
| `vocab_lists` | User vocab decks/lists | Yes | Yes | |
| `review_log` | Individual review answers and before/after box state | Yes | Yes | |
| `activity_log` | Daily reading/listening/watching totals | Yes | Yes | Statistics and points are derived from preserved activity and study data. |
| `study_source` | Study log sources | Yes | Yes | |
| `study_session` | Study sessions, dates, duration, skill, notes | Yes | Yes | |
| `highlights` | Text highlights | Yes | Yes | |
| `pdf_highlights` | PDF word-range highlights | Yes | Yes | |
| `pdf_ink` | PDF page strokes | Yes | Yes | |
| `headlines` | Cached news headlines | Yes | Yes | Present in the database archive. |
| `resources` | User-created resource bookmarks | Yes | Yes | |
| `exam_attempts` | User exam answers, scores and notes | Yes | Yes | |
| `shadow_attempts` | Shadowing/pronunciation attempts | Yes | Yes | |
| `kv_cache` | App key/value state including grammar settings, watch history, translation overrides, captions, achievement celebration state and app caches | Yes | Yes | Values and user-visible state in this table are preserved; some transient cached values may be rebuilt. |
| `texts_fts` | Search index for text documents | Yes | Rebuilt as needed | Derived index; not independent user data. |

Room schema version: **5**. The archive manifest records `schemaVersion`; restore rejects future/invalid versions, validates the SQLite header, then opens the staged DB through Room so available migrations run before replacement. Current migration chain is 1→2→3→4→5.

## DataStore and settings keys

The entire `zabankhan.preferences_pb` DataStore file is included in local and Drive archives, preserving unknown/future keys too. On restore, the staged Preferences file is decoded before swap. The API key is removed from legacy Preferences during backup and restore; current API secrets live in platform secure storage and are never archived. Drive authentication is platform-managed and excluded.

| Keys | Included/restored? | Notes |
|---|---|---|
| `learning_lang`, `learning_langs`, `meaning_lang`, `dictionary_meaning_lang`, `ui_lang`, `theme_mode` | Yes | Language and appearance settings. |
| `daily_new_cards`, `review_intervals`, `daily_goal_minutes`, `weekly_points_goal` | Yes | Learning goals and SRS defaults. |
| `reader_font_size`, `reader_font_scale`, `reading_background`, `page_navigation`, `reading_highlight_color`, `reader_auto_follow`, `last_highlight_color` | Yes | Reader preferences. |
| `watch_translation_enabled`, `watch_subtitle_size_percent`, `watch_compact_player` | Yes | Video preferences. |
| `speech_rate`, `speech_repeat`, `auto_pronounce_cards`, `review_audio_autoplay_sides`, `speech_recognition_engine`, `card_practice`, `voice_engine`, `tts_voices` | Yes | Speech preferences, not generated audio. |
| `tts_cache_max_size_mb`, `tts_cache_max_age_days` | Yes | Cache policy settings. |
| `last_dictionary_providers`, `news_categories`, `news_keywords`, `news_matching_first`, `news_only_matching` | Yes | User selection/filter preferences. |
| `review_reminder_enabled`, `review_reminder_hour`, `review_reminder_minute` | Yes | Reminder preferences. |
| `llm_provider`, `llm_model`, `cloud_provider`, `cloud_url`, `cloud_model` | Yes | Provider configuration; no credential. |
| `llm_api_key` | **No** | Legacy value migrated to secure storage and scrubbed before archive; removed from staged restores. |
| `backup:backup_pdfs`, `backup:auto_backup_enabled`, `backup:last_backup_at_ms`, `backup:last_drive_upload_at_ms`, `backup:last_auto_backup_at_ms`, `backup:pdf_backup_changed_at` | Yes | Backup controls/status. |
| `backup:auto_counts`, `backup:database_stamp` | No on restore | Local change-detection metadata is intentionally reset so a new device can establish its own baseline. |
| `progress_seen` | Yes | Achievement/celebration history. |

## User files and caches

| File store | Included? | Restored? | Notes |
|---|---:|---:|---|
| `text_bodies/` | Yes | Yes | Imported/extracted text files; paths are rebased. |
| `text_images/` | Yes | Yes | User images and extracted EPUB assets; paths are rebased. |
| `videos/` | Yes | Yes | Imported/local video and subtitle-related files stored here. |
| `pdfs/` | Opt-in (`backup:backup_pdfs`, enabled by default) | Yes when manifest says included | PDFs are copied with the archive and database paths rebased. If omitted, existing PDF files on the destination are retained because a PDF-excluding archive does not own that directory. |
| `cache/tts/`, `cache/pdf-ocr/` | No | No | Synthesized speech and OCR output are reproducible caches. |
| `own-dictionary/`, `dataset_packs/`, `vosk/` | No | No | Dictionary shards, downloadable dataset packages and speech-recognition models are re-downloadable caches/resources. |
| Platform secure storage (LLM API key, Drive auth) | No | No | Secrets and account credentials are deliberately excluded. |
| Local backup/export ZIPs | No nested copy | N/A | Backups are stored separately from app files; Drive archives contain the same payload as local archives. |

## Backup and restore behavior

- Local and Drive backups use the same ZIP payload and manifest. Drive upload prunes only matching `zabankhan-backup-*.zip` files and keeps the newest five by `createdTime`; it paginates the full listing and does not prune after a failed upload.
- Automatic backup checks database modification time and configured PDF changes, records a baseline/count snapshot, applies date and size rotation, and schedules Drive upload after creating the local archive. The automatic archive keeps at least three ordinary snapshots under its size budget; protected snapshots are retained separately (up to five).
- Restore validates the ZIP structure and bounds, required manifest/database/preferences entries, entry names, database header/integrity, Room schema/migrations, file paths, and Preferences serialization in staging before closing the current app database. Replacement uses staged files and rollback copies; a replacement failure restores prior files.
- No Gradle tasks were run for this audit, as explicitly requested.

## Verification limits / follow-up

Common tests cover manifest/schema validation, Drive pagination and newest-five retention, legacy migration, and a ZIP database round trip that compares every non-derived user-data table row by row. The round-trip fixture explicitly asserts the Leitner box and review timestamps, review-log box transitions, and study-source/session contents. `texts_fts` is excluded from row equality because Room rebuilds this derived index from `texts`. The test exercises the serialized SQLite payload but not the platform-backed live-container file replacement and rollback path; that full integration behavior remains unverified.
