## Why

The plugin reports duplicates as *pairs*, so an asset copied into four modules produces six
table rows and the same file appears in most of them — the user has to perform the grouping
mentally before they can decide anything. Finding a duplicate is also where the plugin stops:
there is no way to act on the result from inside the tool window.

Underneath, the same scan is implemented three times (full scan, targeted scan, external-asset
check), each re-reading settings, re-walking the project, and re-hashing, with results delivered
through two unrelated mechanisms. That duplication is why the three flows have drifted apart in
locking behaviour and result presentation, and it makes every UI change cost three edits.

## What Changes

- **BREAKING** Scan results are grouped into **clusters** (connected components over
  above-threshold similarity links) instead of pairs. Each drawable belongs to exactly one
  cluster. The pair remains an internal artefact, no longer a UI concept.
- Each cluster designates a **canonical** member — the copy to keep — chosen by a documented
  heuristic and overridable by the user.
- New **Safe Delete** action on non-canonical members, delegating entirely to IntelliJ's
  `SafeDeleteHandler` so reference checking, conflict preview, and undo come from the platform.
  No reference rewriting is implemented.
- Clusters that span formats (e.g. PNG + Android vector) are kept and flagged; deleting from
  one requires an extra confirmation naming the format change, because raster and vector are
  not interchangeable.
- **BREAKING** The tool window is rebuilt as a **master–detail** layout: cluster list on the
  left, member cards with previews and per-member actions on the right. The old six-column
  pair table is removed.
- New **live threshold slider** and **module/format filters**. Scans retain pairs down to a
  fixed 70% floor so the slider re-clusters in memory without rescanning.
- **BREAKING** The drag-and-drop / "Check Similar Drawable" results dialog is removed. External
  files now appear as a pinned, clearly-marked pseudo-cluster in the same tool window.
  "Find Similar Drawable" selects the cluster containing the chosen file instead of replacing
  all results.
- The three scan flows collapse into one pipeline taking an optional target. Image decoding
  moves out of the read lock and runs in parallel; scan state is published as a single
  `StateFlow`, so results survive closing and reopening the tool window.
- Dead code removed: the never-read stale-results flag, the never-consumed
  "Show refresh reminder" setting, the never-invoked cancel entry point, and three separate
  copies of drawable-extension / drawable-directory / file-size-formatting logic.

## Capabilities

### New Capabilities
- `duplicate-clustering`: Grouping similarity pairs into clusters, canonical member selection,
  mixed-format detection, and the cluster data model consumed by the UI.
- `redundant-asset-removal`: Safe Delete of non-canonical cluster members via the platform
  refactoring engine, including the mixed-format confirmation gate.
- `result-filtering`: Live threshold slider plus module and format filters applied to retained
  pairs without rescanning.

### Modified Capabilities
- `results-ui`: Master–detail cluster presentation replaces the pair table, side-by-side
  preview, savings column, and first-row preselection requirements. Scan state now sourced
  from an observable state holder and re-rendered on reopen.
- `similarity-detection`: Comparison retains pairs down to a fixed floor rather than the
  configured threshold, applies a retention cap, and emits clusters alongside pairs. Targeted
  comparison becomes a filter over the same pass rather than a separate routine.
- `project-scan`: One pipeline serves all three invocation modes; decoding runs outside the
  read lock and in parallel; lifecycle notifications become observable state; the never-called
  cancellation entry point is removed.
- `external-asset-check`: Results render in the tool window as a pinned external pseudo-cluster;
  the dedicated dialog, its table, and its main-panel-isolation requirement are removed.
- `targeted-scan`: Result presentation selects the containing cluster instead of replacing the
  full result set.
- `configuration`: The unused refresh-reminder toggle is removed; the configured similarity
  threshold becomes the initial position of the live slider rather than a scan-time cutoff.
- `hash-caching`: The stale-results flag requirement is removed; nothing consumes it.

## Impact

**Removed**: `toolwindow/DropCheckDialog.kt`, `toolwindow/DropCheckTableModel.kt`,
`toolwindow/SimilarityTableModel.kt`, `DuplicateDrawablePanel`'s duplicate cell renderers and
its private `formatFileSize`, `DrawableHashCacheService.hasChangedSinceLastScan` /
`clearChangedFlag`, `DrawableScanService.cancelScan`, `DrawableAnalyzerSettings.showOutdatedBanner`,
`DrawableFileScanner`'s self-delegating private `isInDrawableDirectory`, and the tool-window
`stretchHeight` manipulation performed from the service layer.

**Rewritten**: `service/DrawableScanService.kt` (three pipelines to one, callbacks to
`StateFlow`), `toolwindow/DuplicateDrawablePanel.kt` (master–detail), `normalizer/DrawableNormalizer.kt`
(`normalizeAndHash` / `normalizeExternalFile` merged; per-worker `SvgRenderer` for Batik thread
safety), `listener/DrawableFileChangeListener.kt` (adopts the strict `res`/`composeResources`
directory check instead of its weaker private copy).

**Added**: cluster model and union-find engine, canonical-selection heuristic, filter state,
Safe Delete action, and the project's first test sources — pure-logic JUnit coverage for
clustering, canonical selection, density deduplication, hash arithmetic, format detection, and
cross-format gating, plus one platform fixture test for drawable discovery.

**Dependencies**: none added. `testFramework(Platform)` is already declared in
`build.gradle.kts` but currently unused; this change puts it to work.

**Risk**: connected-component grouping can chain two visually distinct icons together through
an intermediate image, and the 70% retention floor makes that more likely at low slider
settings. Mitigated by showing each cluster's weakest pairwise similarity so a chained-in
outlier is visible rather than hidden.
