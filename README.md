# Lacak Asset

An IntelliJ IDEA plugin that detects similar and duplicate drawable resources in Android projects.

<img width="1552" height="899" alt="Screenshot 2026-03-13 at 15 01 20" src="https://github.com/user-attachments/assets/0d027ef7-5aef-419e-bf7e-e8625253297f" />

## Features

- Scans drawable resources across PNG, JPG, WebP, SVG, and Android XML vector formats
- Uses perceptual hashing (dHash + pHash) to identify visually similar images
- Compares Android vector drawables by structural fingerprint (code equality)
- Deduplicates density variants (mdpi, hdpi, xhdpi, etc.) before comparison
- Configurable similarity threshold (default: 90%)
- Real-time detection via file change listener
- Tool window with side-by-side image preview and similarity score table
- Right-click context menu to find drawables similar to a selected file
- Supports KMM and Compose Multiplatform drawable scanning

## Installation

1. Open IntelliJ IDEA or Android Studio
2. Go to **Settings → Plugins → Marketplace**
3. Search for **Lacak Asset** and install

Alternatively, build from source and install the plugin JAR manually via **Settings → Plugins → Install Plugin from Disk**.

## Usage

### Scan for duplicates

Go to **Tools → Scan Similar Drawables** or open the **Lacak Asset** tool window at the bottom of the IDE.

### Find similar drawable

Right-click any drawable file in the Project view and select **Find Similar Drawable** to find all drawables similar to the selected file.

### Tool window

The tool window displays a table of similar drawable pairs with their similarity percentage. Double-clicking a row opens the corresponding file in the editor.

### Configuration

Go to **Settings → Tools → Lacak Asset** to configure:

| Setting | Default | Description |
|---|---|---|
| Similarity threshold | 90% | Minimum similarity score to report a pair |
| Excluded directories | _(none)_ | Comma-separated directory names to skip |
| Include XML drawables | Enabled | Whether to include Android vector XML files |

## How it works

1. **Scanning** — `DrawableFileScanner` walks the project's `res/drawable*` directories and collects drawable files.
2. **Normalization** — `DrawableNormalizer` renders each file to a `BufferedImage`. SVG files use Apache Batik; Android XML vectors are converted to SVG first; WebP files use TwelveMonkeys ImageIO.
3. **Hashing** — `SimilarityEngine` computes a dHash and pHash for each image. Android vector drawables use a structural fingerprint instead.
4. **Comparison** — All pairs are compared. dHash acts as a fast pre-filter (threshold: 80%); pHash determines the final similarity score. Structural fingerprints are compared by equality (100% match only).
5. **Density deduplication** — Before comparison, only the highest-density variant of each resource name is kept (xxxhdpi > xxhdpi > … > ldpi).
6. **Caching** — `DrawableHashCacheService` caches hashes by modification stamp to avoid re-hashing unchanged files.

## Building from source

```bash
./gradlew buildPlugin
```

The output JAR is located in `build/distributions/`.

To run the plugin in a sandboxed IDE instance:

```bash
./gradlew runIde
```

## Compatibility

- IntelliJ IDEA 2024.2+ / Android Studio Ladybug+
- Automatically compatible with future IDE versions
- JDK 21

## License

MIT
