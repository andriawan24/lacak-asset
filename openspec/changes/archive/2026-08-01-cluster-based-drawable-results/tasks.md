## 1. Phase 1 — Dead code removal

- [x] 1.1 Remove `hasChangedSinceLastScan` and `clearChangedFlag` from `DrawableHashCacheService`, and its call site in `DuplicateDrawablePanel`
- [x] 1.2 Remove `showOutdatedBanner` from `DrawableAnalyzerSettings.State` and its checkbox from `DrawableAnalyzerConfigurable`
- [x] 1.3 Remove `DrawableScanService.cancelScan` and the unreachable cancellation notification path
- [x] 1.4 Remove the self-delegating private `isInDrawableDirectory` from `DrawableFileScanner`, leaving the companion function as the single entry point
- [x] 1.5 Bind the excluded-directories field through `bindText` in `DrawableAnalyzerConfigurable` and drop the hand-written `apply`/`reset`/`isModified` overrides

## 2. Phase 1 — Shared logic consolidation

- [x] 2.1 Add a `FileSize.format` helper and replace both copies of `formatFileSize` in `DuplicateDrawablePanel` and `SimilarityTableModel`
- [x] 2.2 Make `DrawableFileChangeListener` use `DrawableFileScanner.DRAWABLE_EXTENSIONS` and `DrawableFileScanner.isInDrawableDirectory` instead of its private copies
- [x] 2.3 Extract excluded-directory parsing from the three inline copies in `DrawableScanService` into one function on the settings state

## 3. Phase 1 — Unified pipeline

- [x] 3.1 Add `ScanState` sealed hierarchy: `Idle`, `Scanning(processed, total)`, `Ready(pairs, clusters)`, `Failed(message)`
- [x] 3.2 Create `ScanPipeline` with a single `run(target: DrawableFile?)` entry performing discovery, format filtering, hashing, and comparison
- [x] 3.3 Read file bytes inside `readAction`; move decoding, SVG rendering, vector conversion, and hashing outside it
- [x] 3.4 Merge `DrawableNormalizer.normalizeExternalFile` into `normalizeAndHash` with the cache as an optional participant and the vector-root validation applied uniformly
- [x] 3.5 Parallelise hashing over `Dispatchers.Default` with a per-worker `SvgRenderer` instance
- [x] 3.6 Add per-file progress reporting and `checkCanceled` between files
- [x] 3.7 Replace the four `onScanX` callback properties with a `StateFlow<ScanState>` exposed by `DrawableScanService`
- [x] 3.8 Rewrite `DrawableScanService.startScan`, `scanSingleFile`, and `scanDroppedFile` as thin wrappers over `ScanPipeline`
- [x] 3.9 Move the tool-window reveal and `stretchHeight` logic out of `DrawableScanService` into the panel
- [x] 3.10 Update `DuplicateDrawablePanel` to collect `StateFlow` instead of assigning callbacks, and to render the current state on attach
- [x] 3.11 Verify Phase 1 compiles and the plugin's existing behaviour is unchanged apart from the reopen fix

## 4. Phase 2 — Cluster model

- [x] 4.1 Add `DrawableCluster` model: members, canonical member, similarity range, mixed-format flag, estimated saving, external flag
- [x] 4.2 Implement union-find clustering over retained pairs in a `ClusterBuilder`
- [x] 4.3 Compute each cluster's strongest and weakest linking similarity
- [x] 4.4 Implement the canonical heuristic: density, then reference count, then pixel area, then byte size, then path
- [x] 4.5 Implement mixed-format detection and estimated saving as the sum of non-canonical member sizes
- [x] 4.6 Change `SimilarityEngine` to retain pairs at a 0.70 floor rather than the configured threshold
- [x] 4.7 Apply the 50000-pair retention cap, keeping the highest scoring, and log when truncation occurs
- [x] 4.8 Emit clusters alongside retained pairs from the comparison pass and carry both in `ScanState.Ready`

## 5. Phase 2 — Tests

- [x] 5.1 Add a `src/test/kotlin` source set and confirm it runs under the existing Gradle configuration
- [x] 5.2 Test union-find clustering: direct pair, transitive chain, disjoint groups, single-drawable exclusion
- [x] 5.3 Test similarity range reporting for uniform, chained, and two-member clusters
- [x] 5.4 Test the canonical heuristic including each tie-break level and determinism under a full tie
- [x] 5.5 Test mixed-format detection and estimated saving, including recomputation after a canonical override
- [x] 5.6 Test the retention floor and the retention cap including the truncation log
- [x] 5.7 Test density-variant deduplication ordering and the same-name-different-module case
- [x] 5.8 Test hash arithmetic: Hamming distance, normalized similarity, dHash and pHash bit widths
- [x] 5.9 Test `DrawableFormat.fromExtension` including case handling and unsupported extensions
- [x] 5.10 Add one `BasePlatformTestCase` covering `DrawableFileScanner` over a synthetic `res/drawable*` and `composeResources/drawable*` tree, including nine-patch and exclusion handling
- [x] 5.11 Keep the existing pair table working by flattening clusters back to pairs, so Phase 2 ships without UI changes

## 6. Phase 3 — Master-detail UI

- [x] 6.1 Build the cluster list component: canonical thumbnail, canonical file name, member count, similarity range, estimated saving
- [x] 6.2 Make the cluster list sortable by saving, member count, and similarity, defaulting to descending saving
- [x] 6.3 Build the member card component: thumbnail, file name, source description, file size, canonical badge, action row
- [x] 6.4 Build the detail pane hosting the member cards for the selected cluster
- [x] 6.5 Replace `DuplicateDrawablePanel`'s vertical split with the horizontal master-detail split and persist its splitter position
- [x] 6.6 Add the mixed-format marker to both the cluster row and the detail pane
- [x] 6.7 Implement cluster preselection and the empty, scanning, and failed states
- [x] 6.8 Implement navigation: double-click a member card opens it, double-click a cluster row opens the canonical member, both no-ops when the project is disposed
- [x] 6.9 Render the external candidate as a pinned cluster marked external, with delete and canonical override disabled on the candidate
- [x] 6.10 Route drag-and-drop and `CompareAssetAction` to the pinned cluster and reveal the tool window instead of opening a dialog
- [x] 6.11 Route `FindSimilarDrawableAction` to select the cluster containing the target, reporting when the target belongs to no cluster
- [x] 6.12 Delete `DropCheckDialog`, `DropCheckTableModel`, `SimilarityTableModel`, and the duplicated percent and thumbnail renderers
- [x] 6.13 Update `DropScanResult` usage or remove the model if the pinned-cluster path makes it redundant

## 7. Phase 4 — Filters and actions

- [x] 7.1 Add the threshold slider covering 70 to 100, initialised from configuration and never writing back to it
- [x] 7.2 Re-cluster in memory on slider movement without rescanning
- [x] 7.3 Add module and format filters, showing a cluster when any member matches
- [x] 7.4 Add the filters-hiding-everything empty state with a clear-filters affordance
- [x] 7.5 Preserve canonical overrides across re-clustering by keying them on file path
- [x] 7.6 Implement deferred reference counting on cluster selection and recompute the canonical member from it
- [x] 7.7 Add mark-as-canonical, open, and reveal actions to the member cards
- [x] 7.8 Implement Safe Delete via `SafeDeleteHandler` on non-canonical members only
- [x] 7.9 Add the mixed-format confirmation naming the removed and retained formats, raised only for mixed clusters
- [x] 7.10 Refresh results after deletion: drop the member, and drop the cluster when fewer than two members remain
- [x] 7.11 Suppress deletion for external candidates while keeping it available on their project matches

## 8. Finalisation

- [x] 8.1 Update `plugin.xml` if action registrations changed
- [x] 8.2 Update `README.md` to describe cluster results, Safe Delete, filters, and the slider
- [x] 8.3 Update `build.gradle.kts` change notes for the release
- [x] 8.4 Run the full build and test suite and confirm both pass
- [x] 8.5 Report which behaviour was verified by tests and which remains unverified because the IDE was not launched
