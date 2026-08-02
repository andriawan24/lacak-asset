# Lacak Asset

An IntelliJ IDEA plugin that finds duplicate drawable resources in Android projects and helps
you delete the redundant copies.

<img width="1552" height="899" alt="Screenshot 2026-03-13 at 15 01 20" src="https://github.com/user-attachments/assets/0d027ef7-5aef-419e-bf7e-e8625253297f" />

> The screenshot predates the cluster-based tool window and will be refreshed.

## Features

- Scans drawable resources across PNG, JPG, WebP, SVG, and Android XML vector formats
- Uses perceptual hashing (dHash + pHash) to identify visually similar images
- Compares Android vector drawables by structural fingerprint (code equality)
- Groups every copy of an asset into **one row**, rather than listing each pair separately
- Picks the copy worth keeping, and lets you override that choice
- Deletes redundant copies through the IDE's own Safe Delete refactoring
- Live similarity slider plus module and format filters, applied without rescanning
- Deduplicates density variants (mdpi, hdpi, xhdpi, etc.) before comparison
- Checks a file that is not yet in the project, before you add it
- Supports KMM and Compose Multiplatform drawable scanning

## Installation

1. Open IntelliJ IDEA or Android Studio
2. Go to **Settings → Plugins → Marketplace**
3. Search for **Lacak Asset** and install

Alternatively, build from source and install the plugin JAR manually via **Settings → Plugins → Install Plugin from Disk**.

## Usage

### Scan for duplicates

Go to **Tools → Lacak Asset → Scan All Drawables**, or use the toolbar in the **Lacak Asset**
tool window at the bottom of the IDE.

Results appear as a list of duplicate **groups** on the left. Selecting a group shows every
copy on the right, with the one to keep badged **Keep this one**.

### Delete a redundant copy

Select a group, then use **Delete…** on any copy other than the one being kept. Deletion runs
through IntelliJ's Safe Delete, so you get a usage preview, conflict reporting, and undo.
The kept copy is never offered for deletion, so a group cannot be emptied by accident.

If a group mixes formats — a PNG and an Android vector, say — you get an extra confirmation
naming both. Those formats are not interchangeable; rendering, tinting, and supported API
levels can differ.

### Choose which copy to keep

The plugin picks one by density, then how widely each copy is referenced, then dimensions,
then file size. To keep a different one, use **Keep this instead** on its card.

### Find similar drawables to one file

Right-click any drawable in the Project view and select **Find Similar Drawables** to jump to
the group that file belongs to.

### Check a file before adding it

Use **Tools → Lacak Asset → Check External Drawable…**, or drag a file onto the tool window.
The file appears as a pinned group at the top, marked as external. Nothing is copied into the
project and nothing is written to disk.

### Filtering

The tool window's controls filter results already in memory — no rescanning:

| Control | Effect |
|---|---|
| Similarity slider | Only show groups whose copies are at least this similar (70–100%) |
| Module | Only show groups with a copy in the chosen module |
| Format | Only show groups containing the chosen format |

### Configuration

Go to **Settings → Tools → Lacak Asset** to configure:

| Setting | Default | Description |
|---|---|---|
| Similarity threshold | 90% | Starting position of the tool window slider |
| Excluded directories | _(none)_ | Comma-separated directory names to skip |
| File types | All enabled | Which formats participate in a scan |

## How it works

1. **Scanning** — `DrawableFileScanner` walks the project's `res/drawable*` and
   `composeResources/drawable*` directories and collects drawable files.
2. **Loading** — `ScanPipeline` reads file bytes under a read action, then releases the lock.
   Decoding, rendering, and hashing run outside it, in parallel.
3. **Normalization** — `DrawableNormalizer` renders each file to a `BufferedImage`. SVG files
   use Apache Batik; Android XML vectors are converted to SVG first; WebP uses TwelveMonkeys.
4. **Hashing** — `SimilarityEngine` computes a dHash and pHash for each image. Android vector
   drawables also get a structural fingerprint.
5. **Comparison** — All pairs are compared. dHash acts as a fast pre-filter (80%); pHash
   decides the score. Pairs are retained down to a fixed 70% floor, so the slider can move
   without recomparing. Structural fingerprints match by equality (100% only).
6. **Clustering** — `ClusterBuilder` groups retained pairs into connected components, so each
   drawable belongs to exactly one group. Because similarity is not transitive, each group
   reports its weakest link as well as its strongest.
7. **Density deduplication** — Only the highest-density variant of each resource name takes
   part in comparison (xxxhdpi > xxhdpi > … > ldpi, with an unqualified variant preferred).
8. **Caching** — `DrawableHashCacheService` caches hashes by modification stamp to avoid
   re-hashing unchanged files.

## Building from source

```bash
./gradlew buildPlugin
```

The output JAR is located in `build/distributions/`.

To run the plugin in a sandboxed IDE instance:

```bash
./gradlew runIde
```

To run the tests:

```bash
./gradlew test
```

## Compatibility

- IntelliJ IDEA 2024.2+ / Android Studio Ladybug+
- Automatically compatible with future IDE versions
- JDK 21

## License

MIT
