# results-ui Specification

## Purpose

Present scan results so the user can judge each candidate pair visually and act on
it, without leaving the IDE.

Implemented by `toolwindow/DuplicateDrawableToolWindowFactory.kt`,
`toolwindow/DuplicateDrawablePanel.kt`, `toolwindow/SimilarityTableModel.kt`, and
`toolwindow/ImagePreviewPanel.kt`.

## Requirements

### Requirement: Tool Window Registration

The system SHALL register a tool window titled "Lacak Asset" anchored to the
bottom of the IDE, available while the IDE is indexing.

#### Scenario: Tool window present during indexing

- **WHEN** the IDE is in dumb mode
- **THEN** the tool window can still be opened

### Requirement: Results Table

The system SHALL display each match as a row with the two drawables' file names,
the similarity percentage, each drawable's source description, and an estimated
size saving.

#### Scenario: Match row content

- **WHEN** a match between `ic_home.png` in `:app/main` and `home_icon.svg` in `:core:ui/main` is displayed
- **THEN** the row shows both file names, the percentage, both source descriptions, and a size figure

### Requirement: Source Description

The source description for a drawable SHALL combine its module path, source set,
and resource origin label.

#### Scenario: Compose resource

- **WHEN** a drawable comes from `composeResources` in module `:shared`, source set `commonMain`
- **THEN** its source reads as `:shared (commonMain, Compose Resources)`

### Requirement: Size Saving Estimate

The estimated saving for a match SHALL be the smaller of the two files' byte
lengths, rendered in bytes, KB, or MB as appropriate.

#### Scenario: Pair of differing sizes

- **WHEN** a match pairs a 40 KB file with a 12 KB file
- **THEN** the estimated saving reads as the 12 KB figure

### Requirement: Column Sorting

The table SHALL be sortable by any column, with the similarity and saving columns
sorted numerically rather than lexically.

#### Scenario: Sort by similarity

- **WHEN** the user clicks the similarity column header
- **THEN** rows order by numeric percentage

### Requirement: Side-by-Side Preview

Selecting a row SHALL display the cached thumbnails of both drawables side by
side beneath the table, each labelled with its file name. Deselecting SHALL clear
both previews.

#### Scenario: Row selected

- **WHEN** the user selects a match row
- **THEN** both drawables' thumbnails are shown side by side

#### Scenario: Selection cleared

- **WHEN** the table selection is cleared
- **THEN** both preview panels are emptied

### Requirement: Navigation to File

Double-clicking a row SHALL open one of the paired drawables in the editor,
choosing the second drawable when the double-click lands on a column describing
the second drawable and the first otherwise.

#### Scenario: Double-click on the second file's column

- **WHEN** the user double-clicks the "Image B" or "Source B" cell of a row
- **THEN** the second drawable opens in the editor

#### Scenario: Double-click elsewhere in the row

- **WHEN** the user double-clicks any other cell of a row
- **THEN** the first drawable opens in the editor

### Requirement: Scan State Feedback

The table SHALL indicate a busy state while a scan runs, and SHALL show a status
line summarising the outcome: the match count with the aggregate size figure when
matches exist, a "no cleanup needed" message when none exist, and a partial
results message when the scan was cancelled.

#### Scenario: Scan running

- **WHEN** a scan is in progress
- **THEN** the table shows a busy indicator and a scanning message

#### Scenario: Matches found

- **WHEN** a scan completes with 12 matches
- **THEN** the status line reports 12 similar pairs and the aggregate reviewable size

#### Scenario: No matches

- **WHEN** a scan completes with zero matches
- **THEN** the table's empty text states that none were found at the current threshold
- **AND** the status line states that no cleanup is needed

#### Scenario: Cancelled scan

- **WHEN** a scan is cancelled
- **THEN** the partial results are displayed
- **AND** the status line states that the scan was cancelled

### Requirement: Initial Empty State

Before any scan has run the table SHALL prompt the user to run a scan.

#### Scenario: Tool window opened for the first time

- **WHEN** the tool window is opened and no scan has run
- **THEN** the table's empty text invites the user to run "Scan All Drawables"

### Requirement: First Row Preselection

When a scan completes with at least one match, the system SHALL select the first
row so a preview is immediately visible.

#### Scenario: Results arrive

- **WHEN** a scan completes with matches
- **THEN** the highest-scoring row is selected and previewed

### Requirement: Layout Persistence

The system SHALL persist the positions of the table/preview splitter and the
left/right preview splitter across sessions.

#### Scenario: Splitter moved and IDE restarted

- **WHEN** the user drags a splitter and later reopens the tool window
- **THEN** the splitter position is retained

### Requirement: Disposal

Closing the tool window content SHALL detach the panel's scan observers so that no
callbacks reference the disposed panel.

#### Scenario: Tool window content closed

- **WHEN** the tool window content is disposed
- **THEN** the scan service holds no observers from that panel
