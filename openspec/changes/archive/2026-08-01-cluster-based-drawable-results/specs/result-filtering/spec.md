## ADDED Requirements

### Requirement: Live Threshold Slider

The tool window SHALL expose a similarity threshold slider covering 70% to 100%.
Moving it SHALL re-filter the retained pairs and rebuild the clusters from the
results already in memory, without rescanning or re-hashing any file.

#### Scenario: Threshold raised

- **WHEN** results are displayed and the user raises the slider from 85% to 95%
- **THEN** pairs below 95% are discarded and the clusters are rebuilt
- **AND** no progress indicator appears

#### Scenario: Threshold lowered

- **WHEN** the user lowers the slider to 75%
- **THEN** previously hidden pairs at or above 75% reappear and clusters merge accordingly

#### Scenario: Initial position

- **WHEN** the tool window is opened
- **THEN** the slider starts at the threshold stored in configuration

#### Scenario: No results yet

- **WHEN** no scan has run
- **THEN** the slider is present but moving it produces no results

### Requirement: Module Filter

The tool window SHALL let the user restrict results to selected modules. A
cluster SHALL be shown when at least one of its members belongs to a selected
module.

#### Scenario: Single module selected

- **WHEN** the user filters to `:app`
- **THEN** only clusters with at least one member in `:app` are listed

#### Scenario: Cross-module cluster

- **WHEN** a cluster spans `:app` and `:core:ui` and the user filters to `:core:ui`
- **THEN** the cluster is listed with all of its members

#### Scenario: Filter cleared

- **WHEN** the user clears the module filter
- **THEN** all clusters are listed again

### Requirement: Format Filter

The tool window SHALL let the user restrict results to selected formats. A
cluster SHALL be shown when at least one of its members has a selected format.

#### Scenario: Vector-only filter

- **WHEN** the user filters to Android vector
- **THEN** clusters containing no vector member are hidden

#### Scenario: Mixed-format cluster retained

- **WHEN** a cluster contains a PNG and a vector and the user filters to PNG
- **THEN** the cluster is listed

### Requirement: Filters Are Presentation Only

Filters and the threshold slider SHALL affect only what is displayed. They SHALL
NOT alter configuration, discard retained pairs, or trigger a scan, so clearing a
filter restores the full result set without recomputation.

#### Scenario: Filter then clear

- **WHEN** the user applies a module filter and then clears it
- **THEN** the original result set is restored without a scan

#### Scenario: Configuration untouched

- **WHEN** the user moves the threshold slider
- **THEN** the threshold stored in project configuration is unchanged

### Requirement: Empty Filter Result

When active filters exclude every cluster, the system SHALL state that filters
are hiding the results rather than reporting that no duplicates exist.

#### Scenario: Over-restrictive filter

- **WHEN** results exist but every cluster is excluded by the active filters
- **THEN** the list states that the current filters match nothing
- **AND** offers to clear the filters
