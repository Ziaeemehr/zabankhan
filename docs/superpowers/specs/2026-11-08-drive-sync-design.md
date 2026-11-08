# Google Drive device sync design

## Intent

Let the Release app on Android and iPad share learning progress without requiring the user to export and restore a full database after every change. Google Drive remains the transport and storage provider; the app does not add a hosted backend.

## Current behavior

The app creates ZIP snapshots of its local Room database and uploads them to Drive's hidden `appDataFolder`. Restore replaces the destination database. Android and iOS can upload automatic snapshots, but neither device automatically applies a remote snapshot. Most rows use local auto-incrementing numeric IDs, and several rows refer to other rows, so merging two database files directly can collide IDs or break references.

## Goals

- Automatically check for and exchange changes when the app starts or returns to the foreground, and shortly after local edits while online.
- Work offline; queue local changes until Drive is available.
- Preserve changes made independently on both devices, including additions and deletions.
- Make applying remote changes idempotent so retries cannot duplicate records.
- Show sync state, the last successful sync, and actionable errors. Keep manual “Sync now” available.
- Keep the existing full ZIP backup and restore as a separate safety and migration path.

## Initial scope

Sync flashcards and review progress:

- Vocabulary cards and lists, including the word, saved sentence, meaning, dictionary URL, and review scheduling state.
- Review history, including undo/deletion markers, merged by stable operation ID.
- A card added while reading a text, PDF, or video transcript appears on the other device with its saved sentence and meaning. The source document/video itself and the local `textId` link do not sync, so opening the original source is only available on the device that has it.

Keep PDFs on the iPad. Do not sync library documents, video files, transcripts, reading positions, study logs, measured activity, bundled dictionaries, downloaded datasets, translation/media caches, API credentials, platform-specific preferences, highlights, or PDF ink in this first version. Existing full backup/restore continues to transfer files and preferences; PDF inclusion follows the current backup setting. Revisit broader sync after cards and reviews are stable.

## Approaches considered

1. **Keep whole-database snapshots and auto-restore the newest one.** Small implementation, but concurrent edits and restores can silently discard a device's newer data. Rejected.
2. **Merge SQLite snapshots by their current numeric row IDs.** Preserves some additions but IDs are local and relations can point at the wrong records. Rejected.
3. **Sync logical record changes with stable IDs, a per-device change journal, and tombstones.** Requires schema and write-path work, but supports retries, offline changes, and explicit conflict handling without a server. Recommended and selected as the basis for implementation.

## Proposed design

- Give each installation a persistent random device ID. Give each syncable record a stable sync ID independent of its local SQLite integer ID. Translate references such as review-to-card and session-to-source through those sync IDs when applying changes locally.
- Record logical upserts and deletes in an idempotent change journal. Journal entries have a unique operation ID, entity sync ID, device sequence, and base revision. Deletes create tombstones so an older snapshot cannot resurrect a removed item.
- Store each device's journal/state in a separate file in the shared Drive `appDataFolder`. One device never overwrites the other device's change file. Upload only after applying available remote operations; keep unacknowledged local operations until Drive confirms them.
- Check for changes on app launch/resume and after local writes with a short debounce. Do not depend on Drive push notifications or a custom server. A “Sync now” action uses the same engine.
- Merge review events by stable operation ID and replay them deterministically to rebuild card scheduling state. Apply a single-sided card/list edit directly. If both devices changed the same card content or list from the same base revision, preserve both versions and show a conflict choice; do not silently choose a winner. Apply deletes only after comparing their base revision with edits.
- On first sync, combine the existing local progress from both devices. Preserve distinct records even if their display text matches; do not deduplicate cards solely by word.
- Keep syncing blocked if the Google account differs or the cloud data schema is newer than this app. Report the reason and leave local data intact.

## Conflict and recovery behavior

No merge or retry may replace the whole local database. The sync engine applies a batch inside a Room transaction and advances its remote cursor only after the transaction succeeds. Before the first sync and before any conflict resolution, retain a local safety snapshot. Network/auth failures leave pending operations queued for the next attempt.

## Platform and UI

Use the existing `drive.appdata` authorization and Drive client on Play Android and iOS Release. Trigger checks from Android foreground/background lifecycle and iOS scene-phase lifecycle. Add a Sync section to Backup settings with status, last sync, Sync now, and conflict actions. The Bazaar flavor remains without Drive support.

## Constraints and open implementation choices

- Preserve backward compatibility with existing ZIP backups and database migrations.
- Never upload secrets or platform credentials.
- The exact set of write paths to instrument must be audited before implementation; writes made through import/migration paths need explicit classification.
- PDFs remain on the iPad by decision. Syncing a PDF to Android later would need file upload/download, stable file identity, and on-demand download behavior; it is outside this implementation.
