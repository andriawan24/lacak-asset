# similarity-detection Specification

## Purpose

Decide which pairs of drawables are visually the same asset, using perceptual
hashing for raster and rendered content and exact structural comparison for
Android vector drawables.

Implemented by `engine/ImageHash.kt`, `engine/SimilarityEngine.kt`, and
`model/SimilarityResult.kt`.
## Requirements
### Requirement: Difference Hash

The system SHALL compute a 64-bit difference hash by resizing the normalized
image to 9x8 greyscale and setting one bit per horizontally adjacent pixel pair
according to whether the left pixel is brighter than the right.

#### Scenario: dHash width

- **WHEN** a dHash is computed for any normalized image
- **THEN** the hash reports a bit length of 64

### Requirement: Perceptual Hash

The system SHALL compute a perceptual hash by resizing the normalized image to
32x32 greyscale, applying a 2-D discrete cosine transform, taking the top-left
16x16 coefficient block excluding the DC term, and setting one bit per
coefficient according to whether it exceeds the median of that block.

#### Scenario: pHash width

- **WHEN** a pHash is computed for any normalized image
- **THEN** the hash reports a bit length of 255

### Requirement: Hash Similarity Measure

The system SHALL express similarity between two hashes as one minus their
Hamming distance divided by the bit length.

#### Scenario: Identical hashes

- **WHEN** two hashes have a Hamming distance of zero
- **THEN** their similarity is 1.0

### Requirement: Structural Equality Short-Circuit

The system SHALL report a pair at 100% similarity without consulting the
perceptual hashes when both drawables carry a structural fingerprint and those
fingerprints are equal.

#### Scenario: Two identical vector drawables

- **WHEN** two Android vector drawables have equal structural fingerprints
- **THEN** the pair is reported at 100%

#### Scenario: Vectors with differing fingerprints

- **WHEN** two Android vector drawables have unequal structural fingerprints
- **THEN** the pair falls through to perceptual hash comparison

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

### Requirement: Density Variant Deduplication

Before comparison the system SHALL group drawables by resource name and module
path and retain only the highest-density variant of each group, ranked
unqualified > xxxhdpi > xxhdpi > xhdpi > hdpi > mdpi > ldpi.

#### Scenario: Same asset across densities

- **WHEN** `ic_home` exists in `drawable-hdpi`, `drawable-xhdpi`, and `drawable-xxhdpi` of the same module
- **THEN** only the `drawable-xxhdpi` variant participates in comparison
- **AND** no pair is reported between the density variants of `ic_home`

#### Scenario: Unqualified variant present

- **WHEN** `ic_home` exists in both `drawable` and `drawable-xxxhdpi` of the same module
- **THEN** the `drawable` variant is retained

#### Scenario: Same name in different modules

- **WHEN** `ic_home` exists in module `:app` and in module `:core:ui`
- **THEN** both are retained and compared against each other

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

### Requirement: Similarity Result Content

Each reported match SHALL identify both drawables, the similarity as an integer
percentage, and the similarity as a normalized fraction.

#### Scenario: Reported match fields

- **WHEN** a pair is reported at a normalized similarity of 0.934
- **THEN** the integer percentage is 93

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

