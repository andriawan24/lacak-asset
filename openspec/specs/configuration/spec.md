# configuration Specification

## Purpose

Let each project tune what counts as a match and what gets scanned, without
affecting other projects.

Implemented by `settings/DrawableAnalyzerSettings.kt` and
`settings/DrawableAnalyzerConfigurable.kt`.

## Requirements

### Requirement: Settings Location

The system SHALL expose a settings page named "Lacak Asset" under the Tools
section of the IDE settings.

#### Scenario: User opens settings

- **WHEN** the user navigates to Settings, then Tools
- **THEN** a "Lacak Asset" page is available

### Requirement: Per-Project Workspace Storage

Settings SHALL be stored per project in the workspace file, so they are not
shared through version control and do not leak between projects.

#### Scenario: Two projects with different thresholds

- **WHEN** project A is set to 95 and project B to 80
- **THEN** each project scans at its own threshold

### Requirement: Similarity Threshold

The system SHALL expose a similarity threshold as an integer percentage between
50 and 100 inclusive, adjustable in steps of 5, defaulting to 90, and SHALL apply
it as the minimum perceptual-hash similarity for reporting a pair.

#### Scenario: Default threshold

- **WHEN** a project has never configured the threshold
- **THEN** scans run at 90%

#### Scenario: Threshold lowered

- **WHEN** the user sets the threshold to 75 and runs a scan
- **THEN** pairs at 75% and above are reported

### Requirement: Per-Format Toggles

The system SHALL expose an independent enable toggle for each of PNG, JPEG, WebP,
SVG, and Android vector XML, each defaulting to enabled, and SHALL exclude
disabled formats from every scan.

#### Scenario: WebP disabled

- **WHEN** the WebP toggle is cleared and a scan runs
- **THEN** no WebP file is hashed or reported

#### Scenario: All formats enabled by default

- **WHEN** a project has never configured formats
- **THEN** all five formats participate in scans

### Requirement: Excluded Directories

The system SHALL accept a comma-separated list of directory names to skip,
trimming whitespace around each entry and ignoring empty entries, defaulting to
an empty list.

#### Scenario: Multiple exclusions with spacing

- **WHEN** the field contains `generated, sampledata ,  `
- **THEN** the directories `generated` and `sampledata` are skipped
- **AND** the trailing empty entry is ignored

#### Scenario: No exclusions configured

- **WHEN** the field is empty
- **THEN** only the built-in skip list applies

### Requirement: Modification Tracking

The settings page SHALL report itself as modified when any field differs from the
stored state, and SHALL restore the stored values on reset.

#### Scenario: Field edited then reset

- **WHEN** the user edits the excluded directories field and then resets
- **THEN** the field returns to the stored value
- **AND** the page reports itself as unmodified
