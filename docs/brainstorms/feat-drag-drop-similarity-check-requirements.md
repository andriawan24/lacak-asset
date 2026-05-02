# Requirements: Drag-and-Drop Similarity Check

**Date:** 2026-04-24  
**Status:** Draft  
**Author:** andriawan24

---

## Problem

Checking whether an external image already exists as a project drawable requires multiple steps: manually add the file to `res/drawable*/`, then right-click → "Find Similar Drawable". This friction discourages pre-addition checks and wastes time when the asset turns out to be a duplicate.

## Goal

Let users drag an image file from the OS file manager directly onto the Lacak Asset tool window to immediately check its similarity against project drawables — without adding the file to the project first.

---

## User Flow

1. User opens the Lacak Asset tool window.
2. User drags an image file from Finder / Explorer onto the panel.
3. Panel shows a visual drop-highlight (border glow or overlay) while hovering.
4. On drop: plugin runs `SimilarityEngine.findSimilarToTarget()` against the dropped file.
5. A floating dialog opens showing results.
6. Clicking a match row opens the matched project drawable in the editor.
7. Closing the dialog returns state to normal — main panel is unaffected.

---

## Scope

### In scope
- Drop target: Lacak Asset tool window panel only
- Single file per drop event (users may re-drop after closing the dialog for another check)
- Supported formats: PNG, JPG, WebP, SVG, XML vector drawable (same formats as existing normalizer)
- Floating dialog with similarity results
- Click-to-open matched file in editor

### Out of scope
- Dropping multiple files at once
- Drop targets outside the tool window (editor, project tree, etc.)
- Adding the dropped file to the project
- Persisting or saving the dropped file anywhere
- Modifying the main scan results table

---

## Dialog Specification

**Header:** `Checking: <filename>` with a small thumbnail of the dropped image.

**Table columns:**

| Column | Content |
|--------|---------|
| Input | Thumbnail of dropped image |
| Match | Thumbnail of matching project drawable |
| Similarity | Percentage (e.g. `94%`) |
| Source | Resource name / relative path of match |

**Empty state:** "No similar drawables found." message when no matches exceed the similarity threshold.

**Actions:**
- Single-click or double-click a row → opens matched file in the IDE editor
- Close button → dismisses dialog, no side effects

---

## Acceptance Criteria

1. Dragging a supported image file over the tool window panel shows a visible drop-highlight.
2. Dropping a file triggers similarity analysis and opens the results dialog.
3. Dialog header shows the dropped filename and a preview thumbnail.
4. Results table shows: input thumbnail, match thumbnail, similarity %, source path.
5. When zero matches are found, dialog shows an empty state message rather than an empty table.
6. Clicking a result row opens the matched drawable in the editor.
7. Closing the dialog leaves the main panel state unchanged.
8. Dropping an unsupported file type shows an error message in the dialog (not a crash).
9. Dragging a directory or non-image file is rejected with visual feedback.
10. Drop works on both macOS and Windows.
11. While similarity analysis runs, the dialog shows a progress indicator and the IDE remains responsive.

---

## Non-Goals / Constraints

- Do not change how the existing "Find Similar Drawable" context menu action works.
- Do not alter the main similarity table when a drop check is performed.
- No file is written to disk from the dropped input.

---

## Decisions

- **Similarity threshold:** Reuse the existing global similarity threshold setting. No separate threshold control added for this feature. Note: the threshold applies only to perceptual hash comparison (DHash/PHash for PNG/JPG/WebP/SVG). Structural fingerprint matching for XML vector drawables is always exact-match and is unaffected by the threshold.
- **Loading state:** The dialog opens immediately on drop and shows a progress indicator while hashing runs. Analysis executes on a background thread (same pattern as `DrawableScanService`). Results replace the progress indicator when complete.
