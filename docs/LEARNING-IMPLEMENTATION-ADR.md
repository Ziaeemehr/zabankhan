# Learning Tab implementation decisions and reuse audit

## P0 decisions

- Place Learn in the center of the five bottom destinations, between Library and Words. Keep Review in the bottom bar and Settings accessible from the Home toolbar.
- Keep runtime contracts and development fixtures in the app branch. Use the owner-selected `zabankhan-learn` repository for production content and a configurable HTTPS GitHub Pages origin, following the existing dataset delivery model. Confirm the production URL and license before publication. Draft fixtures do not require a live server.
- Start with French A1–B2 and require Persian instructional fields. Keep target language, instructional locale and UI locale separate, and allow additional levels and instructional locales in the data model.
- Use on-device TTS for offline practice. Treat authentic recordings as separately licensed assets. Keep both current Android flavors in the verification gate.
- Include learning progress in backup and restore once the storage contract lands. Defer adding this state to Drive library sync until its merge policy is decided.

## Reuse map

| Area | Branch evidence and decision |
| --- | --- |
| Navigation | `App.kt` and `HomeScreen` own Home and its feature entry points. Register Learn in the bottom bar and reuse the grammar, exams, reader and library destinations. |
| Language and offline assets | `LanguageSpec` owns target-language behavior. `LanguageDataPacks` in `datapack/LanguageDataPack.kt` resolves bundled resources by target language. Extend the existing pack access path when adding installed learning packs. |
| Reading, dictionary and Leitner | `TappableLearningText`, `ui/reader/DictionarySheet.kt` and `ReadingScreen` provide the text and dictionary interaction patterns. Route explicit vocabulary saves through the existing vocabulary flow and `VocabSrs`. |
| TTS | `Tts` in `speech/Speech.kt` and `ActivityTts` already serve Android and iOS callers. Use the active language and local engine for offline activities. |
| PDF and EPUB | Home and Library route existing PDFs to `pdf/{id}` and other text to `reader/{id}`. Reuse the existing add route and platform file picker for user-owned files, with `ImportRepository` handling text and EPUB. |
| Downloads | `DatasetRepository` already resolves manifests, streams downloads and imports decks and stories. `OwnDictionary` has a separate dictionary cache on the same HTTPS origin. Reuse common transport and the platform ZIP support in `content/ZipArchive.kt`; learning pack integrity and atomic replacement still need their own acceptance tests. |
| Progress and backups | `AppDatabase` exports schema 11 and exposes `ExamAttemptDao`. Add learning persistence through a migration with coverage modeled on `BatchAMigrationTest`. `LocalBackup` snapshots the database and validates schema on restore. `DriveBackup` and `DriveSyncCoordinator` are distinct integration points to verify before claiming restore or sync support. |

## Existing content and publication status

- The provenance trail for the grammar notes begins with the French Reader migration and subsequent notes copy. That identifies where the app obtained the files. Item-level origins and redistribution rights need verification before porting them into a production learning pack. The bundled poster catalog currently has no entries.
- The bundled exam guide records official sources and checked dates. Reverify each variant against current official specifications before publishing exam practice.
- The TCF prompt metadata enumerates 897 prompts by task and supplies no item-level rights evidence. Keep the bank outside production learning packs pending provenance and license verification.
- Telegram exports and their scanner output stay private. Prioritize illustrated children's and teen periodicals. Catalog entries require publisher verification and a rights record before release. Pilot educational content still requires owner approval and, where LLM-generated or materially edited, three independent reviews on the same revision.

P1 starts with draft fixtures, a local catalog contract and validation, then deterministic scoring with platform NFC. Production delivery, exam variant selection and the starter pack size decision can follow the applicable milestones.

## Initial curriculum and source register

These are acquisition candidates. Before using any source text or asset, record its exact version and source locator, rights and allowed uses. Verify outcome mappings against the CEFR Companion Volume during reference-packet review.

| Level | Initial outcome | Candidate independent practice |
| --- | --- | --- |
| A1 | Exchange basic personal information in a first meeting | Match questions and replies, then record a short introduction |
| A2 | Arrange an everyday appointment | Read a short schedule and propose a suitable time |
| B1 | Explain a past expectation and compare it with what happened | Read an original concert account and explain the contrast |
| B2 | Support a position and respond to a counterargument | Compare two original short viewpoints and write a response |

| Source candidate | Intended use | Current rights evidence |
| --- | --- | --- |
| Council of Europe CEFR Companion Volume | Outcome mapping | Verify edition, locators and reuse terms before copying descriptors |
| France Éducation international | Exam variant and format references | Verify current official pages and checked dates before an exam pilot |
| Bayard Jeunesse periodicals | Illustrated magazine discovery, beginning with Pomme d’Api and Okapi | Verify official listings and level estimates. No file redistribution rights established |
| Wikisource and Vikidia | Potential authentic reading | Verify license and attribution for each selected text and each illustration |
| Édito and Grammaire progressive | Bibliographic and progression references | No reproduction permission established |

The two draft pilot reference packets in `tools/learning/fixtures` exercise stable IDs, canonical French text and Persian localization for an A1 selection and B1 text response. Their claim locators and reviews are still pending. The packets are authoring examples and are excluded from the app bundle and catalog release tool.
