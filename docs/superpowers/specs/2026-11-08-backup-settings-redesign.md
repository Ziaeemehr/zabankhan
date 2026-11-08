# Backup settings redesign

## Intent

Make backup and restore actions feel trustworthy and easy to understand. Users should quickly see whether a recent backup exists and know the safest next action.

## Current state

`BackupSettings` stacks automatic, local-file, and optional Google Drive controls as plain text and similarly styled outlined buttons. PDF inclusion is a separate switch. Restore confirmation and local safety snapshots already exist and must remain intact. FrenchReader import is a separate action below this screen.

## Approaches

1. **Restyle only:** add card borders and spacing but retain the current flat hierarchy. Lowest effort, but backup status and primary action remain unclear.
2. **Status summary plus three method cards (chosen proposal):** show a concise backup status summary, then separate automatic, local-file, and cloud cards with clear status, explanation, and primary/secondary actions. Keeps all existing capabilities visible while making the safest path apparent.
3. **Guided backup wizard:** split backup and restore into staged flows. More hand-holding, but adds steps to frequent actions and hides recovery controls.

The proposed default is approach 2 because backup is high-impact and users need both confidence about the latest snapshot and direct access to restore options.

## Goals

- Give local automatic backup, portable local-file backup, and Google Drive distinct visual groups.
- Make last-backup state and automatic-backup status scannable.
- Establish a clear visual hierarchy for creating a backup and a separate, deliberate restore path.
- Keep PDF inclusion and its current size information understandable.
- Preserve existing restore confirmations, safety snapshots, provider availability behavior, and FrenchReader import.
- Localize new labels in English, Persian, and French.

## Non-goals and anti-goals

- Do not change backup archive format, retention, scheduling, sign-in, restore, or migration behavior.
- Do not add backup destinations or hide restore behind a multi-step wizard.
- Do not imply that cloud backup is available when Drive is not configured.

## Constraints and implementation calls

- Reuse existing Compose Material 3 components, theme, backup operations, and localized resources.
- Keep this as a focused redesign of `BackupSettings`; do not move FrenchReader import into the backup cards.
- Status and actions must reflect existing automatic/local/Drive semantics; derive display state from current preferences and backup metadata.
- Keep destructive restore actions visually secondary and retain existing confirmation dialogs.

## Acceptance

- The page opens with a concise backup status area and presents available methods as clearly separated cards.
- Users can still toggle automatic backups, create/export a local backup, restore from local or automatic snapshots, configure PDF inclusion, and use Drive when configured.
- Existing backup operations and safety behavior remain unchanged.
- English, Persian, and French resource sets include every new visible label.
