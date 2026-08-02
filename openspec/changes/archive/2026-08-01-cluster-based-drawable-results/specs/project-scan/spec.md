## ADDED Requirements

### Requirement: Unified Scan Pipeline

Full scans, targeted scans, and external candidate checks SHALL be served by one
pipeline that accepts an optional target. The three modes SHALL differ only in
what the pipeline is given and which part of its output is presented, not in how
discovery, normalization, hashing, or comparison are performed.

#### Scenario: Full scan

- **WHEN** the pipeline runs with no target
- **THEN** all clusters are presented

#### Scenario: Targeted scan

- **WHEN** the pipeline runs with a project drawable as target
- **THEN** the same discovery, hashing, and comparison run
- **AND** only the cluster containing the target is presented

#### Scenario: External candidate

- **WHEN** the pipeline runs with an external file as target
- **THEN** the same discovery, hashing, and comparison run
- **AND** the candidate's matches are presented

### Requirement: Decoding Outside the Read Lock

The system SHALL hold a read action only for virtual file system traversal,
metadata access, and reading file bytes. Image decoding, SVG rendering, vector
conversion, and hashing SHALL run outside the read action.

#### Scenario: Rendering a large SVG

- **WHEN** an SVG is rendered during a scan
- **THEN** no read action is held for the duration of the rendering

#### Scenario: Write action during a scan

- **WHEN** the user edits a file while a scan is decoding images
- **THEN** the edit is not blocked by the scan

### Requirement: Parallel Hashing

The system SHALL normalize and hash drawables concurrently across worker threads.
Renderers that are not thread-safe SHALL NOT be shared between workers.

#### Scenario: Many drawables

- **WHEN** a scan hashes several hundred drawables
- **THEN** the work is distributed across multiple workers

#### Scenario: Concurrent SVG rendering

- **WHEN** two workers render SVG content at the same time
- **THEN** each uses its own renderer instance
- **AND** the resulting hashes are identical to those produced sequentially

### Requirement: Per-File Progress

The system SHALL report progress as files are processed and SHALL check for
cancellation between files, so a scan stops promptly when cancelled.

#### Scenario: Progress advances

- **WHEN** a scan processes drawables
- **THEN** the background progress indicator advances as files complete

#### Scenario: Cancellation observed

- **WHEN** the background progress indicator is cancelled mid-scan
- **THEN** the scan stops without processing the remaining files

## MODIFIED Requirements

### Requirement: Background Execution

The system SHALL run the scan off the event dispatch thread under a background
progress indicator, SHALL perform virtual file system access inside a read
action, and SHALL perform image decoding and hashing outside the read action.

#### Scenario: Scan of a large project

- **WHEN** a scan runs against a project with many drawables
- **THEN** the IDE remains responsive
- **AND** a background progress indicator is shown

### Requirement: Scan Pipeline

A full scan SHALL discover drawable files subject to the configured exclusions,
retain only files whose format is enabled in configuration, normalize and hash
them via the cache, compare all pairs down to the retention floor, and group the
retained pairs into clusters at the displayed threshold.

#### Scenario: Disabled format

- **WHEN** SVG is disabled in configuration
- **THEN** no SVG file participates in the scan

#### Scenario: Clusters produced

- **WHEN** a scan completes
- **THEN** both the retained pairs and the clusters formed at the displayed threshold are available

### Requirement: Scan Lifecycle Notifications

The system SHALL publish scan state as observable state with exactly one current
value, covering idle, running, ready with results, and failed. Observers
attaching after a state change SHALL immediately receive the current value, and
more than one observer SHALL be supported.

#### Scenario: Successful completion

- **WHEN** a scan finishes normally
- **THEN** the state becomes ready and carries the retained pairs and clusters

#### Scenario: Late observer

- **WHEN** an observer attaches after a scan has completed
- **THEN** it immediately receives the ready state with the existing results

#### Scenario: Failure

- **WHEN** a scan throws an unexpected error
- **THEN** the error is logged
- **AND** the state becomes failed carrying the error message

### Requirement: Completion Notification

On completion the system SHALL post an IDE notification: a warning stating the
number of duplicate groups found when clusters exist, and an informational
message stating that none were found otherwise.

#### Scenario: Duplicates found

- **WHEN** a scan finds 12 clusters
- **THEN** a warning notification reports 12 duplicate drawable groups

#### Scenario: No duplicates found

- **WHEN** a scan finds no clusters
- **THEN** an informational notification reports that none were found
