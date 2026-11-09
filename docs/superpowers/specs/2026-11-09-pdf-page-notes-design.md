# Anchored PDF Notes

## Intent

Let learners attach editable text notes to a selected word or passage in a PDF. This supports writing exercises and comments that need to stay connected to the relevant part of the page.

## Goals

- Create a note from a text selection using the PDF reader's existing selection actions.
- Reopen a note by selecting the same passage and choosing the note action again.
- Keep each note attached to its selected range; distinct ranges can have separate notes.
- Let learners edit, select, copy, and paste ordinary text.
- Save automatically and restore notes when the PDF is reopened.
- Keep text notes separate from pen and highlighter strokes.
- Keep the editor focused and spacious, and localize it for English, Persian, and French.

## Non-goals

- Persistent on-page note markers or a separate notes browser.
- Notes pinned to arbitrary screen coordinates or blank areas of the PDF.
- Recognizing drawn pen strokes as editable text or generating a font from handwriting.
- Embedding notes into or rewriting the PDF file.
- Cloud synchronization beyond the app's existing backup and restore behavior.

## Current State and Constraints

- The PDF reader extracts page words and supports selecting a word range. The selection toolbar already supports copy, save-to-dictionary, listen, translate, and highlighting.
- PDF highlights store `(textId, page, startWord, endWord)` and render against the extracted word geometry. This provides a direct model for anchoring notes to text.
- Pen strokes are stored separately in `pdf_ink` as normalized vector coordinates. Notes must not be mixed into that JSON.
- The Room database is in common code, currently schema version 6. The reader is Compose Multiplatform; common code must avoid Android/JVM APIs.
- The text selection and OCR/extraction layer only provides reliable word-range anchors where it has extracted selectable words. The first version does not support free-position notes on non-text regions.
- English, Persian, and French resources are supported. Mixed RTL/LTR content should follow its text direction without changing the whole reader layout.

## Approaches

1. **Selected-text note editor (selected).** Add a note action to the existing selection toolbar, save the selected page and word range, and reopen the note when the same range is selected again. This uses the existing word-range model without adding on-page marker layout.
2. **One note per page.** This is simpler to store and open, but it does not connect an exercise comment to the specific phrase it discusses.
3. **Free-position sticky notes.** This permits notes anywhere, but needs coordinate editing, dragging, collision handling, and zoom-aware placement that the current note request does not require.

Approach 1 is selected based on the user's preference to connect a note to a specific part of the page while keeping the first version simple. Reusing selection ranges is more reliable than free-position notes; reopening by reselecting the text avoids marker layout and collision handling.

## Proposed Experience

- The learner selects a word or passage and chooses a localized Note action in the existing PDF selection toolbar.
- A spacious editor opens with the selected text and page number visible as context. The text area supports ordinary typing, selection, copy/paste, and platform-supported handwriting input through the keyboard.
- Changes save automatically. Closing the editor returns to the PDF without an extra save step.
- If the same exact range is selected again, open its existing note instead of creating a duplicate. Different ranges remain separate notes.
- The selected passage is context for the note and remains unchanged; editing the note never alters the PDF text or its existing highlights.
- Existing pen and highlighter interactions remain unchanged.

## Data and Compatibility

- Add a `PdfTextNote` Room entity containing `textId`, `page`, `startWord`, `endWord`, selected-text snapshot, and note body. Identify an existing note by the composite range `(textId, page, startWord, endWord)` so saving the same selection updates that note.
- Add DAO operations to find/upsert by range and delete by note id. Cascade note deletion when its `TextDocument` is deleted.
- Use a Room auto-migration from schema 6 to 7 and export the new schema.
- Include the notes table in the existing user-data backup/restore round trip. PDF file inclusion continues to follow the existing PDF backup preference.
- Keep stored word indices page-scoped and show the selected passage/page as context in the editor.
- Add localized action and editor strings for English, Persian, and French.

## Acceptance Criteria

- Selecting a word range offers a Note action and opens the editor for that exact page/range.
- Saving and reopening a note preserves its body and anchor; selecting the same range again finds the same note.
- Different ranges on a page and the same range on different pages or PDFs remain independent.
- Selecting an existing range again opens its note; another range, page, or PDF opens a different note.
- Text can be entered, edited, selected, copied, and pasted without loss; rapid edits cannot be overwritten by a stale save.
- Deleting a note removes it; deleting the PDF library record cascades its notes.
- Backup/restore preserves notes without forcing PDF files into the backup.
- English, Persian, and French labels render correctly and mixed-direction note text remains editable.
- Existing PDF selection, highlighting, rendering, page navigation, and pen strokes continue to work.
- Freehand stroke recognition, handwriting-font generation, and notes attached to arbitrary non-text coordinates remain out of scope.

## Implementation Choices Left to the Builder

- Reuse the selection popup for note creation and the selected word range as the note key.
- Do not add persistent markers or a separate notes browser in this first version.
- Keep autosave local and ordered so rapid edits cannot be overwritten by an older pending write.
- Use the existing backup table enumeration and round-trip coverage as the compatibility source of truth.
