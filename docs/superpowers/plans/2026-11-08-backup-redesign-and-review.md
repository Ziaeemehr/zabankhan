# Backup Settings Redesign and Three-Round Review Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign backup settings into a clear, status-led experience, then run three documented review and repair rounds across the app.

**Architecture:** Keep backup creation, retention, provider availability, restore, and confirmation behavior unchanged. Rework only the `BackupSettings` presentation and its localized resources, then audit the complete repository in three independent passes: correctness/data safety, cross-platform and navigation behavior, and regression/build/UI integration. Fix confirmed findings as they are found and verify each round before proceeding.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3, common resources, Gradle verification tasks.

**Spec:** `docs/superpowers/specs/2026-11-08-backup-settings-redesign.md`

## Global Constraints

- Do not change backup archives, scheduling, retention, authentication, restore behavior, or migrations as part of the redesign.
- Keep English, Persian, and French resources in sync.
- Keep FrenchReader import separate from the three backup method cards.
- Use JDK 17 and preserve Play/Bazaar/iOS compatibility.
- Do not commit changes.
- For the broad review, inspect the entire repository and fix confirmed issues only; document findings and verification per round.

## Review Focus

- Backup status must distinguish automatic, local export, and Drive timestamps; verify each displayed value comes from the matching source.
- Drive is not configured: the cloud card must be absent without leaving misleading gaps or claiming backup availability.
- Restore actions remain deliberate: local, automatic, and Drive restore retain their existing confirmation and safety-snapshot behavior.
- PDF backup toggle and measured file size remain visible and accurately described.
- Review fixes must not regress Android Play, Bazaar, iOS simulator compilation, unit tests, or the existing backup coverage guarantees.

---

### Task 1: Redesign backup settings presentation

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/ziaee/zabankhan/backup/BackupSettings.kt`
- Modify: `composeApp/src/commonMain/composeResources/values*/batch_a.xml` (or focused backup resource files if the project pattern permits)

**Interfaces:**
- Consumes existing backup APIs, `BackupPrefs`, `AutoBackup`, `DriveBackupClient`, and restore dialogs.
- Produces a status summary and distinct automatic, local-file, and optional Drive cards; underlying operations and data sources remain unchanged.

- [x] Inspect all existing backup flows and string resources before editing.
- [x] Restyle the page with a concise latest-backup summary and three method cards, using clear primary and secondary action hierarchy.
- [x] Keep automatic toggle, automatic restore list, local archive export/import, PDF toggle/size, Drive sign-in/backup/restore/sign-out, and all confirmation dialogs operational.
- [x] Add matching English, Persian, and French copy for new labels.
- [x] Run focused backup tests and platform compilation; inspect the resulting diff for unintended behavior changes.

### Task 2: Review round 1 — data safety and correctness

**Files:**
- Review all tracked source, configuration, and documentation files; modify only files with confirmed defects.
- Add or update focused tests for each confirmed defect where feasible.

**Interfaces:**
- Consumes the entire repository state after Task 1.
- Produces a written round-1 findings log with severity, evidence, fix, and verification for each issue.

- [x] Trace critical data flows: database writes/migrations, backup/restore, imports/exports, authentication/secrets, and destructive operations.
- [x] Search for crashes, data loss, invalid state transitions, resource leaks, and unsafe error handling.
- [x] Fix confirmed issues, add regression coverage, and run the narrowest relevant checks.
- [x] Record reviewed areas, confirmed findings, fixes, and unresolved limits in `docs/reviews/2026-11-08-round-1-data-safety.md`.

### Task 3: Review round 2 — platform, navigation, and lifecycle

**Files:**
- Review all tracked `commonMain`, `androidMain`, `iosMain`, routing, and platform configuration files; modify only files with confirmed defects.
- Add or update focused tests for each confirmed defect where feasible.

**Interfaces:**
- Consumes the fixes and round-1 log from Task 2.
- Produces a written round-2 findings log with severity, evidence, fix, and verification.

- [x] Trace platform boundaries and lifecycle behavior for Android Play, Bazaar, and iOS/iPad, including navigation, back handling, file pickers, background tasks, and platform services.
- [x] Check for platform-specific behavior divergence, unsupported common code, incorrect permission/config assumptions, and lifecycle races.
- [x] Fix confirmed issues, add regression coverage, and run relevant platform compile or test checks.
- [x] Record reviewed areas, confirmed findings, fixes, and unresolved limits in `docs/reviews/2026-11-08-round-2-platforms.md`.

### Task 4: Review round 3 — regression, UX, and final verification

**Files:**
- Review all tracked UI/resources plus Task 1–3 changes; modify only confirmed defects.
- Add or update focused tests for each confirmed defect where feasible.

**Interfaces:**
- Consumes the prior fixes and review logs.
- Produces the final review summary with all three rounds and final verification results.

- [x] Recheck key user journeys and visible states: onboarding, home/tabs, study logging/statistics, settings/backup, library/reader, review, and restore/import flows.
- [x] Inspect localization parity, RTL/LTR behavior, accessibility labels, empty/loading/error states, and layout constraints.
- [x] Fix confirmed regressions and run the full project verification gate.
- [x] Record round-3 findings and a consolidated summary in `docs/reviews/2026-11-08-round-3-regression-and-summary.md`.

## Self-review

- Spec coverage: all backup redesign goals, constraints, and acceptance criteria are covered in Task 1.
- Three review rounds are sequential and distinguish data safety, platform/lifecycle, and regression/UX; each has a durable findings log.
- Review fixes are evidence-led, focused, and paired with regression checks. No blanket refactor is planned.
- Final verification covers Android Play, Android Bazaar, unit tests, iOS simulator tests, and iOS device framework linking.
