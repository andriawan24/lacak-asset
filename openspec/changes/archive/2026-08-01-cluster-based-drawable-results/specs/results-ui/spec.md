## ADDED Requirements

### Requirement: Master-Detail Layout

The tool window SHALL present results as a horizontal master-detail split: a
cluster list on the leading side and a detail pane for the selected cluster on
the trailing side.

#### Scenario: Results displayed

- **WHEN** a scan completes with clusters
- **THEN** the cluster list occupies the leading side and the detail pane the trailing side

#### Scenario: No cluster selected

- **WHEN** the cluster list has no selection
- **THEN** the detail pane invites the user to select a cluster

### Requirement: Cluster List

Each cluster SHALL be listed as a single row showing the canonical member's
thumbnail, the canonical member's file name, the number of members, the
similarity range, and the estimated saving. A drawable SHALL NOT appear in more
than one row.

#### Scenario: Cluster row content

- **WHEN** a four-member cluster whose canonical member is `ic_home.png` is listed
- **THEN** the row shows that thumbnail, `ic_home.png`, a count of four, the similarity range, and the estimated saving

#### Scenario: Asset duplicated four times

- **WHEN** the same asset exists in four modules and all four are mutually similar
- **THEN** exactly one row is listed rather than six pair rows

### Requirement: Cluster List Sorting

The cluster list SHALL be sortable by estimated saving, member count, and
similarity, sorted numerically rather than lexically, and SHALL default to
descending estimated saving.

#### Scenario: Default order

- **WHEN** results are first displayed
- **THEN** the cluster recovering the most bytes is listed first

#### Scenario: Sort by member count

- **WHEN** the user sorts by member count
- **THEN** clusters order by their numeric member count

### Requirement: Cluster Detail Pane

The detail pane SHALL show one card per member of the selected cluster. Each card
SHALL show the member's thumbnail, file name, source description, file size, and
its per-member actions, and the canonical member's card SHALL carry a visible
canonical badge.

#### Scenario: Cluster selected

- **WHEN** the user selects a three-member cluster
- **THEN** three member cards are shown

#### Scenario: Canonical identified

- **WHEN** a cluster's members are displayed
- **THEN** exactly one card carries the canonical badge

#### Scenario: Member actions

- **WHEN** a non-canonical member card is displayed
- **THEN** it offers open, reveal, mark-as-canonical, and delete actions

#### Scenario: Canonical member actions

- **WHEN** the canonical member card is displayed
- **THEN** it offers open and reveal but no delete action

### Requirement: Mixed Format Indicator

A cluster whose members span formats SHALL be visibly marked as mixed format in
both the cluster list and the detail pane, explaining that raster and vector
drawables are not directly interchangeable.

#### Scenario: Mixed cluster listed

- **WHEN** a cluster contains a PNG and an Android vector
- **THEN** its row and detail pane carry a mixed-format marker

#### Scenario: Uniform cluster

- **WHEN** every member shares one format
- **THEN** no mixed-format marker is shown

### Requirement: Cluster Preselection

When results arrive with at least one cluster, the system SHALL select the first
listed cluster so its detail pane is immediately populated.

#### Scenario: Results arrive

- **WHEN** a scan completes with clusters
- **THEN** the first cluster is selected and its members are shown

### Requirement: Results Survive Reopening

The tool window SHALL render the most recent scan results when it is reopened,
without requiring a rescan.

#### Scenario: Tool window closed and reopened

- **WHEN** the user closes the tool window after a scan and reopens it
- **THEN** the previous results are displayed

#### Scenario: Reopened during a scan

- **WHEN** the tool window is reopened while a scan is running
- **THEN** the running state is displayed

## MODIFIED Requirements

### Requirement: Navigation to File

Double-clicking a member card SHALL open that drawable in the editor.
Double-clicking a cluster row SHALL open its canonical member. Neither SHALL act
when the project has been disposed.

#### Scenario: Double-click a member card

- **WHEN** the user double-clicks a member card
- **THEN** that member's file opens in the editor

#### Scenario: Double-click a cluster row

- **WHEN** the user double-clicks a cluster row
- **THEN** the cluster's canonical member opens in the editor

#### Scenario: Project disposed

- **WHEN** the project is disposed and a row is double-clicked
- **THEN** no file is opened and no error is raised

### Requirement: Scan State Feedback

The tool window SHALL indicate a busy state while a scan runs, and SHALL show a
status line summarising the outcome: the cluster count with the aggregate
recoverable size when clusters exist, and a "no cleanup needed" message when none
exist. Failures SHALL report their message and direct the user to the IDE log.

#### Scenario: Scan running

- **WHEN** a scan is in progress
- **THEN** the tool window shows a busy indicator and a scanning message

#### Scenario: Clusters found

- **WHEN** a scan completes with 12 clusters
- **THEN** the status line reports 12 groups and the aggregate recoverable size

#### Scenario: No clusters

- **WHEN** a scan completes with zero clusters
- **THEN** the cluster list's empty text states that none were found at the current threshold
- **AND** the status line states that no cleanup is needed

#### Scenario: Failed scan

- **WHEN** a scan fails
- **THEN** the failure message is displayed
- **AND** the status line directs the user to the IDE log

### Requirement: Initial Empty State

Before any scan has run the cluster list SHALL prompt the user to run a scan.

#### Scenario: Tool window opened for the first time

- **WHEN** the tool window is opened and no scan has run
- **THEN** the cluster list's empty text invites the user to run "Scan All Drawables"

### Requirement: Layout Persistence

The system SHALL persist the position of the master-detail splitter across
sessions.

#### Scenario: Splitter moved and IDE restarted

- **WHEN** the user drags the splitter and later reopens the tool window
- **THEN** the splitter position is retained

### Requirement: Disposal

Closing the tool window content SHALL stop the panel observing scan state and
SHALL cancel any analysis the panel started, so that nothing continues to
reference the disposed panel.

#### Scenario: Tool window content closed

- **WHEN** the tool window content is disposed
- **THEN** the panel no longer observes scan state

#### Scenario: Disposed mid-analysis

- **WHEN** the panel is disposed while a candidate analysis it started is running
- **THEN** that analysis is cancelled

## REMOVED Requirements

### Requirement: Results Table

**Reason**: Pair rows are replaced by cluster rows. A pair table forces the user
to mentally group rows that describe the same asset, and shows the same file in
many rows.

**Migration**: Equivalent information is provided by the Cluster List and Cluster
Detail Pane requirements. The pair remains an internal artefact of comparison and
is no longer presented.

### Requirement: Size Saving Estimate

**Reason**: A saving defined as the smaller of two files is meaningless once a
group can hold more than two members.

**Migration**: Replaced by the Estimated Cluster Saving requirement in
`duplicate-clustering`, which totals the non-canonical members.

### Requirement: Column Sorting

**Reason**: The six-column pair table no longer exists.

**Migration**: Replaced by the Cluster List Sorting requirement.

### Requirement: Side-by-Side Preview

**Reason**: A two-panel preview cannot represent a cluster of more than two
members.

**Migration**: Replaced by the Cluster Detail Pane requirement, which shows one
preview per member.

### Requirement: First Row Preselection

**Reason**: Restated in cluster terms.

**Migration**: Replaced by the Cluster Preselection requirement.
