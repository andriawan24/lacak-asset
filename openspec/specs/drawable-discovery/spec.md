# drawable-discovery Specification

## Purpose

Locate every candidate drawable resource in the open project and describe it with
enough metadata (resource name, format, density, module, source set, origin) for
downstream normalization and comparison.

Implemented by `scanner/DrawableFileScanner.kt` and `model/DrawableFile.kt`.

## Requirements

### Requirement: Drawable Directory Recognition

The system SHALL treat a file as a drawable resource only when its parent
directory is `drawable` or begins with `drawable-`, AND its grandparent
directory is `res` or `composeResources`.

#### Scenario: Traditional Android resource directory

- **WHEN** a file lives at `app/src/main/res/drawable-xhdpi/ic_home.png`
- **THEN** the file is recognized as a drawable resource

#### Scenario: Compose Multiplatform resource directory

- **WHEN** a file lives at `shared/src/commonMain/composeResources/drawable/ic_home.png`
- **THEN** the file is recognized as a drawable resource

#### Scenario: XML outside a drawable directory

- **WHEN** a file lives at `app/src/main/res/layout/activity_main.xml`
- **THEN** the file is NOT recognized as a drawable resource

#### Scenario: Drawable directory without a recognized grandparent

- **WHEN** a file lives at `assets/drawable/ic_home.png`
- **THEN** the file is NOT recognized as a drawable resource

### Requirement: Supported Extensions

The system SHALL collect files whose extension is one of `png`, `jpg`, `jpeg`,
`webp`, `svg`, or `xml`, matched case-insensitively.

#### Scenario: Uppercase extension

- **WHEN** a drawable directory contains `IC_HOME.PNG`
- **THEN** the file is collected and classified as format `PNG`

#### Scenario: Unsupported extension

- **WHEN** a drawable directory contains `notes.txt`
- **THEN** the file is not collected

### Requirement: Nine-Patch Exclusion

The system SHALL skip nine-patch images, identified by a filename ending in
`.9.png`.

#### Scenario: Nine-patch present

- **WHEN** a drawable directory contains `bg_button.9.png`
- **THEN** the file is not collected

### Requirement: Directory Exclusion

The system SHALL skip the built-in directories `build`, `.gradle`, `.idea`,
`.git`, and `node_modules`, SHALL skip any directory whose name begins with `.`,
and SHALL additionally skip any directory name supplied by configuration.

#### Scenario: Build output ignored

- **WHEN** generated drawables exist under `app/build/generated/res/drawable/`
- **THEN** those files are not collected

#### Scenario: User-configured exclusion

- **WHEN** the configured excluded directories contain `sampledata`
- **AND** drawables exist under a `sampledata` directory
- **THEN** those files are not collected

### Requirement: Resource Metadata Extraction

For each collected file the system SHALL derive: the resource name (filename
without extension), the density qualifier (the portion of the parent directory
name after `drawable-`, or empty string for plain `drawable`), the module path
(the path segment before `/src/`, rendered as a Gradle-style `:module:path`),
the source set (the path segment immediately after `/src/`), and the resource
origin (`ANDROID_RES` or `COMPOSE_RESOURCES`).

#### Scenario: Density qualifier from a qualified directory

- **WHEN** the file is `core/ui/src/main/res/drawable-xxhdpi/ic_home.png`
- **THEN** the resource name is `ic_home`
- **AND** the density qualifier is `xxhdpi`
- **AND** the module path is `:core:ui`
- **AND** the source set is `main`
- **AND** the resource origin is `ANDROID_RES`

#### Scenario: Unqualified drawable directory

- **WHEN** the file is `app/src/debug/res/drawable/ic_home.png`
- **THEN** the density qualifier is the empty string
- **AND** the source set is `debug`

#### Scenario: File outside any source set

- **WHEN** the file path contains no `/src/` segment
- **THEN** the module path is `:`
- **AND** the source set defaults to `main`

### Requirement: Empty Project Handling

The system SHALL return an empty result rather than failing when the project has
no base directory or contains no drawable resources.

#### Scenario: Project with no drawables

- **WHEN** a scan runs against a project containing no drawable directories
- **THEN** discovery returns an empty list
- **AND** no error is raised
