# Local motivation

## Intent and constraints
Add points, levels, badges and a configurable weekly goal using existing local data, entirely in common Kotlin. No account, network, points table, schema change or platform code. Preserve the existing home card appearance and daily goal. Provide English, Persian and French strings and respect existing content direction handling.

## Approach
Recommended: observe existing Room data and pass a snapshot to a pure `progress/computeProgress(inputs)` function. Return a serializable `ProgressSummary` with global points/level and language-scoped badge progress. Store only weekly goal and last-seen celebration identifiers in DataStore prefs.

Alternatives: calculate separately inside each screen (duplicates formulas and risks inconsistent displays), or persist a points ledger (conflicts with the request). The shared calculator is the suitable approach; implementation details remain the builder's responsibility.

## Scoring
Use the requested weights: whole reading minutes, two points per review plus one for correct answers, five per currently learned word, twenty per finished text, ten per exam attempt, and ten per day of current streak capped at one hundred. Aggregate all stored languages for total points and level, including languages no longer selected in Settings. Use existing SRS learned status and existing reading completion semantics rather than inventing new definitions. Reuse activityStreak with the same activity/review/shadow inputs as Home.

Level zero begins at zero points. Level n begins at 100*n*(n+1)/2; report points earned within the level, points required for the next level and a clamped fraction. Use Long arithmetic for points.

Badges are scoped to the selected language: first text, streaks of 7 and 30 days, learned words at 100/500/1000, reviews at 1000, finished texts at 10, and reading at 600 minutes. Badge records expose stable id, language, unlocked, current and target values. Levels are global.

## Weekly accounting — decision requiring confirmation
Use the local calendar week, Monday through Sunday, and inject date/time zone into the calculator for deterministic tests. Default goal is 300 points.

Existing activity rows, review logs and exam attempts have suitable dates. Learned words and finished texts do not retain their completion timestamps. Recommended: include their full rewards in total points, but omit those rewards from weekly points. Count the current streak's days falling in this week, subject to the same 100-point cap. Explain the weekly accounting in localized screen text. Do not treat last access/review timestamps as historical completion dates.

An alternative is to approximate completion dates using last access/review timestamps; revisiting a finished text could then move its reward into another week. Exact historical weekly completion rewards would require additional stored event data, outside this design.

## UI and preferences
Extend Home ProgressCard with a localized Level n chip and a thin next-level bar. Card tap opens a new Achievements route; retain Statistics access through Settings. Achievements uses the existing top app bar and card components, showing global level and points, weekly bar and goal, and language-specific badge cards in an adaptive vertical grid. Locked badges use muted colors and current/target text. Keep saved-word interaction and daily goal intact.

Add the weekly goal to Settings > General with the existing StepperSetting. Clamp it to a positive supported range.

Compare fully loaded summaries against last-seen global level and language-qualified badge ids in prefs. Persist monotonic seen values before displaying one short combined snackbar, preventing duplicate celebrations after navigation/restart or streak decay/recovery. Never compare placeholders emitted while data loads. The first complete snapshot reports existing achievements once.

## Validation
Common unit tests cover each score component, the streak cap, empty data, exact level boundaries, every badge threshold, multiple languages, local week boundaries and serialization round-trip. Run the requested Android/iOS Kotlin compile commands with JDK 17; if the Android unflavored task is ambiguous, use the concrete flavor compile tasks. Run the applicable common unit tests and project verification gate where the environment permits, reporting failures accurately.
