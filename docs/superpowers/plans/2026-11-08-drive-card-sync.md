# Google Drive Card Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically merge flashcards, lists, and review progress between the Release app on Android and iPad through Google Drive, while leaving PDFs and source files on their original device.

**Architecture:** Add stable sync identifiers and a durable operation journal alongside the existing Room database. Each device stores an independent journal in Drive `appDataFolder`; a common sync engine downloads, transactionally applies, then uploads operations, preserving retry safety and surfacing concurrent edits for user resolution. Existing ZIP backup and restore remain independent.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Room, kotlinx.serialization, Ktor, existing Google Drive `drive.appdata` auth, Android lifecycle and iOS SwiftUI scene phase.

**Spec:** `docs/superpowers/specs/2026-11-08-drive-sync-design.md`

## Global Constraints

- PDFs remain on the iPad; source texts/videos/PDFs, study logs, activity, dictionaries, caches, credentials, platform preferences, highlights, and PDF ink are excluded from sync.
- Preserve backward compatibility with existing ZIP backups and Room migrations.
- Never upload secrets or platform credentials.
- Use existing `drive.appdata` authorization; the Bazaar flavor remains without Drive support.
- Applying remote operations is transactional and idempotent; failures leave local data and remote cursor intact.
- A card originating from a text, PDF, or video syncs its word, saved sentence, and meaning; its source document and local `textId` link do not sync.
- Do not add or run tests or create commits; verify the Android Play/Bazaar and iOS framework builds.

## Review Focus

- An offline device returning after the other device edited the same card: retain both edits and expose a resolvable conflict.
- A retry after an interrupted apply/upload: no duplicated cards, reviews, or deletions.
- A review event received before its card operation: defer/apply safely without losing review history.
- Different Google accounts or a newer remote schema: stop sync and preserve local data.
- Legacy local cards and lists without stable IDs: include them in initial sync without merging distinct cards merely because their words match.

---

## File Map

- `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/data/Entities.kt`: stable sync IDs and sync journal/conflict entities.
- `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/data/AppDatabase.kt`: schema version, migrations, DAO registration, transaction access.
- `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/data/Daos.kt`: DAO operations to persist and apply local/remote card, list, review, and journal changes.
- `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/backup/DriveBackup.kt`: Drive appData file CRUD for per-device sync journals, kept separate from ZIP snapshots.
- New `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/sync/` files: wire format, journal repository, merge engine, and sync coordinator.
- Existing card/list/review write screens and repositories: route user mutations through journal-aware DAO methods; import/bundled-dataset paths are classified explicitly.
- `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/backup/BackupSettings.kt` and resource XML files: sync status, last-success time, Sync now, errors, and conflict resolution controls.
- `composeApp/src/androidMain/kotlin/com/ziaee/zabankhan/backup/Backup.android.kt`: trigger sync when the app returns to foreground and debounce online pending work.
- `composeApp/src/iosMain/kotlin/com/ziaee/zabankhan/backup/Backup.ios.kt` and `iosApp/iosApp/iOSApp.swift`: trigger sync on active scene phase.

## Implementation Tasks

### Task 1: Add stable identities and journal schema

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/data/Entities.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/data/AppDatabase.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/data/Daos.kt`

**Interfaces:**
- Produce `SyncOperation` with `operationId: String`, `deviceId: String`, `sequence: Long`, `entityType: String`, `entitySyncId: String`, `baseRevision: Long?`, `operationType: String`, and serialized payload/tombstone data.
- Produce persistent sync metadata for device identity, cursors, remote schema/account identity, last-success time, and conflicts.
- Add stable `syncId` fields to vocabulary cards and lists; keep numeric Room IDs for local foreign keys.
- Migrate all existing rows with unique sync IDs without changing their local IDs or relations.

- [x] Add stable IDs and journal metadata entities, plus required DAO queries.
- [x] Increment Room schema version and add a migration that backfills IDs and initializes metadata; retain all prior migrations and schema export behavior.
- [x] Confirm legacy full-backup database restore remains compatible through the migration chain; verify by inspecting the Room migration path and preserving its schema history.

### Task 2: Journal local card, list, and review mutations

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/data/Daos.kt`
- Modify all actual card/list/review mutation call sites in `ui/`, `content/`, `dictionary/`, `watch/`, and `ui/reader/` found by auditing Room writes.

**Interfaces:**
- Produce transactional DAO methods for card/list create, content update, move, delete, review, and review undo that mutate the row and append the corresponding journal operation atomically.
- Use operation UUIDs for idempotence; use tombstones for deletes and review undo markers rather than deleting shared history.
- Classify bundled dataset refresh/import paths so they do not accidentally journal destructive replacement of user data; user-initiated imports remain syncable.

- [x] Audit every write to `VocabEntry`, `VocabList`, and `ReviewLogEntry`, recording whether it represents a user mutation, migration, bundled data, or full restore.
- [x] Route user mutations through transactional journal-aware methods; include saved sentence and meaning while excluding local source links such as `textId`.
- [x] Ensure card/list delete and review undo append tombstones/markers and preserve relationship identity through sync IDs.

### Task 3: Define per-device Drive journal transport

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/backup/DriveBackup.kt`
- Create: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/sync/SyncWireFormat.kt`
- Create or modify platform-specific persistent device ID storage only if existing storage cannot provide a stable installation identifier.

**Interfaces:**
- Produce serializable `DeviceChangeFile` with `schemaVersion`, `accountId`, `deviceId`, monotonically increasing sequence, and journal operations.
- Produce `DriveSyncStore.listDeviceFiles(token)`, `readDeviceFile(token, fileId)`, and `writeDeviceFile(token, deviceId, bytes)` using distinct appData files per device; never overwrite ZIP backup files.
- Stable device ID survives app restart and app upgrades; it is not an auth token or credential.

- [x] Add Drive appData file listing/read/update support for sync payloads while preserving current ZIP backup APIs and retention behavior.
- [x] Serialize/validate schema and owning account before returning remote operations; reject unsupported newer schemas and account mismatch without applying data.
- [x] Keep each device file independent so concurrent uploads cannot overwrite the other device's journal.

### Task 4: Implement idempotent merge and conflict handling

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/sync/DriveSyncEngine.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/data/Daos.kt`

**Interfaces:**
- Produce `suspend fun syncOnce(): SyncResult` through a coordinator-facing engine.
- Apply remote operations in deterministic order keyed by `(deviceId, sequence)` and deduplicate by `operationId`.
- Translate card/list/review references by stable sync IDs; preserve distinct cards even when visible words match.
- Replay review operations deterministically to rebuild review scheduling state; queue events whose referenced card has not arrived yet.
- Apply a single-sided edit; store both conflicting values and base revisions if concurrent edits share the same base; do not silently select a winner.
- Apply a delete only when its base revision is not superseded by an edit; otherwise surface a conflict.

- [x] Implement transactional application of downloaded operations and persist the remote cursor only after commit.
- [x] Merge review events by operation ID and derive card scheduling state deterministically.
- [x] Persist content/delete conflicts without changing the visible card until the user resolves them.
- [x] Keep pending local operations until the Drive write is confirmed; retrying the same batch must not duplicate effects.

### Task 5: Add orchestration, safety snapshots, and lifecycle triggers

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/sync/DriveSyncCoordinator.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/backup/AutoBackup.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/ziaee/zabankhan/backup/Backup.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/ziaee/zabankhan/backup/Backup.ios.kt`
- Modify: `iosApp/iosApp/iOSApp.swift`
- Reuse: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/backup/LocalBackup.kt`

**Interfaces:**
- Coordinator exposes `syncNow()` and debounced `onLocalChange()`; Android/iOS active lifecycle calls `syncNow()` when Drive auth is available.
- Sync sequence is download → local safety backup when needed → transactional apply → upload local journal → mark acknowledged operations; no whole-database restore.
- Network/auth failures preserve pending operations and return actionable status for next foreground/manual attempt.

- [x] Coordinate sync attempts with a single-flight guard and debounce local writes while online.
- [x] Take a local safety snapshot before initial merge and before conflict resolution, reusing existing backup code without uploading credentials.
- [x] Wire Android foreground and iOS active-scene triggers to the coordinator; Bazaar must never initialize or call Drive sync.
- [x] Preserve existing automatic ZIP backup behavior as a separate flow.

### Task 6: Add settings UI for sync state and conflicts

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/backup/BackupSettings.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/`
- Modify: `composeApp/src/commonMain/composeResources/values-fa/`
- Modify: `composeApp/src/commonMain/composeResources/values-fr/`

**Interfaces:**
- Show sync status, last successful sync, Sync now, actionable error, and unresolved conflicts.
- Conflict resolution explicitly lets the user keep either version or retain both as distinct cards where valid; resolve action creates a new journal operation.

- [x] Add the Sync section while keeping existing backup/restore controls intact and clearly separate.
- [x] Add localized strings for default, Persian, and French resources; avoid horizontally scrolling menus/chip rows.
- [x] Keep sync controls hidden or unavailable in unsupported Bazaar builds while preserving normal backup settings.

### Task 7: Verify compatibility and platform builds

**Files:**
- Review all changed files and generated Room schema/migration output.

- [x] With JDK 17, run platform builds: `./gradlew :composeApp:assemblePlayDebug :composeApp:assembleBazaarDebug :composeApp:linkDebugFrameworkIosArm64`.
- [x] Inspect the final diff for accidental sync of excluded files/preferences, credentials, PDF data, or platform-specific settings.
- [x] Confirm the existing full ZIP backup/restore path and schemas remain intact; do not commit.
