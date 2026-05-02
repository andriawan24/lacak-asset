---
title: "feat: Add drag-and-drop image similarity check"
type: feat
status: active
date: 2026-04-24
origin: docs/brainstorms/feat-drag-drop-similarity-check-requirements.md
---

# feat: Add drag-and-drop image similarity check

## Overview

Users currently must add an external image to `res/drawable*/` before checking whether it already exists in the project — a multi-step, friction-heavy flow. This feature adds a drop target to the Lacak Asset tool window: drop an image from Finder/Explorer, and a modeless results dialog immediately shows which project drawables are similar. The file is never added to the project.

## Problem Frame

The existing `FindSimilarDrawableAction` requires the target to already be a project `VirtualFile` inside a `drawable` directory. The `DrawableScanService.performSingleFileScan` flow enforces this with an early-return guard on step 5. An entirely new execution path is needed that: (1) wraps an OS `java.io.File` into the engine's data model, (2) bypasses the project-path guard, and (3) delivers results to a standalone dialog without touching the main panel's scan callbacks.

## Requirements Trace

- R1. Drop zone on tool window panel accepts single OS file drops (PNG, JPG, WebP, SVG, XML vector)
- R2. Hover shows visual drop feedback; invalid drops (directories, unsupported types) show no-drop cursor
- R3. Dialog opens immediately with progress indicator; analysis runs on background thread
- R4. Dialog shows dropped file thumbnail, match thumbnails, similarity %, source path per result
- R5. Empty state: "No similar drawables found." when no results exceed threshold
- R6. Row click opens matched file in editor
- R7. Closing dialog leaves tool window panel state unchanged
- R8. Unsupported file errors surface inside the dialog (not a crash)
- R9. Drop while full scan is running is rejected with visual feedback
- R10. Feature works on macOS and Windows

## Scope Boundaries

- Drop target is the tool window panel only — no IDE-wide drop handling
- Dropped file is never added to the project or written anywhere. Note: a cold-cache drop may trigger a full project drawable scan as a side effect (reads project files, writes to `DrawableHashCacheService`). The dropped file itself is never written.
- Main panel scan results table is not affected by drop analysis
- Shared scan callbacks (`onScanStarted`, `onScanCompleted`, etc.) are not touched by drop analysis
- No test infrastructure setup — project has no `src/test/` directory; manual verification path is specified per unit

## Context & Research

### Relevant Code and Patterns

- `src/main/kotlin/id/andriawan/lacakasset/normalizer/DrawableNormalizer.kt` — `private fun loadAsBufferedImage(file: DrawableFile, project: Project): BufferedImage?` (stays `private`; only `normalizeExternalFile` is the public entry point — no cross-class call to `loadAsBufferedImage` is needed); `normalizeAndHash` for the list-processing pattern to replicate for single-file path
- `src/main/kotlin/id/andriawan/lacakasset/service/DrawableScanService.kt` — `withBackgroundProgress` + `CoroutineScope` injection pattern; `performSingleFileScan` for the flow to bypass; `isScanning` state flag to check before accepting drops
- `src/main/kotlin/id/andriawan/lacakasset/service/DrawableHashCacheService.kt` — `ConcurrentHashMap<String, HashedDrawable>` keyed by path string; needs a new `getAllCached(): List<HashedDrawable>` method
- `src/main/kotlin/id/andriawan/lacakasset/engine/SimilarityEngine.kt` — `findSimilarToTarget(target: HashedDrawable, candidates: List<HashedDrawable>, threshold: Double): List<SimilarityResult>`; `computeHashes(file, image, thumbnail, fingerprint)` for single-file hashing
- `src/main/kotlin/id/andriawan/lacakasset/toolwindow/DuplicateDrawablePanel.kt` — panel to register DnD on; existing `FileEditorManager.openFile(file, true)` on `mouseClicked` as the row-click pattern to replicate
- `src/main/kotlin/id/andriawan/lacakasset/toolwindow/ImagePreviewPanel.kt` — `setPreview(img: BufferedImage?, fileName: String)` accepts `BufferedImage` directly; reusable in dialog header without VFS dependency
- `src/main/kotlin/id/andriawan/lacakasset/toolwindow/SimilarityTableModel.kt` — columns layout and `getResultAt(row)` pattern; dialog table model will simplify the column set (drop the Input column, shown in header instead)
- `src/main/kotlin/id/andriawan/lacakasset/model/DrawableFile.kt` — `DrawableFormat.fromExtension(ext): DrawableFormat?` for format detection; `DrawableFile` data class fields for synthetic construction
- `src/main/kotlin/id/andriawan/lacakasset/model/DrawableFile.kt` — `ResourceOrigin` enum for placeholder value in synthetic `DrawableFile`

### Institutional Learnings

- No `docs/solutions/` directory exists in this project — no prior institutional knowledge to draw from.

### External References

- `com.intellij.ide.dnd.DnDSupport` builder with `enableAsNativeTarget()` — required for OS-level file drops into tool windows; raw `TransferHandler` and `java.awt.dnd.DropTarget` are silently intercepted by IntelliJ's internal DnD system and will not work reliably for external drops.
- `DnDTargetChecker.update()` return value: `false` = "I handle this" (inverted from most listener conventions).
- `DnDEvent.setDropPossible(Boolean)` for cursor control; `DnDEvent.setHighlighting(Component, DropTargetHighlightingType)` for hover overlay; `setCleanUpOnLeaveCallback` to clear highlight on drag exit.
- `DialogWrapper(project, null, false, IdeModalityType.MODELESS)` for non-blocking dialog; `show()` not `showAndGet()` for modeless display; `init()` must be the last call in the subclass constructor.
- `LocalFileSystem.getInstance().refreshAndFindFileByIoFile(java.io.File)` to wrap an OS file into a `VirtualFile` with a real `inputStream` and `modificationStamp`.

## Key Technical Decisions

- **DnD API**: Use `DnDSupport.createBuilder(panel).enableAsNativeTarget()` — not `TransferHandler`. Without `enableAsNativeTarget()`, the component only receives intra-IDE drag events and misses OS file manager drops entirely.
- **DrawableFile construction for dropped file**: `LocalFileSystem.refreshAndFindFileByIoFile(ioFile)` provides a real `VirtualFile`; synthetic `DrawableFile` uses `nameWithoutExtension` as `resourceName`, `""` as `densityQualifier`, `"(external)"` as `modulePath`, `"dropped"` as `sourceSet`. `DrawableFormat.fromExtension(ext)` determines format. The dropped file cannot collide with project candidates in `findSimilarToTarget` because `SimilarityEngine` skips any candidate whose `virtualFile.path` equals the target's path (`if (candidate.file.virtualFile.path == target.file.virtualFile.path) continue`) — and an OS file path can never equal a project-internal `VirtualFile` path. The `modulePath = "(external)"` is a descriptive placeholder, not the collision guard.
- **Execution path isolation**: `scanDroppedFile` is a new method on `DrawableScanService` that launches its own coroutine job, sets its own `isDropAnalysisRunning: AtomicBoolean`, and delivers results through a callback parameter — never through the shared `onScanStarted`/`onScanCompleted` lambdas. This preserves tool window panel state.
- **ANDROID_VECTOR external files**: Accepted. `AndroidVectorToSvgConverter.convertToSvg` reads `@color/` references via `ColorResourceResolver`, which gracefully falls back to `#000000` for unresolvable references. Similarity results remain structurally valid; color-heavy vectors may produce weaker matches.
- **`normalizeExternalFile` helper**: Add `fun normalizeExternalFile(file: DrawableFile, project: Project): HashedDrawable?` to `DrawableNormalizer` (calling the promoted-to-`internal` `loadAsBufferedImage`, creating thumbnail, calling `similarityEngine.computeHashes`). This encapsulates the single-file normalization path cleanly.
- **Cache access for candidates**: Add `fun getAllCached(): List<HashedDrawable>` to `DrawableHashCacheService`. `scanDroppedFile` checks if the result is non-empty. If empty (cold start), it runs a full project drawable scan first (same `scanner.findDrawableFiles` → `normalizeAndHash` path as `performSingleFileScan` steps 1–4), populating the cache before calling `findSimilarToTarget`.
- **Dialog reuse on repeat drops**: `DuplicateDrawablePanel` holds a `private var dropDialog: DropCheckDialog?` reference. On each drop: if `dropDialog` is non-null and not disposed, cancel the current `dropJob`, call `dropDialog.resetToLoading(newFileName)`, and start a new job. If disposed or null, create a fresh dialog and call `dialog.show()`.
- **Drop-while-scanning guard**: In `setTargetChecker`, if `scanService.isScanning == true`, call `event.setDropPossible(false)` — this shows the no-drop cursor without any dialog interaction. No tooltip is needed; the cursor change is sufficient.
- **Concurrent drop cancellation**: `scanDroppedFile` returns a `Job`. The panel stores this as `private var dropJob: Job?`. Before starting a new drop, `dropJob?.cancel()` is called. The dialog's progress state is reset synchronously on the EDT before the new job launches.
- **Format detection**: Extension-only via `DrawableFormat.fromExtension()` — consistent with `DrawableFileScanner`. Unsupported extension → `setDropPossible(false)` during hover (no-drop cursor); if a file slips through to the drop handler, open dialog in error state.
- **Dialog Input column**: Dialog table omits the repeated Input column (flagged as redundant in the requirements review — P3 finding). The header already shows the dropped file thumbnail. Table columns: Match thumbnail, Similarity %, Source.

## Open Questions

### Resolved During Planning

- **Dialog modality**: Modeless (`IdeModalityType.MODELESS`). Main panel remains interactable while dialog is open. (see origin: docs/brainstorms/feat-drag-drop-similarity-check-requirements.md)
- **Callback isolation**: Drop analysis owns its own coroutine and callback. Shared scan callbacks are not modified. (flow analysis finding: single-var callbacks would clobber tool window listeners)
- **Second drop while dialog open**: Cancel existing job → reset dialog to Loading state → start new job. One dialog instance per panel, reused. No stacked dialogs.
- **Empty cache state**: `scanDroppedFile` detects empty cache and runs a full project scan first before comparing. Progress indicator in dialog covers this latency.
- **`loadAsBufferedImage` visibility**: Promote from `private` to `internal` in `DrawableNormalizer`. No new class needed.
- **Input column**: Omitted from dialog table (redundant with header thumbnail). Three columns: Match thumbnail, Similarity %, Source.

### Deferred to Implementation

- **`normalizeExternalFile` thumbnail size**: Assume 48×48 ARGB consistent with `HashedDrawable.thumbnail` — confirm by reading the constant from the existing `normalizeAndHash` implementation.
- **`computeHashes` exact signature**: Confirm parameter order (`file`, `image`, `thumbnail`, `fingerprint`) matches the actual `SimilarityEngine` method before calling.
- **`getAllCached()` on cold start**: If `refreshAndFindFileByIoFile` returns null (file moved/deleted between drop event and background execution), return `DropScanResult.Error("File no longer accessible")` and update dialog error state.
- **`DnDEvent.setHighlighting` layer**: If the tool window is embedded in a JCEF panel, `setHighlighting` may not find the right `JLayeredPane`. Fall back to a custom `Border` on the panel if highlighting fails.

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification. The implementing agent should treat it as context, not code to reproduce.*

```
DuplicateDrawablePanel (UI thread)
    │
    ├─ DnDSupport.createBuilder(this)
    │      .enableAsNativeTarget()
    │      .setTargetChecker { event ->
    │            file = extractSingleFile(event) ?: setDropPossible(false); return
    │            if (isScanning) setDropPossible(false); return
    │            format = DrawableFormat.fromExtension(file.ext) ?: setDropPossible(false); return
    │            setDropPossible(true)
    │            setHighlighting(this, RECTANGLE)
    │      }
    │      .setDropHandler { event ->
    │            file = extractSingleFile(event) ?: return
    │            format = DrawableFormat.fromExtension(file.ext) ?: openDialogError("Unsupported"); return
    │            dropJob?.cancel()
    │            dropDialog = openOrReuseDialog(file.name)  // DialogWrapper.show() on EDT
    │            dropJob = scanService.scanDroppedFile(file) { result ->
    │                withContext(EDT) { dropDialog.setState(result) }
    │            }
    │      }
    │      .setCleanUpOnLeaveCallback { this.repaint() }
    │      .install()
    │
    └─ private var dropDialog: DropCheckDialog?
       private var dropJob: Job?

DrawableScanService.scanDroppedFile(ioFile, callback): Job
    ├─ vFile = LocalFileSystem.refreshAndFindFileByIoFile(ioFile) ?: callback(Error); return
    ├─ synthetic = DrawableFile(vFile, nameWithoutExtension, format, "", "(external)", "dropped")
    ├─ candidates = cacheService.getAllCached()
    ├─ if (candidates.isEmpty()) candidates = runFullProjectScan()  // normalizeAndHash all project files
    ├─ target = normalizer.normalizeExternalFile(synthetic, project) ?: callback(Error); return
    ├─ results = similarityEngine.findSimilarToTarget(target, candidates, threshold)
    └─ callback(Success(results, target.thumbnail))

DropCheckDialog (IdeModalityType.MODELESS)
    ├─ Header: ImagePreviewPanel + "Checking: <filename>"
    ├─ Body (states):
    │      Loading  → AsyncProcessIcon + "Analysing…"
    │      Results  → JBTable (DropCheckTableModel: Match | Similarity | Source)
    │      Empty    → JLabel("No similar drawables found.")
    │      Error    → JLabel("<error message>")
    └─ Row click (single-click) → FileEditorManager.openFile(result.fileB.virtualFile, true)
```

## Implementation Units

- [ ] **Unit 1: Expose single-file normalization in `DrawableNormalizer`**

**Goal:** Make `loadAsBufferedImage` accessible outside the class and add a `normalizeExternalFile` convenience method for the drop analysis path.

**Requirements:** R1 (prerequisite: engine must accept external input)

**Dependencies:** None

**Files:**
- Modify: `src/main/kotlin/id/andriawan/lacakasset/normalizer/DrawableNormalizer.kt`

**Approach:**
- `loadAsBufferedImage` stays `private` — `normalizeExternalFile` is the only cross-class entry point; no external caller ever needs to access `loadAsBufferedImage` directly
- Add `fun normalizeExternalFile(file: DrawableFile, project: Project): HashedDrawable?` as a public method that calls `loadAsBufferedImage`, scales the result to a 48×48 thumbnail (same logic as in `normalizeAndHash`), calls `similarityEngine.computeHashes(file, image, thumbnail, fingerprint)`, and returns `HashedDrawable?` or `null` if loading fails
- For `ANDROID_VECTOR` format: first call `vectorConverter.isAaptVector(file.virtualFile)` — if false (e.g., a layout XML or manifest), return null immediately without attempting conversion. If true, call `vectorConverter.extractStructuralFingerprint(file.virtualFile)` to populate `structuralFingerprint` and proceed with `convertToSvg`. This mirrors the `normalizeAndHash` guard and prevents layout/manifest XML files from silently producing bad hashes.

**Patterns to follow:**
- Thumbnail creation and `computeHashes` call pattern from `normalizeAndHash` in `src/main/kotlin/id/andriawan/lacakasset/normalizer/DrawableNormalizer.kt`

**Test scenarios:**
- Happy path: PNG file on disk → `normalizeExternalFile` returns non-null `HashedDrawable` with correct `resourceName`
- Happy path: SVG file on disk → returns `HashedDrawable` with non-null `dHash` and `pHash`
- Edge case: `ANDROID_VECTOR` file with unresolvable `@color/` references → returns `HashedDrawable` (degraded, not null)
- Error path: corrupt/zero-byte PNG → `loadAsBufferedImage` returns null → `normalizeExternalFile` returns null
- Edge case: file extension is valid but `LocalFileSystem` cannot provide `inputStream` → returns null

**Verification:**
- `DrawableNormalizer.loadAsBufferedImage` is callable from `DrawableScanService` (same module, `internal` visibility)
- `normalizeExternalFile` returns a `HashedDrawable` for each supported raster format
- Existing `normalizeAndHash` behavior is unchanged (no regressions in full-scan flow)

---

- [ ] **Unit 2: Add drop analysis execution path to `DrawableScanService`**

**Goal:** New `scanDroppedFile` method that independently normalizes a dropped OS file and compares it against project drawables, isolated from the shared scan callbacks.

**Requirements:** R1, R3, R8, R9

**Dependencies:** Unit 1

**Files:**
- Modify: `src/main/kotlin/id/andriawan/lacakasset/service/DrawableScanService.kt`
- Modify: `src/main/kotlin/id/andriawan/lacakasset/service/DrawableHashCacheService.kt`
- Create: `src/main/kotlin/id/andriawan/lacakasset/model/DropScanResult.kt`

**Approach:**
- `DropScanResult` sealed class: `Success(results: List<SimilarityResult>, droppedThumbnail: BufferedImage)`, `Error(message: String)`
- Add `fun getAllCached(): List<HashedDrawable>` to `DrawableHashCacheService` — returns `ConcurrentHashMap.values().toList()`
- Add `private val isDropAnalysisRunning = AtomicBoolean(false)` to `DrawableScanService`
- Add `fun scanDroppedFile(ioFile: java.io.File, callback: (DropScanResult) -> Unit): Job` — launches on `cs` scope (platform-injected `CoroutineScope`); uses `withBackgroundProgress(project, "Checking similarity…")` internally; delivers result via `withContext(Dispatchers.EDT) { callback(result) }`
- Inside the coroutine: wrap with `LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)`, construct synthetic `DrawableFile`, call `normalizer.normalizeExternalFile`, get candidates from `cacheService.getAllCached()`, if empty run `readAction { scanner.findDrawableFiles(project, excludedDirs) }` then `normalizer.normalizeAndHash(...)` outside the `readAction` (image I/O must not hold the VFS read lock), then call `similarityEngine.findSimilarToTarget(target, candidates, threshold)` using `DrawableAnalyzerSettings.getInstance(project).similarityThreshold` (or whichever getter the existing `performSingleFileScan` uses)
- If `vFile == null` or `normalizeExternalFile` returns null, deliver `DropScanResult.Error(...)` via callback
- Does not set `isScanning = true` — this job is fully independent from the full-scan state machine

**Patterns to follow:**
- `withBackgroundProgress` + `readAction` pattern from `performSingleFileScan` in `src/main/kotlin/id/andriawan/lacakasset/service/DrawableScanService.kt`
- Settings access: `DrawableAnalyzerSettings.getInstance(project)`

**Test scenarios:**
- Happy path: valid PNG file on disk with populated cache → callback receives `Success` with non-empty results sorted by similarity descending
- Happy path: cold cache (no prior scan) → `scanDroppedFile` populates cache and returns results
- Error path: `refreshAndFindFileByIoFile` returns null (file deleted) → callback receives `Error("File no longer accessible")`
- Error path: `normalizeExternalFile` returns null (corrupt file) → callback receives `Error("Could not process file")`
- Empty result: file is valid but no project drawables match above threshold → `Success` with empty results list
- Concurrent safety: `scanDroppedFile` called twice rapidly → second `Job` starts cleanly; both deliver via callback independently

**Verification:**
- Callback is always delivered on EDT (`Dispatchers.EDT` context)
- Full-scan `isScanning` flag is unaffected when drop analysis runs concurrently
- Shared `onScanStarted`/`onScanCompleted` callbacks on the service are not invoked

---

- [ ] **Unit 3: Build `DropCheckDialog`**

**Goal:** Modeless dialog that opens immediately with a loading state, transitions to results/empty/error state when analysis completes, and allows row-click to open matched files.

**Requirements:** R3, R4, R5, R6, R7, R8

**Dependencies:** Unit 2 (uses `DropScanResult`, `SimilarityResult`)

**Files:**
- Create: `src/main/kotlin/id/andriawan/lacakasset/toolwindow/DropCheckDialog.kt`
- Create: `src/main/kotlin/id/andriawan/lacakasset/toolwindow/DropCheckTableModel.kt`

**Approach:**

`DropCheckTableModel`:
- Extends `AbstractTableModel`
- Columns: `["Match", "Similarity", "Source"]` — 3 columns (Input column omitted, shown in header)
- Match column type: `BufferedImage` (thumbnail from `HashedDrawable.thumbnail` via `DrawableHashCacheService.getCached(result.fileB.virtualFile.path)`)
- Source column: `"${result.fileB.resourceName}.${result.fileB.format.extensions.first()} (${result.fileB.modulePath})"` — consistent with `SimilarityTableModel` source rendering
- `fun setResults(results: List<SimilarityResult>)`; `fun getResultAt(row: Int): SimilarityResult?`

`DropCheckDialog`:
- Constructor: `DropCheckDialog(private val project: Project, initialFileName: String) : DialogWrapper(project, null, false, IdeModalityType.MODELESS)`
- `init { title = "Similarity Check"; isModal = false; init() }` — `init()` as last call
- `createCenterPanel()` returns a `BorderLayout` panel with:
  - NORTH: `headerPanel` — horizontal `BoxLayout` with `ImagePreviewPanel` (48×48) + `fileNameLabel: JBLabel`
  - CENTER: `contentPanel` (CardLayout) with three cards: `"loading"` (AsyncProcessIcon + label), `"results"` (JBScrollPane wrapping JBTable), `"empty"` / `"error"` (JBLabel)
- `fun setState(result: DropScanResult)` (must be called on EDT): switches cards, populates table or error label, updates header thumbnail
- `fun resetToLoading(newFileName: String)` (EDT): updates `fileNameLabel`, clears header thumbnail (`imagePreviewPanel.clearPreview()`), clears stale results (`tableModel.setResults(emptyList())`), clears error label text, then shows loading card — ensures no stale state is visible during the loading transition
- Row selection listener: single-click on JBTable → `FileEditorManager.getInstance(project).openFile(result.fileB.virtualFile, true)` — guard with `project.isDisposed()`
- `getDimensionServiceKey()` returns `"LacakAsset.DropCheckDialog"` for persisted size
- `override fun createActions(): Array<Action> = arrayOf(cancelAction)` — only a Close button, no OK

**Patterns to follow:**
- `ImagePreviewPanel.setPreview(img, fileName)` for thumbnail display — `src/main/kotlin/id/andriawan/lacakasset/toolwindow/ImagePreviewPanel.kt`
- `FileEditorManager.getInstance(project).openFile(vFile, true)` on mouse click — `src/main/kotlin/id/andriawan/lacakasset/toolwindow/DuplicateDrawablePanel.kt`
- Column rendering pattern from `SimilarityTableModel` for Source column — `src/main/kotlin/id/andriawan/lacakasset/toolwindow/SimilarityTableModel.kt`

**Test scenarios:**
- Happy path: `setState(Success(results, thumbnail))` with non-empty results → results card shown, table has correct row count, Source column renders `resourceName.ext (modulePath)`
- Happy path: `setState(Success(emptyList(), thumbnail))` → empty card shown with "No similar drawables found."
- Happy path: `setState(Error("message"))` → error card shown with message text
- Happy path: row single-click → `FileEditorManager.openFile` called with `result.fileB.virtualFile`
- Loading state: dialog constructed → loading card is shown by default before any `setState` call
- Edge case: `resetToLoading("new_file.png")` called while results card is showing → loading card appears, file name label updates
- Edge case: `project.isDisposed()` is true when row is clicked → `openFile` is not called, no exception
- Edge case: `result.fileB.virtualFile` is not in `DrawableHashCacheService` → Match column shows null thumbnail (graceful)

**Verification:**
- Dialog is non-blocking: IDE tool window and editor remain interactive when dialog is visible
- Closing the dialog does not modify any service state or tool window content
- `getDimensionServiceKey()` persists dialog size across IDE restarts

---

- [ ] **Unit 4: Register DnD target on `DuplicateDrawablePanel`**

**Goal:** Wire OS file drops into the panel, validate the dropped file, open/reuse the dialog, and launch the drop analysis job.

**Requirements:** R1, R2, R3, R7, R9, R10

**Dependencies:** Unit 2, Unit 3

**Files:**
- Modify: `src/main/kotlin/id/andriawan24/lacakasset/toolwindow/DuplicateDrawablePanel.kt`

**Approach:**
- Add `private var dropDialog: DropCheckDialog? = null` and `private var dropJob: Job? = null` fields
- In the panel's `init` block (after existing UI setup):
  ```
  DnDSupport.createBuilder(this)
      .enableAsNativeTarget()
      .setTargetChecker { event -> ... false }
      .setDropHandler { event -> ... }
      .setCleanUpOnLeaveCallback { repaint() }
      .setDisposableParent(this)
      .install()
  ```
- `setTargetChecker` logic:
  - Extract `DnDNativeTarget.EventInfo` from `event.attachedObject`; if absent, `setDropPossible(false)`; return `false`
  - Extract `List<File>` via `info.transferable.getTransferData(DataFlavor.javaFileListFlavor)`
  - If not exactly one file or the file is a directory → `setDropPossible(false)`; return `false`
  - `DrawableFormat.fromExtension(file.extension)` → if null → `setDropPossible(false)`; return `false`
  - `if (scanService.isScanning)` → `setDropPossible(false)`; return `false`
  - `event.setDropPossible(true)`; `event.setHighlighting(this, DnDEvent.DropTargetHighlightingType.RECTANGLE)`
  - Return `false` (IntelliJ DnD: `false` = "I handle this")
- `setDropHandler` logic (always on EDT):
  - Extract single `java.io.File` from event (same validation as checker); if invalid silently return
  - `DrawableFormat.fromExtension` check again; if null, open dialog in error state; return
  - Cancel previous job: `dropJob?.cancel()`
  - If `dropDialog == null || dropDialog!!.isDisposed` → `dropDialog = DropCheckDialog(project, file.name)`; `dropDialog!!.show()`
  - Else → `dropDialog!!.resetToLoading(file.name)` (dialog already visible)
  - `dropJob = scanService.scanDroppedFile(file) { result -> dropDialog?.setState(result) }`
- `setCleanUpOnLeaveCallback`: calls `repaint()` to clear any custom highlight state
- Register `dropDialog` for disposal in `dispose()`: `dropDialog?.disposeIfNeeded()`

**Patterns to follow:**
- `DnDSupport.createBuilder` with `enableAsNativeTarget()` — IntelliJ Platform SDK `com.intellij.ide.dnd`
- `setDisposableParent(this)` ties DnD lifecycle to panel disposal — critical for tool window teardown
- Existing `dispose()` method in `DuplicateDrawablePanel` for adding cleanup

**Test scenarios:**
- Happy path (macOS): drag valid PNG from Finder → drop-highlight appears → drop → loading dialog opens → results populate
- Happy path (Windows): drag valid PNG from File Explorer → `DataFlavor.javaFileListFlavor` resolves → same flow
- Rejection: drag a `.pdf` file → no-drop cursor shown, no dialog on drop
- Rejection: drag a directory → no-drop cursor shown
- Guard: drag file while full scan is running (`isScanning == true`) → no-drop cursor shown
- Concurrent drop: drop file A → dialog opens loading → immediately drop file B → dialog resets to loading with file B name → results for file B appear
- Integration: row click in open dialog → correct file opens in editor tab
- State preservation: drop analysis runs → open dialog → check tool window table is unchanged

**Verification:**
- `DnDSupport.setDisposableParent(this)` ensures no DnD listener leak after tool window close
- Full-scan progress bar and toolbar button remain functional during drop analysis
- Dialog is non-null only while a drop session is in progress or visible to user

## System-Wide Impact

- **Interaction graph:** `DrawableScanService.isScanning` is read (not written) by the DnD target checker. No existing callbacks, listeners, or observers are modified.
- **Error propagation:** Drop analysis errors are delivered via `DropScanResult.Error` to the dialog's error state. They do not propagate to the main panel or IDE notification system.
- **State lifecycle risks:** `dropDialog` reference must be cleared or checked for `isDisposed` before reuse. `dropJob` must be cancelled on panel disposal to avoid coroutine leaks.
- **API surface parity:** `FindSimilarDrawableAction` (context-menu path) is unchanged. Both paths call `SimilarityEngine.findSimilarToTarget` with the same threshold — consistent results.
- **Integration coverage:** The full drop → hash → compare → display path crosses: OS DnD system → `DuplicateDrawablePanel` → `DrawableScanService` → `DrawableNormalizer` → `SimilarityEngine` → `DropCheckDialog`. Manual integration testing is the primary verification path given no test infrastructure.
- **Unchanged invariants:** `DrawableScanService.isScanning`, shared scan callbacks (`onScanStarted`, `onScanCompleted`, `onScanCancelled`, `onScanError`), and `DuplicateDrawablePanel`'s table model are all unmodified by this feature.

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| `DnDSupport` not routing OS drops correctly in a JCEF-embedded tool window | Test on target IJ version (2024.2+) early; if `enableAsNativeTarget()` fails, fall back to panel-level `TransferHandler` as a platform-specific workaround |
| `refreshAndFindFileByIoFile` returns null for files on network drives or sandboxed paths | Deliver `DropScanResult.Error("File not accessible")` to dialog; no crash |
| ANDROID_VECTOR external file produces wrong hashes due to unresolved `@color/` references | Documented decision: accepted degradation; structural fingerprint matching still works for aapt vectors |
| `DnDNativeTarget.EventInfo` flavor extraction differs between macOS (javaFileListFlavor) and Windows (may also need `DataFlavor.stringFlavor` fallback for some apps) | Use `javaFileListFlavor` as primary; if null, attempt `stringFlavor` extraction and parse file path string |
| `dropDialog` disposed between `dropJob` callback delivery and `setState` call | Guard `dropDialog?.setState(result)` — safe null call |
| Tool window teardown while drop analysis is running | `dispose()` calls `dropJob?.cancel()` and `dropDialog?.disposeIfNeeded()` |

## Documentation / Operational Notes

- No user-facing documentation needed — the drop zone is self-discoverable via standard OS drag behavior.
- The dialog's `getDimensionServiceKey` persists size between IDE restarts via `DimensionService` (IntelliJ built-in).
- No plugin marketplace metadata changes required.

## Sources & References

- **Origin document:** [docs/brainstorms/feat-drag-drop-similarity-check-requirements.md](docs/brainstorms/feat-drag-drop-similarity-check-requirements.md)
- `com.intellij.ide.dnd.DnDSupport` — IntelliJ Platform DnD builder with native target support
- `com.intellij.openapi.ui.DialogWrapper` — IdeModalityType.MODELESS constructor
- `com.intellij.platform.ide.progress.withBackgroundProgress` — coroutine-based background progress
- `LocalFileSystem.getInstance().refreshAndFindFileByIoFile(java.io.File)` — VFS wrapping for external files
