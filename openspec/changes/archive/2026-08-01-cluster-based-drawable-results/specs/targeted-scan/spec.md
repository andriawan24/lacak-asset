## MODIFIED Requirements

### Requirement: One-Against-All Comparison

A targeted scan SHALL discover and hash the project's drawables under the same
exclusions and format filters as a full scan, and SHALL then locate the cluster
containing the selected file.

#### Scenario: Targeted comparison scope

- **WHEN** a targeted scan runs on `ic_home.png` in a project with 500 drawables
- **THEN** the project's drawables are discovered and hashed
- **AND** the cluster containing `ic_home.png` is identified

#### Scenario: Cache reused

- **WHEN** a targeted scan follows a completed full scan with no files changed
- **THEN** cached hashes are reused rather than recomputed

### Requirement: Target Not Eligible

The system SHALL report that nothing was found when the selected file is absent
from the discovered set, for example because its format is disabled in
configuration or it falls under an exclusion.

#### Scenario: Selected file's format disabled

- **WHEN** the user targets an SVG while SVG is disabled in configuration
- **THEN** the scan completes reporting that nothing similar was found

### Requirement: Result Presentation

On completion the system SHALL reveal the tool window and SHALL select the
cluster containing the targeted drawable, leaving the remaining results in the
list. When the targeted drawable belongs to no cluster, the system SHALL report
that nothing similar was found.

#### Scenario: Tool window closed when results arrive

- **WHEN** a targeted scan completes and the tool window is hidden
- **THEN** the tool window is shown with the containing cluster selected

#### Scenario: Other results preserved

- **WHEN** a targeted scan completes
- **THEN** clusters not containing the target remain listed rather than being discarded

#### Scenario: No similar drawables

- **WHEN** the targeted drawable belongs to no cluster
- **THEN** the tool window reports that nothing similar was found
