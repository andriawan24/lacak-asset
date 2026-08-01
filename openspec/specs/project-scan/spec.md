# project-scan Specification

## Purpose

Run a full-project sweep for similar drawables in the background, report progress
and completion to the user, and expose the results to the tool window.

Implemented by `service/DrawableScanService.kt` and `action/ScanDrawablesAction.kt`.

## Requirements

### Requirement: Scan Invocation

The system SHALL offer a "Scan All Drawables" action from the Tools menu and from
the tool window toolbar that starts a full-project scan.

#### Scenario: User triggers a scan

- **WHEN** the user invokes "Scan All Drawables"
- **THEN** a full-project scan begins

### Requirement: Single Concurrent Scan

The system SHALL disable the scan action while a scan is running, and SHALL
ignore a scan request received while a scan is already active.

#### Scenario: Scan requested during an active scan

- **WHEN** a scan is in progress and the user invokes the scan action again
- **THEN** no second scan starts

### Requirement: Background Execution

The system SHALL run the scan off the event dispatch thread under a background
progress indicator, and SHALL perform virtual file system access inside a read
action.

#### Scenario: Scan of a large project

- **WHEN** a scan runs against a project with many drawables
- **THEN** the IDE remains responsive
- **AND** a background progress indicator is shown

### Requirement: Scan Pipeline

A full scan SHALL discover drawable files subject to the configured exclusions,
retain only files whose format is enabled in configuration, normalize and hash
them via the cache, and compare all pairs at the configured threshold.

#### Scenario: Disabled format

- **WHEN** SVG is disabled in configuration
- **THEN** no SVG file participates in the scan

#### Scenario: Threshold applied

- **WHEN** the configured threshold is 95
- **THEN** pairs are compared at a normalized threshold of 0.95

### Requirement: Scan Lifecycle Notifications

The system SHALL notify observers when a scan starts, completes with results, is
cancelled with partial results, or fails with an error.

#### Scenario: Successful completion

- **WHEN** a scan finishes normally
- **THEN** observers receive the full result list

#### Scenario: Cancellation

- **WHEN** a running scan is cancelled
- **THEN** observers receive the results accumulated so far

#### Scenario: Failure

- **WHEN** a scan throws an unexpected error
- **THEN** the error is logged
- **AND** observers are notified of the failure

### Requirement: Completion Notification

On completion the system SHALL post an IDE notification: a warning stating the
number of similar pairs found when the result list is non-empty, and an
informational message stating that none were found otherwise.

#### Scenario: Duplicates found

- **WHEN** a scan finds 12 similar pairs
- **THEN** a warning notification reports 12 similar drawable pairs

#### Scenario: No duplicates found

- **WHEN** a scan finds no similar pairs
- **THEN** an informational notification reports that none were found

### Requirement: Empty Project Short-Circuit

The system SHALL return an empty result without error when discovery finds no
drawable files.

#### Scenario: Project with no drawables

- **WHEN** a scan runs against a project containing no drawables
- **THEN** the scan completes with an empty result
