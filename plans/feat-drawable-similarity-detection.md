# feat: Drawable Similarity Detection

## Overview

Build the core feature of the Lacak Asset IntelliJ plugin: scan Android project drawable resources across all formats (PNG, JPG, WebP, SVG, Android XML vector drawables) and detect pairs with >90% visual similarity using perceptual hashing. Results are displayed in a tool window with side-by-side image previews.

## Problem Statement

In large Android teams, developers frequently duplicate drawable resources because:
- Designer naming isn't clear or consistent
- Different developers export the same asset independently
- Same icon exists in different formats (PNG + SVG + XML vector)
- No automated tool exists to detect visual duplicates across formats

This leads to bloated APK sizes and maintenance confusion.

## Technical Approach

### Architecture

```
User clicks "Scan" button in Tool Window
       |
       v
DrawableScanService (project service, coroutine-based)
       |
       v
DrawableFileScanner (VFS traversal)
  - Finds all drawable files in res/drawable* directories
  - Filters by extension: png, jpg, jpeg, webp, svg, xml (vector only)
  - Groups by logical resource name to exclude density variants
       |
       v
DrawableNormalizer (format-agnostic image loading)
  - PNG/JPG/JPEG: javax.imageio.ImageIO
  - WebP: TwelveMonkeys ImageIO plugin
  - SVG: Apache Batik Transcoder
  - Android XML vector: Parse XML -> Convert to SVG -> Batik render
  - All output: 128x128 ARGB BufferedImage (aspect-ratio preserved, white background)
       |
       v
SimilarityEngine (two-stage perceptual hashing via JImageHash)
  - Stage 1: dHash (64-bit) fast filter at >= 0.80 normalized similarity
  - Stage 2: pHash (32-bit) confirmation at >= 0.90 normalized similarity
  - Skip comparisons between density variants of same resource name
       |
       v
DrawableHashCacheService (in-memory + persistent state)
  - Key: file path + modification timestamp
  - Stored in workspace file (not committed to VCS)
  - Invalidated by file change listener
       |
       v
DuplicateDrawableToolWindow (JBTable + JBSplitter + image preview)
  - Table columns: Image A, Image B, Similarity %, Module
  - Bottom panel: side-by-side image preview
  - Double-click to navigate to file in project tree
  - Toolbar: Scan, Refresh, Settings
```

### Key Design Decisions

1. **Perceptual hashing over neural networks**: JImageHash (dHash + pHash) is lightweight (~100KB), fast (ms per image), requires no GPU or model files. Suitable for an IDE plugin. Neural embeddings (CLIP/ONNX) are overkill for format-converted duplicate detection.

2. **Two-stage comparison**: dHash pre-filter eliminates 95%+ non-matches instantly. pHash confirms at higher fidelity. This keeps 1000+ drawable scans under 30 seconds.

3. **Density variant exclusion**: Files with the same base name (`ic_launcher`) in different density directories (`drawable-hdpi`, `drawable-xhdpi`) are NOT compared against each other. These are intentional variants.

4. **Android XML vector handling**: Parse `<vector>` XML, extract `android:pathData` and `android:fillColor`, convert to SVG string, render via Batik. Color references (`@color/primary`) are resolved by parsing `res/values/colors.xml`; unresolvable colors fall back to black.

5. **Manual scan only**: Scan is triggered by explicit button click. File changes invalidate the cache and show a "Results may be outdated" banner, but do not auto-trigger re-scan.

6. **Android plugin is optional**: Declare `org.jetbrains.android` as an optional dependency. The plugin detects Android projects by checking for `**/src/*/res/drawable*` directories via VFS, not via Android SDK APIs.

### Dependencies (build.gradle.kts)

```kotlin
dependencies {
    // Image hashing
    implementation("dev.brachtendorf:JImageHash:1.0.0")

    // WebP support
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")

    // SVG rendering
    implementation("org.apache.xmlgraphics:batik-transcoder:1.17")
    implementation("org.apache.xmlgraphics:batik-codec:1.17")

    intellijPlatform {
        intellijIdea("2025.2.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}
```

## Implementation Phases

### Phase 1: Foundation (Scaffold + File Discovery)

Create the project structure and implement drawable file discovery.

**Files to create/modify:**

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Add JImageHash, TwelveMonkeys, Batik dependencies |
| `plugin.xml` | Register tool window, notification group, settings, actions, file listener |
| `DrawableFile.kt` | Data class representing a discovered drawable resource |
| `DrawableFileScanner.kt` | VFS traversal to find all drawable files, group by resource name |
| `DrawableScanService.kt` | Project service orchestrating scan with coroutine + progress reporting |

**Acceptance criteria:**
- [ ] `DrawableFileScanner` finds all PNG, JPG, WebP, SVG, and XML vector files in `res/drawable*` directories
- [ ] Files are grouped by logical resource name (e.g., `ic_launcher` across densities)
- [ ] Scan runs in background thread with cancellation support and progress indicator
- [ ] Non-Android projects show "No drawable resources found" empty state
- [ ] Build directories (`build/`, `.gradle/`) are excluded from scanning

### Phase 2: Image Normalization Pipeline

Convert all drawable formats into comparable `BufferedImage` instances.

**Files to create:**

| File | Purpose |
|------|---------|
| `DrawableNormalizer.kt` | Unified entry point for loading any drawable format as BufferedImage |
| `SvgRenderer.kt` | Renders SVG files to BufferedImage using Apache Batik |
| `AndroidVectorConverter.kt` | Parses Android XML vector drawables, converts to SVG, renders via Batik |
| `ColorResourceResolver.kt` | Resolves `@color/` references from `res/values/colors.xml` |

**Acceptance criteria:**
- [ ] PNG, JPG, JPEG loaded via `ImageIO.read()`
- [ ] WebP loaded via TwelveMonkeys `ImageIO.read()` (auto-registered)
- [ ] SVG rendered to 128x128 BufferedImage via Batik transcoder
- [ ] Android XML vectors (`<vector>` root tag) converted to SVG then rendered
- [ ] `@color/name` references resolved from `colors.xml`; unresolvable fall back to black
- [ ] All images normalized to 128x128 ARGB with white background, aspect ratio preserved (letterboxed)
- [ ] Animated vector drawables (`<animated-vector>`) are skipped
- [ ] Nine-patch (`.9.png`) files are skipped
- [ ] Corrupted/unreadable files are logged and skipped without crashing the scan

### Phase 3: Similarity Engine + Cache

Implement perceptual hash comparison and result caching.

**Files to create:**

| File | Purpose |
|------|---------|
| `SimilarityEngine.kt` | Two-stage dHash/pHash comparison, produces `SimilarityResult` list |
| `SimilarityResult.kt` | Data class: fileA, fileB, similarity percentage, algorithm used |
| `DrawableHashCacheService.kt` | Project service caching computed hashes, keyed by path + modStamp |

**Acceptance criteria:**
- [ ] dHash (64-bit) computed for all normalized images
- [ ] Candidate pairs with dHash normalized similarity >= 0.80 proceed to Stage 2
- [ ] pHash (32-bit) computed for candidates; pairs with >= 0.90 are flagged as duplicates
- [ ] Density variants of same resource name are excluded from comparison
- [ ] Cache stores `(filePath, modificationStamp, dHash, pHash, thumbnail)` per file
- [ ] Cache entries invalidated when file's `modificationStamp` changes
- [ ] For N files, comparison is O(N^2) on hash distances (nanoseconds per pair, acceptable up to ~2000 files)

### Phase 4: Tool Window UI

Build the results display with table, previews, and toolbar.

**Files to create/modify:**

| File | Purpose |
|------|---------|
| `DuplicateDrawableToolWindowFactory.kt` | Replaces `MyToolWindowFactory`; registers the real tool window |
| `DuplicateDrawablePanel.kt` | `SimpleToolWindowPanel` with toolbar, table, and preview splitter |
| `SimilarityTableModel.kt` | Table model for JBTable: columns for file names, similarity, module |
| `ImagePreviewPanel.kt` | Custom Swing panel rendering a `BufferedImage` with file label |
| `ScanDrawablesAction.kt` | Toolbar action triggering `DrawableScanService.startScan()` |
| `NavigateToFileAction.kt` | Double-click handler to open file in editor or project tree |
| `MyMessageBundle.properties` | Add all UI strings (tool window title, empty states, progress messages) |

**UI Layout:**
```
+----------------------------------------------------------+
| [Scan] [Refresh]                     [Settings]          |  <- Toolbar
+----------------------------------------------------------+
| Image A        | Image B       | Similarity | Module     |  <- JBTable
|----------------|---------------|------------|------------|
| ic_home.png    | home_icon.svg | 95%        | :app       |
| bg_splash.webp | splash_bg.png | 92%        | :core:ui   |
| ...            |               |            |            |
+----------------------------------------------------------+
| [Preview: ic_home.png]  |  [Preview: home_icon.svg]     |  <- JBSplitter
|                          |                               |
+----------------------------------------------------------+
```

**Acceptance criteria:**
- [ ] Tool window registered with id "Lacak Asset" and palette icon
- [ ] Table shows all duplicate pairs sorted by similarity (highest first)
- [ ] Selecting a row updates the side-by-side preview panel below
- [ ] Double-clicking a file name navigates to the file in the project tree
- [ ] Empty state: "Click 'Scan' to find similar drawables" when never scanned
- [ ] Empty state: "No similar drawables found" when scan completes with zero results
- [ ] Scan button disabled during active scan; progress shown via `table.setPaintBusy(true)`
- [ ] Progress indicator in status bar: "Scanning 45/200 drawables..."

### Phase 5: Settings + File Change Listener

Add configurable threshold and cache invalidation on file changes.

**Files to create:**

| File | Purpose |
|------|---------|
| `DrawableAnalyzerSettings.kt` | `PersistentStateComponent` storing threshold, exclusions |
| `DrawableAnalyzerConfigurable.kt` | Settings page under Tools, Kotlin UI DSL v2 |
| `DrawableFileChangeListener.kt` | `AsyncFileListener` invalidating cache on drawable changes |

**Settings:**
- Similarity threshold (default: 90%, range: 50-100%)
- Excluded directories (comma-separated, default: empty)
- Include XML drawables (default: true)

**Acceptance criteria:**
- [ ] Settings page accessible via Settings > Tools > Lacak Asset
- [ ] Changing threshold re-filters existing cached results (no re-scan needed)
- [ ] File changes to drawables invalidate affected cache entries
- [ ] "Results may be outdated" banner shown when files changed since last scan
- [ ] Settings stored per-project in workspace file

## Data Model

```kotlin
data class DrawableFile(
    val virtualFile: VirtualFile,
    val resourceName: String,        // "ic_launcher" (without extension)
    val format: DrawableFormat,      // PNG, JPG, WEBP, SVG, ANDROID_VECTOR
    val densityQualifier: String,    // "hdpi", "xhdpi", "" for default
    val modulePath: String           // ":app", ":core:ui"
)

enum class DrawableFormat {
    PNG, JPG, WEBP, SVG, ANDROID_VECTOR
}

data class HashedDrawable(
    val file: DrawableFile,
    val dHash: Hash,                 // JImageHash Hash object
    val pHash: Hash,
    val thumbnail: BufferedImage,    // 48x48 for UI display
    val modificationStamp: Long
)

data class SimilarityResult(
    val fileA: DrawableFile,
    val fileB: DrawableFile,
    val similarityPercent: Int,      // 0-100
    val normalizedSimilarity: Double // 0.0-1.0 from pHash
)
```

## File Structure (New Files)

```
src/main/kotlin/id/andriawan/lacakasset/
  model/
    DrawableFile.kt
    DrawableFormat.kt
    HashedDrawable.kt
    SimilarityResult.kt
  scanner/
    DrawableFileScanner.kt
  normalizer/
    DrawableNormalizer.kt
    SvgRenderer.kt
    AndroidVectorConverter.kt
    ColorResourceResolver.kt
  engine/
    SimilarityEngine.kt
  service/
    DrawableScanService.kt
    DrawableHashCacheService.kt
  settings/
    DrawableAnalyzerSettings.kt
    DrawableAnalyzerConfigurable.kt
  toolwindow/
    DuplicateDrawableToolWindowFactory.kt
    DuplicateDrawablePanel.kt
    SimilarityTableModel.kt
    ImagePreviewPanel.kt
  action/
    ScanDrawablesAction.kt
    NavigateToFileAction.kt
  listener/
    DrawableFileChangeListener.kt
```

## Edge Cases Handled

| Edge Case | Handling |
|-----------|----------|
| Non-Android project | Show "No drawable resources found" empty state |
| 0 drawable files | Show "No drawable resources found" |
| Density variants (same name, different qualifier) | Excluded from comparison |
| Corrupted/unreadable image | Log warning, skip file, continue scan |
| 0-byte file | Skip with warning |
| Animated vector drawable | Skip (not a static image) |
| Nine-patch (.9.png) | Skip (special format) |
| `@color/` reference in XML vector | Resolve from colors.xml, fallback to black |
| Very large project (1000+ drawables) | Background thread, progress indicator, cancellable |
| User cancels mid-scan | Show partial results with "Scan cancelled" banner |
| Scan clicked while scan running | Button disabled during active scan |
| File changed since last scan | Cache invalidated, "outdated" banner shown |
| Symlinks in drawable directory | `VfsUtilCore.iterateChildrenRecursively` handles cycles |
| Build/generated directories | Excluded from scan |

## Dependencies & Risks

| Risk | Mitigation |
|------|-----------|
| Apache Batik is large (~5MB) | Only include `batik-transcoder` + `batik-codec`, not full Batik |
| JImageHash may not be maintained | Library is stable, pure Java, no runtime dependencies. Can be forked if needed |
| Android XML vector attributes not fully convertible to SVG | Handle common cases (path, fillColor, strokeColor); skip complex cases (gradients, clip-path groups) |
| Memory usage with 1000+ BufferedImages | Process images sequentially: load, hash, discard. Only keep 48x48 thumbnails in cache |
| IntelliJ API breaking changes in future versions | Target `sinceBuild = 252`, use only stable APIs documented in SDK |

## References

- [JImageHash - Perceptual Image Hashing](https://github.com/KilianB/JImageHash)
- [Apache Batik SVG Toolkit](https://xmlgraphics.apache.org/batik/)
- [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys)
- [IntelliJ Platform SDK - Tool Windows](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)
- [IntelliJ Platform SDK - Background Processes](https://plugins.jetbrains.com/docs/intellij/background-processes.html)
- [IntelliJ Platform SDK - Virtual File System](https://plugins.jetbrains.com/docs/intellij/virtual-file-system.html)
- [IntelliJ Platform SDK - Services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html)
- [IntelliJ Platform SDK - Settings](https://plugins.jetbrains.com/docs/intellij/settings-guide.html)
- [IntelliJ Platform SDK - Kotlin UI DSL v2](https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html)
- [IntelliJ Platform SDK - Plugin Listeners](https://plugins.jetbrains.com/docs/intellij/plugin-listeners.html)
- [Android VectorDrawable Resources](https://developer.android.com/develop/ui/views/graphics/vector-drawable-resources)
