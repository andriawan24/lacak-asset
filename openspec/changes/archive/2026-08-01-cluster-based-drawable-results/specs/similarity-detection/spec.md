## ADDED Requirements

### Requirement: Pair Retention Floor

Comparison SHALL retain every pair scoring at or above a fixed floor of 0.70,
independent of the configured threshold, so that the displayed threshold can be
adjusted without recomparing. The configured threshold SHALL NOT be applied
during comparison.

#### Scenario: Pair below the configured threshold retained

- **WHEN** a pair scores 0.78 and the configured threshold is 0.90
- **THEN** the pair is retained for later filtering
- **AND** it is not presented until the displayed threshold is lowered to 0.78 or below

#### Scenario: Pair below the floor discarded

- **WHEN** a pair scores 0.65
- **THEN** the pair is discarded and cannot be recovered without rescanning

### Requirement: Retention Cap

The system SHALL cap the number of retained pairs at 50000, keeping the
highest-scoring pairs, and SHALL log that truncation occurred rather than
discarding silently.

#### Scenario: Cap exceeded

- **WHEN** comparison produces more than 50000 pairs at or above the floor
- **THEN** the 50000 highest-scoring pairs are retained
- **AND** a log entry records how many were dropped

#### Scenario: Cap not reached

- **WHEN** comparison produces fewer than 50000 retained pairs
- **THEN** all are retained and nothing is logged

### Requirement: Cluster Emission

A comparison pass SHALL return the retained pairs together with the clusters
formed from them at the currently displayed threshold, so that a consumer needs
no second pass to obtain either view.

#### Scenario: Pass result

- **WHEN** a comparison pass completes
- **THEN** both the retained pairs and the clusters derived from them are available

## MODIFIED Requirements

### Requirement: Two-Stage Perceptual Comparison

The system SHALL reject a pair whose dHash similarity is below 0.80 without
computing further, and SHALL then reject a pair whose pHash similarity is below
the retention floor of 0.70. Surviving pairs SHALL be retained with the pHash
similarity as the score, and the displayed threshold SHALL be applied afterwards
as a filter rather than during comparison.

#### Scenario: Pair rejected by pre-filter

- **WHEN** a pair has dHash similarity of 0.60
- **THEN** the pair is rejected
- **AND** no pHash comparison is reported for it

#### Scenario: Pair rejected at the floor

- **WHEN** a pair has dHash similarity of 0.95 and pHash similarity of 0.65
- **THEN** the pair is rejected

#### Scenario: Pair retained below the displayed threshold

- **WHEN** a pair has dHash similarity of 0.95 and pHash similarity of 0.85
- **AND** the displayed threshold is 0.90
- **THEN** the pair is retained but not presented

#### Scenario: Pair accepted

- **WHEN** a pair has dHash similarity of 0.95 and pHash similarity of 0.93
- **AND** the displayed threshold is 0.90
- **THEN** the pair is retained and presented at 93%

### Requirement: Exhaustive Pair Comparison

For a full scan the system SHALL compare every unordered pair of deduplicated
drawables exactly once and SHALL return the retained pairs ordered by descending
similarity.

#### Scenario: Result ordering

- **WHEN** a scan produces retained pairs at 100%, 95%, and 92%
- **THEN** they are returned in that order

### Requirement: Targeted Comparison

A targeted scan SHALL perform the same exhaustive comparison pass as a full scan
and SHALL then present only the cluster containing the designated drawable. The
target SHALL NOT be reported as a match against itself.

#### Scenario: Target excluded from its own results

- **WHEN** a targeted scan runs against `ic_home.png`
- **THEN** `ic_home.png` does not appear as its own match

#### Scenario: Target has matches

- **WHEN** a targeted scan runs against a drawable that is similar to two others
- **THEN** the cluster containing all three is presented

#### Scenario: Target has no matches

- **WHEN** the designated drawable belongs to no cluster
- **THEN** the result reports that nothing similar was found
