# feat: Find Similar Drawables from Project View Context Menu

## Overview

Add a right-click context menu action in the Project View that allows scanning a single drawable file against all other drawables in the project. When the user right-clicks on a drawable file (PNG, JPG, WebP, SVG, or Android XML vector) inside a `res/drawable*` directory, a "Find Similar Drawables" option appears. Clicking it scans only that file's similarity against all others and displays results in the existing Lacak Asset tool window.

## Problem Statement / Motivation

Currently, the only way to find duplicates is to run a **full project scan** via the Tools menu or the tool window toolbar. This is slow and produces all pairs — the user must then manually search the results table for the file they care about. A right-click action on a specific drawable lets the user get targeted results instantly, which is the most natural workflow when reviewing or cleaning up assets.

## Proposed Solution

### New Components

| # | File | Description |
|---|------|-------------|
| 1 | `action/FindSimilarDrawableAction.kt` | New `DumbAwareAction` for the context menu |
| 2 | `service/DrawableScanService.kt` | Add a new `scanSingleFile()` method |
| 3 | `plugin.xml` | Register the action in `ProjectViewPopupMenu` |
| 4 | `toolwindow/DuplicateDrawablePanel.kt` | Minor: ensure it handles filtered results display |

### Implementation Steps

#### Step 1: Create `FindSimilarDrawableAction`

**File:** `src/main/kotlin/id/andriawan/lacakasset/action/FindSimilarDrawableAction.kt`

- Extend `DumbAwareAction` (file extension checks don't need indexing)
- Override `getActionUpdateThread()` → return `ActionUpdateThread.BGT`
- Override `update(e: AnActionEvent)`:
  - Get file via `e.getData(CommonDataKeys.VIRTUAL_FILE)`
  - Set `presentation.isEnabledAndVisible = false` if:
    - `project` is null
    - file is null or `file.isDirectory`
    - extension not in `DrawableFileScanner.DRAWABLE_EXTENSIONS`
    - file is not inside a `drawable` or `drawable-*` directory under `res/` (reuse logic from `DrawableFileScanner`)
  - Set `presentation.isEnabledAndVisible = true` otherwise
  - Disable if `DrawableScanService.isScanning` is already true
- Override `actionPerformed(e: AnActionEvent)`:
  - Get the selected `VirtualFile` and `Project`
  - Call `DrawableScanService.getInstance(project).scanSingleFile(virtualFile)`

#### Step 2: Add `scanSingleFile()` to `DrawableScanService`

**File:** `src/main/kotlin/id/andriawan/lacakasset/service/DrawableScanService.kt`

- Add a new method `scanSingleFile(targetFile: VirtualFile)` that:
  1. Sets `isScanning = true`, fires `onScanStarted`
  2. Uses `DrawableFileScanner` to find all drawable files (respecting exclusions)
  3. Uses `DrawableNormalizer` to normalize & hash the target file
  4. Uses `DrawableNormalizer` to normalize & hash all other files (leveraging cache)
  5. Uses `SimilarityEngine.findSimilarToTarget()` (new method, see Step 3) to compare the target against all others
  6. Fires `onScanCompleted` with the filtered results
  7. Opens/activates the Lacak Asset tool window
- Run with `withBackgroundProgress()` like the existing `startScan()`

#### Step 3: Add `findSimilarToTarget()` to `SimilarityEngine`

**File:** `src/main/kotlin/id/andriawan/lacakasset/engine/SimilarityEngine.kt`

- Add a method `findSimilarToTarget(target: HashedDrawable, candidates: List<HashedDrawable>, threshold: Int): List<SimilarityResult>`
- Same comparison logic as `findSimilarPairs()` but only compares `target` vs each candidate (1-to-N instead of N-to-N)
- This is O(N) instead of O(N²), making single-file scans much faster

#### Step 4: Register Action in `plugin.xml`

**File:** `src/main/resources/META-INF/plugin.xml`

Add inside `<actions>`:

```xml
<action id="LacakAsset.FindSimilarDrawable"
        class="id.andriawan.lacakasset.action.FindSimilarDrawableAction"
        text="Find Similar Drawables"
        description="Find drawables similar to this file"
        icon="/META-INF/pluginIcon.svg">
    <add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>
</action>
```

#### Step 5: Tool Window Integration

**File:** `src/main/kotlin/id/andriawan/lacakasset/toolwindow/DuplicateDrawablePanel.kt`

- Ensure the existing `onScanCompleted` callback works correctly with the filtered results (it should — it just displays whatever `List<SimilarityResult>` it receives)
- Activate/show the tool window when results arrive from a single-file scan (use `ToolWindowManager.getInstance(project).getToolWindow("Lacak Asset")?.show()`)

## Technical Considerations

- **Performance**: Single-file scan is O(N) vs O(N²) for full scan — much faster for large projects
- **Cache reuse**: `DrawableHashCacheService` already caches hashes by file path; single-file scan benefits from previous full scans
- **Thread safety**: Reuse the same `isScanning` flag and `scanJob` coroutine pattern to prevent concurrent scans
- **XML false positives**: The `update()` method must check the parent directory name (not just extension) to avoid showing the action on layout XMLs, manifests, etc.
- **Density variants**: The target file should be deduplicated against density variants just like in the full scan (keep only highest density per resource name)

## Acceptance Criteria

- [ ] Right-clicking a PNG/JPG/WebP/SVG file in a `res/drawable*` directory shows "Find Similar Drawables"
- [ ] Right-clicking an Android XML vector drawable in a `res/drawable*` directory shows "Find Similar Drawables"
- [ ] Right-clicking a non-drawable file (layout XML, manifest, Kotlin file, etc.) does NOT show the action
- [ ] Right-clicking a directory does NOT show the action
- [ ] Clicking the action scans the selected file against all other drawables
- [ ] Results appear in the existing Lacak Asset tool window (auto-opened if closed)
- [ ] The action is disabled while a scan is already in progress
- [ ] Settings (threshold, excluded directories, XML toggle) are respected
- [ ] The action works during IDE indexing (DumbAware)

## Edge Cases

- **File outside `res/drawable*`**: Action hidden — handled by `update()` directory check
- **Only one drawable in project**: Scan completes with 0 results, shows "no similar drawables found"
- **File deleted between right-click and scan start**: Guard with null/validity check on `VirtualFile`
- **Concurrent scan attempt**: Action disabled when `isScanning` is true

## References

- Existing action pattern: `ScanDrawablesAction.kt`
- File type validation: `DrawableFileScanner.kt` (contains `DRAWABLE_EXTENSIONS` and directory checks)
- JetBrains example: [EditExternallyAction.kt](https://github.com/JetBrains/intellij-community/blob/master/images/src/org/intellij/images/actions/EditExternallyAction.kt) — canonical file-filtered context menu action
- SDK docs: [Grouping Actions](https://plugins.jetbrains.com/docs/intellij/grouping-actions-tutorial.html)
