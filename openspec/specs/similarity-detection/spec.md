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
the configured threshold. Surviving pairs SHALL be reported with the pHash
similarity as the score.

#### Scenario: Pair rejected by pre-filter

- **WHEN** a pair has dHash similarity of 0.60
- **THEN** the pair is rejected
- **AND** no pHash comparison is reported for it

#### Scenario: Pair rejected at the threshold

- **WHEN** a pair has dHash similarity of 0.95 and pHash similarity of 0.85
- **AND** the configured threshold is 0.90
- **THEN** the pair is rejected

#### Scenario: Pair accepted

- **WHEN** a pair has dHash similarity of 0.95 and pHash similarity of 0.93
- **AND** the configured threshold is 0.90
- **THEN** the pair is reported at 93%

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
drawables exactly once and SHALL return the matches ordered by descending
similarity.

#### Scenario: Result ordering

- **WHEN** a scan produces matches at 100%, 95%, and 92%
- **THEN** they are returned in that order

### Requirement: Targeted Comparison

For a targeted scan the system SHALL compare one designated drawable against
every deduplicated candidate, excluding the target's own file path, and SHALL
return the matches ordered by descending similarity.

#### Scenario: Target excluded from its own results

- **WHEN** a targeted scan runs against `ic_home.png`
- **THEN** `ic_home.png` does not appear as its own match

### Requirement: Similarity Result Content

Each reported match SHALL identify both drawables, the similarity as an integer
percentage, and the similarity as a normalized fraction.

#### Scenario: Reported match fields

- **WHEN** a pair is reported at a normalized similarity of 0.934
- **THEN** the integer percentage is 93
