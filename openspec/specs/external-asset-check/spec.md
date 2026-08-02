# external-asset-check Specification

## Purpose

Let the user check whether an image that is not yet part of the project already
exists as a project drawable, before they commit to adding it. The candidate file
is never copied into the project and nothing is written to disk.

Implemented by `DrawableScanService.scanDroppedFile`,
`toolwindow/DropCheckDialog.kt`, `toolwindow/DropCheckTableModel.kt`,
`DuplicateDrawablePanel.setupDropTarget`, `action/CompareAssetAction.kt`, and
`model/DropScanResult.kt`.
## Requirements
### Requirement: Drop Target

The system SHALL accept a file dragged from the host file manager onto the Lacak
Asset tool window panel, and SHALL NOT register drop targets on the editor,
project tree, or any other component.

#### Scenario: Image dragged onto the panel

- **WHEN** the user drags a PNG from the OS file manager over the tool window panel
- **THEN** the drop is accepted

#### Scenario: Image dragged onto the editor

- **WHEN** the user drags a PNG over the editor
- **THEN** the plugin does not handle the drag

### Requirement: Drop Eligibility Feedback

The system SHALL indicate during hover whether the drag will be accepted,
highlighting the panel with a rectangle when it will and marking the drop
impossible when it will not.

#### Scenario: Supported file hovered

- **WHEN** a supported image is dragged over the panel
- **THEN** the panel is highlighted and the drop is marked possible

#### Scenario: Unsupported file hovered

- **WHEN** a file with an unsupported extension is dragged over the panel
- **THEN** the drop is marked impossible

#### Scenario: Directory hovered

- **WHEN** a directory is dragged over the panel
- **THEN** the drop is marked impossible

#### Scenario: Drag leaves the panel

- **WHEN** the drag exits the panel without dropping
- **THEN** the highlight is cleared

### Requirement: Single File Per Drop

The system SHALL accept exactly one file per drop event and SHALL reject a drop
carrying more than one file.

#### Scenario: Multiple files dropped

- **WHEN** the user drops three images at once
- **THEN** the drop is rejected

### Requirement: Cross-Platform Transfer Decoding

The system SHALL read the dropped payload as a Java file list, and SHALL fall
back to interpreting a plain string payload as a single file path for platforms
that deliver drags that way.

#### Scenario: File-list payload

- **WHEN** the drag carries a Java file list flavour
- **THEN** the file is read from that list

#### Scenario: String payload fallback

- **WHEN** the drag carries only a string flavour containing a path
- **THEN** that path is treated as the dropped file

### Requirement: Rejection While Busy

The system SHALL reject a drop while a project scan or a previous drop analysis
is running.

#### Scenario: Drop during a full scan

- **WHEN** a full scan is running and the user drags a file over the panel
- **THEN** the drop is marked impossible

### Requirement: File Chooser Alternative

The system SHALL offer a "Check Similar Drawable" action in the Tools menu that
opens a single-file chooser and runs the same analysis on the chosen file, and
SHALL disable that action while a scan or drop analysis is running.

#### Scenario: User picks a file from the chooser

- **WHEN** the user invokes "Check Similar Drawable" and selects an image
- **THEN** the same analysis and results dialog are used as for a drop

#### Scenario: Action during an active scan

- **WHEN** a scan is running
- **THEN** the action is disabled

### Requirement: External File Treatment

The system SHALL analyse the candidate as a synthetic drawable with an empty
density qualifier and with module and source-set labels marking it as external,
and SHALL NOT add, copy, or write the file anywhere in the project.

#### Scenario: Candidate analysed

- **WHEN** an external image is analysed
- **THEN** no file is created or modified in the project

### Requirement: Candidate Normalization

The system SHALL normalize and hash the candidate using the same pipeline as
project drawables, and SHALL reject an XML candidate whose root element is not
`vector`.

#### Scenario: Vector XML candidate

- **WHEN** the candidate is an Android vector drawable
- **THEN** it is converted, rendered, hashed, and given a structural fingerprint

#### Scenario: Layout XML candidate

- **WHEN** the candidate is a layout XML file
- **THEN** the analysis reports that the file could not be processed

### Requirement: Candidate Comparison

The system SHALL compare the candidate against the project's cached drawables
using the configured similarity threshold, and SHALL perform a full project scan
first when the cache is empty.

#### Scenario: Warm cache

- **WHEN** a project scan has already populated the cache
- **THEN** the candidate is compared against the cached drawables without rescanning

#### Scenario: Cold cache

- **WHEN** no scan has run and the cache is empty
- **THEN** the project's drawables are discovered and hashed before comparison

#### Scenario: Vector candidate matching a project vector

- **WHEN** the candidate's structural fingerprint equals a project vector's fingerprint
- **THEN** the match is reported at 100% regardless of the configured threshold

### Requirement: Background Analysis

The system SHALL run the analysis off the event dispatch thread under a
background progress indicator, and SHALL deliver its outcome on the event
dispatch thread.

#### Scenario: Analysis of a large project

- **WHEN** the candidate is compared against a project with many drawables
- **THEN** the IDE remains responsive

### Requirement: Results Dialog

The system SHALL present candidate analysis inside the Lacak Asset tool window,
revealing the tool window if it is hidden, and SHALL present exactly one of four
states: analysis in progress, matches found, no matches, or error. No separate
dialog SHALL be opened.

#### Scenario: Tool window revealed on drop

- **WHEN** a file is dropped while the tool window is hidden
- **THEN** the tool window is revealed showing a progress indicator for the candidate

#### Scenario: Matches found

- **WHEN** the analysis returns matches
- **THEN** the pinned cluster shows the candidate's thumbnail, its match count, and its members

#### Scenario: No matches

- **WHEN** the analysis returns no matches
- **THEN** the tool window states that nothing similar was found at the current threshold

#### Scenario: IDE remains usable

- **WHEN** a candidate analysis is running
- **THEN** the user can continue interacting with the IDE

### Requirement: Match Table

Each match SHALL be presented as a member card showing the matched project
drawable's cached thumbnail, its similarity to the candidate, and a source
description combining its file name and module path.

#### Scenario: Match card content

- **WHEN** a match against `ic_home.png` in `:app` is presented
- **THEN** the card shows its thumbnail, its percentage, and `ic_home.png (:app)`

#### Scenario: Matched drawable absent from the cache

- **WHEN** a matched drawable has no cached thumbnail
- **THEN** the card renders without a thumbnail rather than failing

### Requirement: Navigation to Match

Double-clicking a match's member card SHALL open the matched project drawable in
the editor, and SHALL do nothing if the project has been disposed.

#### Scenario: User double-clicks a match

- **WHEN** the user double-clicks a match's member card
- **THEN** the matched project drawable opens in the editor

#### Scenario: Project disposed

- **WHEN** the project is disposed and a card is double-clicked
- **THEN** no file is opened and no error is raised

### Requirement: Error Reporting

The system SHALL report failures inside the tool window rather than crashing,
covering an inaccessible file, an unsupported format, a file that could not be
processed, and any unexpected error.

#### Scenario: Unsupported format chosen

- **WHEN** the candidate's extension is not a supported drawable format
- **THEN** the tool window shows an unsupported-format message

#### Scenario: File removed before analysis

- **WHEN** the candidate no longer exists when analysis begins
- **THEN** the tool window reports that the file is no longer accessible

#### Scenario: Unexpected failure

- **WHEN** analysis throws an unexpected error
- **THEN** the error is logged and its message is shown in the tool window

### Requirement: Disposal

Disposing the tool window panel SHALL cancel any in-flight candidate analysis.

#### Scenario: Tool window closed mid-analysis

- **WHEN** the panel is disposed while an analysis is running
- **THEN** the analysis is cancelled

### Requirement: External Candidate Pseudo-Cluster

The system SHALL present an external candidate and its project matches as a
cluster pinned to the top of the cluster list, marked as external and not part of
the project. The candidate SHALL be the cluster's canonical member and SHALL NOT
be eligible for deletion or for canonical override.

#### Scenario: Candidate with matches

- **WHEN** a dropped file matches two project drawables
- **THEN** a pinned cluster containing the candidate and both matches appears at the top of the list

#### Scenario: External marking

- **WHEN** the pinned cluster is displayed
- **THEN** it is marked as external and not part of the project

#### Scenario: Candidate is not deletable

- **WHEN** the pinned cluster's members are displayed
- **THEN** the candidate offers no delete action
- **AND** the matched project drawables do offer one

#### Scenario: Candidate is always canonical

- **WHEN** the user attempts to designate a matched project drawable as canonical
- **THEN** the candidate remains canonical

### Requirement: Candidate Replaced By Successive Check

Checking another candidate SHALL cancel any in-flight analysis and replace the
pinned cluster, so at most one external candidate is present at a time.

#### Scenario: Second file dropped

- **WHEN** the user drops a second file while a candidate is displayed
- **THEN** the previous analysis is cancelled
- **AND** the pinned cluster is replaced by the new candidate

