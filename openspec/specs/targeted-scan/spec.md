# targeted-scan Specification

## Purpose

Let the user ask "what else in this project looks like this file?" directly from
the Project view, without running and then visually filtering a full scan.

Implemented by `action/FindSimilarDrawableAction.kt` and
`DrawableScanService.scanSingleFile`.

## Requirements

### Requirement: Context Menu Availability

The system SHALL show a "Find Similar Drawable" action in the Project view
context menu only when the selected item is a non-directory file whose extension
is a supported drawable extension and which resides in a recognized drawable
directory.

#### Scenario: Drawable selected

- **WHEN** the user right-clicks `res/drawable/ic_home.png`
- **THEN** the action is visible and enabled

#### Scenario: Layout XML selected

- **WHEN** the user right-clicks `res/layout/activity_main.xml`
- **THEN** the action is hidden

#### Scenario: Directory selected

- **WHEN** the user right-clicks a directory
- **THEN** the action is hidden

#### Scenario: Source file selected

- **WHEN** the user right-clicks a Kotlin source file
- **THEN** the action is hidden

### Requirement: Availability During Indexing

The action SHALL remain available while the IDE is indexing, since its visibility
decision depends only on file paths and extensions.

#### Scenario: Right-click during indexing

- **WHEN** the IDE is in dumb mode and the user right-clicks a drawable
- **THEN** the action is still shown

### Requirement: Disabled During an Active Scan

The system SHALL disable the action while a scan is running, and SHALL ignore a
targeted scan request received while a scan is already active.

#### Scenario: Targeted scan requested mid-scan

- **WHEN** a full scan is running and the user invokes the targeted action
- **THEN** no targeted scan starts

### Requirement: One-Against-All Comparison

A targeted scan SHALL discover and hash the project's drawables under the same
exclusions, format filters, and threshold as a full scan, and SHALL then compare
only the selected file against the deduplicated candidates.

#### Scenario: Targeted comparison scope

- **WHEN** a targeted scan runs on `ic_home.png` in a project with 500 drawables
- **THEN** comparisons are performed between `ic_home.png` and each candidate
- **AND** no candidate-to-candidate pairs are compared

### Requirement: Target Not Eligible

The system SHALL return an empty result when the selected file is absent from the
discovered set, for example because its format is disabled in configuration or it
falls under an exclusion.

#### Scenario: Selected file's format disabled

- **WHEN** the user targets an SVG while SVG is disabled in configuration
- **THEN** the scan completes with an empty result

### Requirement: Result Presentation

On completion the system SHALL reveal the tool window containing the results, and
SHALL expand it to at least a quarter of the IDE frame height if it is currently
shorter.

#### Scenario: Tool window closed when results arrive

- **WHEN** a targeted scan completes and the tool window is hidden
- **THEN** the tool window is shown with the results
