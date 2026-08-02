## ADDED Requirements

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

## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Successive Checks

**Reason**: Written in terms of reusing and resetting a dialog that no longer
exists.

**Migration**: Replaced by the Candidate Replaced By Successive Check
requirement, which preserves the cancel-and-reuse behaviour against the pinned
cluster instead of a dialog.

### Requirement: Main Panel Isolation

**Reason**: The candidate check now renders in the main panel by design, so
isolating it from that panel is contradictory.

**Migration**: The candidate occupies a pinned cluster at the top of the list;
existing scan results remain in the list below it and are neither discarded nor
reordered. Clearing the candidate restores the unpinned list unchanged.
