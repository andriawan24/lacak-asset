## ADDED Requirements

### Requirement: Safe Delete Delegation

The system SHALL delete a redundant drawable by invoking the IDE's Safe Delete
refactoring, and SHALL NOT implement its own usage search, reference rewriting,
or file removal. Conflict reporting, usage preview, and undo are therefore
provided by the platform.

#### Scenario: Referenced file

- **WHEN** the user deletes a member that is referenced from source or layout files
- **THEN** the platform's Safe Delete conflict dialog lists those usages
- **AND** the file is removed only if the user proceeds

#### Scenario: Unreferenced file

- **WHEN** the user deletes a member with no usages
- **THEN** Safe Delete removes the file

#### Scenario: Undo

- **WHEN** the user undoes after a deletion
- **THEN** the platform restores the file

### Requirement: Canonical Member Protected

The system SHALL NOT offer deletion of a cluster's canonical member. Deletion
SHALL be offered only for non-canonical members.

#### Scenario: Canonical member selected

- **WHEN** the user views the canonical member of a cluster
- **THEN** no delete action is available for it

#### Scenario: Canonical reassigned

- **WHEN** the user marks a different member as canonical
- **THEN** the delete action becomes available on the previously canonical member
- **AND** becomes unavailable on the newly canonical member

### Requirement: Mixed-Format Confirmation

Deleting a member of a mixed-format cluster SHALL require an additional
confirmation that names the format being removed and the format being kept,
before Safe Delete is invoked. Deleting within a single-format cluster SHALL NOT
raise this confirmation.

#### Scenario: Deleting a raster in favour of a vector

- **WHEN** the user deletes `ic_close.png` from a cluster whose canonical member is `ic_close.xml`
- **THEN** a confirmation states that a PNG is being removed in favour of an Android vector
- **AND** Safe Delete runs only after the user confirms

#### Scenario: Confirmation declined

- **WHEN** the user declines the mixed-format confirmation
- **THEN** no deletion occurs

#### Scenario: Single-format cluster

- **WHEN** the user deletes a PNG from a cluster containing only PNGs
- **THEN** no format confirmation is raised

### Requirement: Result Refresh After Deletion

When a deletion completes, the system SHALL remove the deleted drawable from its
cluster, and SHALL drop the cluster from the results when fewer than two members
remain.

#### Scenario: Cluster retains members

- **WHEN** one member is deleted from a four-member cluster
- **THEN** the cluster remains with three members

#### Scenario: Cluster exhausted

- **WHEN** the only non-canonical member of a two-member cluster is deleted
- **THEN** the cluster is removed from the results

#### Scenario: Deletion cancelled

- **WHEN** the user cancels the platform's Safe Delete dialog
- **THEN** the cluster is unchanged

### Requirement: Deletion Unavailable For External Candidates

The system SHALL NOT offer deletion for a candidate that is not part of the
project, since the plugin never modifies files outside the project.

#### Scenario: External candidate displayed

- **WHEN** an external candidate is shown alongside its project matches
- **THEN** no delete action is offered for the external file itself
- **AND** delete remains available for its non-canonical project matches
