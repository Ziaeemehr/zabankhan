# Study Activity and Manual Study Log

## Intent

Bring the useful study tracking workflow from FrenchReaderSpike into Zabankhan: users can manually record study time, skill, source, and notes, and review their progress with simple monthly and yearly activity heatmaps. The statistics combine manual sessions with automatically tracked in-app reading, listening, and watching time, as requested. A selected day will show both components separately so users can see what contributed to its combined total. The combined total can count overlapping time twice.

## Goals

- Show a GitHub-style activity heatmap with month and year views.
- Let users add, edit, delete, and review manual study sessions by date, duration, skill, and optional source/note.
- Let users manage named study sources with a type and optional default skill.
- Include manual sessions and automatic activity in combined activity totals and streaks for the active learning language.
- Keep source and skill summaries understandable and simpler than the reference's many chart types.
- Support French and English learning data independently through the existing `lang` fields and `LanguageSpec`-based labels; localize Persian and English UI strings and respect content direction.
- Preserve existing local backup, restore, and FrenchReader import behavior.

## Non-goals

- Preventing overlap between manually entered and automatically tracked time. Both are intentionally included in combined totals.
- Replacing the current in-app activity tracker or existing statistics such as vocabulary review and shadowing scores.
- Adding cloud sync or a new statistics/charting dependency.
- Copying every chart, target setting, and configuration in FrenchReaderSpike.

## Current State and Constraints

- Zabankhan already has `StudySource` and `StudySession` Room entities, language-scoped DAO queries, `StudyLogConverters`, and database schema version 5. The models include source type, default skill, date, duration, skill, source, note, and timestamps.
- Backup/restore and FrenchReader migration code already reads and writes study sources and sessions. Reuse these models and preserve those flows; no new database migration is expected unless source review reveals a missing stored field.
- The current `StatisticsScreen` summarizes app-tracked reading/listening/watching, review activity, shadowing, and vocabulary. It has a seven-day bar chart but no monthly/yearly study heatmap or manual study-log screens.
- FrenchReaderSpike has separate manual study-log screens, session/source CRUD, aggregation logic, and month/year heatmaps. Its main database types are already represented in Zabankhan, but platform-specific ViewModel/date APIs and French-only strings must not be copied blindly.
- Common code must remain Kotlin Multiplatform compatible: no `java.*`, Android APIs, or unsupported regex classes. Use existing Compose Multiplatform, Room, kotlinx-datetime, and resource patterns.
- App-level records and source catalogs are scoped to the currently selected learning language. Do not hardcode French assumptions; use `LanguageSpec` and localized resource strings.
- Existing app statistics and manual-session records can overlap by design. The UI must identify their components in selected-day details.

## Approaches

1. **Separate manual log with combined activity heatmap (selected).** Add the FrenchReader-style session list, add/edit form, and source manager, reusing the existing language-scoped models and DAO. Integrate a simpler year/month heatmap into the existing progress screen using automatic activity plus manual sessions. This preserves source/skill detail without duplicating storage, and makes the intentionally combined total visible.
2. **Only add a heatmap and a quick duration form.** This is smaller, but leaves users without source management, edit/history workflows, or useful skill/source summaries requested from the reference.
3. **Keep manual and automatic statistics fully separate.** This avoids overlap, but does not match the user's selected preference to combine them in one activity view.

Approach 1 is selected because the user explicitly wants manual study logging with source and skill and chose combined reporting despite possible overlap.

## Proposed Experience

- The existing Progress screen gains an activity section with a month/year switch, previous/next period controls, localized calendar labels, color-intensity legend, and concise total/active-day summaries.
- Selecting a day shows combined duration and a clear breakdown of automatic in-app time (reading/listening/watching) and manually logged sessions (including skill, source, and note where present).
- A clearly labeled action opens the manual study log. That screen lists recent sessions and offers add, edit, and delete/undo. A compact action opens source management.
- The entry form records date, duration, one study skill, optional existing source, and optional note. Source creation is available inline; source defaults can suggest a skill without preventing a per-session choice.
- Source management supports creating/editing, archiving, and deleting unused sources. Used sources remain available to historical sessions.
- Aggregates for streaks and combined duration use the same active-language study sessions and local-date interpretation as existing activity statistics. Existing vocabulary, shadowing, and library summaries remain intact.
- Empty states explain how to start recording study time. The monthly and yearly maps use accessible descriptions and readable day details; colors communicate intensity with a textual legend too.

## Data and Compatibility

- Reuse `StudySession`, `StudySource`, and `StudyLogDao` without altering schema version 5 unless implementation inspection proves a field or query is missing.
- Filter all new reads and aggregates by `settings.learningLang`.
- Keep dates as local calendar days using `kotlinx-datetime`; split already-recorded automatic intervals according to the existing tracker.
- Ensure backup/restore and FrenchReader migration continue to include manual study records and their language.
- Preserve per-platform implementation boundaries and commonMain restrictions.

## Acceptance Criteria

- A user can add, edit, and remove manual sessions with validation for future dates, invalid duration, missing sources, and duplicate/in-flight saves as applicable to existing patterns.
- A user can create and manage sources and can associate each manual session with a skill and optional source.
- Monthly and yearly heatmaps represent combined manual plus automatic minutes by local day; navigating periods never goes past the first recorded date or current month/year.
- Tapping a day reveals the combined total and the separate automatic/manual components.
- Combined progress totals and streaks include manual session days while retaining current review/shadowing streak behavior.
- Switching learning language only shows its sessions and sources; labels and layout work for French/English and Persian UI locales.
- Existing backup/restore and FrenchReader migration round trips retain these records.
- Tests cover date aggregation, month/year grid boundaries, language isolation, combined totals, and representative session validation.

## Implementation Choices Left to the Builder

- Use the existing navigation structure to expose the study log from Progress and to provide a clear return path.
- Keep the heatmap to two views (month and year), with selected-day detail, avoiding the reference's cumulative and stacked bar chart suite.
- Reuse current resource catalog patterns where suitable, while retaining the study-log source catalog as a distinct model from reading-library items.
- Prefer small commonMain aggregation functions that can be unit-tested without platform dependencies.
