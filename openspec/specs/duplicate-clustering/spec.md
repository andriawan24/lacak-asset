# duplicate-clustering Specification

## Purpose
TBD - created by archiving change cluster-based-drawable-results. Update Purpose after archive.
## Requirements
### Requirement: Cluster Formation

The system SHALL group similarity pairs into clusters by connected components:
two drawables belong to the same cluster when a chain of retained pairs links
them. Every drawable that participates in at least one retained pair SHALL belong
to exactly one cluster, and a drawable SHALL NOT appear in more than one cluster.

#### Scenario: Direct pair

- **WHEN** only the pair (A, B) is retained
- **THEN** one cluster containing exactly A and B is produced

#### Scenario: Transitive chain

- **WHEN** the pairs (A, B) and (B, C) are retained and (A, C) is not
- **THEN** a single cluster containing A, B, and C is produced

#### Scenario: Disjoint groups

- **WHEN** the pairs (A, B) and (C, D) are retained with no link between the groups
- **THEN** two separate clusters are produced

#### Scenario: Unmatched drawable

- **WHEN** a drawable participates in no retained pair
- **THEN** it belongs to no cluster and is not presented as a result

### Requirement: Cluster Similarity Range

Each cluster SHALL report the strongest and weakest similarity among the pairs
that link its members, so that a member drawn in through a transitive chain is
visible rather than hidden behind the best score.

#### Scenario: Uniform cluster

- **WHEN** every linking pair in a cluster scores 100%
- **THEN** the cluster reports a range of 100% to 100%

#### Scenario: Chained cluster

- **WHEN** a cluster is formed from pairs scoring 96% and 78%
- **THEN** the cluster reports a range of 96% to 78%

#### Scenario: Two-member cluster

- **WHEN** a cluster contains exactly two members linked by one pair at 93%
- **THEN** the cluster reports 93% as both its strongest and weakest similarity

### Requirement: Canonical Member Selection

Each cluster SHALL designate exactly one member as canonical — the copy to keep —
selected by the first discriminating rule in this order: highest density variant;
greatest number of references found in the project; largest pixel area; smallest
file size in bytes; lowest file path in lexicographic order. The final rule
guarantees a deterministic outcome.

#### Scenario: Density decides

- **WHEN** a cluster contains an `xxhdpi` variant and an `hdpi` variant
- **THEN** the `xxhdpi` variant is canonical

#### Scenario: Reference count decides

- **WHEN** two members share the same density and one has 7 references while the other has 1
- **THEN** the member with 7 references is canonical

#### Scenario: All rules tie

- **WHEN** two members are indistinguishable under every rule
- **THEN** the member with the lexicographically lower path is canonical
- **AND** repeated runs select the same member

### Requirement: Deferred Reference Counting

Reference counting SHALL NOT run during the scan. The system SHALL select a
provisional canonical member from the remaining rules, and SHALL recompute the
selection with reference counts only when a cluster is selected by the user.

#### Scenario: Scan completes

- **WHEN** a scan produces 200 clusters
- **THEN** no project-wide reference search has been performed

#### Scenario: Cluster selected

- **WHEN** the user selects a cluster
- **THEN** references are counted for its members
- **AND** the canonical designation is recomputed

### Requirement: Canonical Override

The user SHALL be able to designate any member of a cluster as canonical, and
that choice SHALL take precedence over the heuristic for as long as the results
are displayed.

#### Scenario: User overrides

- **WHEN** the user marks a non-canonical member as canonical
- **THEN** that member becomes canonical
- **AND** the previously canonical member becomes non-canonical

#### Scenario: Override survives reselection

- **WHEN** the user overrides the canonical member and then selects another cluster and returns
- **THEN** the overridden designation is still in effect

### Requirement: Mixed-Format Clusters

A cluster whose members do not all share the same format SHALL be marked as mixed
format. Cross-format matches SHALL continue to form clusters rather than being
excluded.

#### Scenario: Raster and vector together

- **WHEN** a cluster contains `ic_close.png` and `ic_close.xml`
- **THEN** the cluster is marked as mixed format

#### Scenario: JPEG and PNG together

- **WHEN** a cluster contains a JPEG and a PNG
- **THEN** the cluster is marked as mixed format

#### Scenario: Single format

- **WHEN** every member of a cluster is a PNG
- **THEN** the cluster is not marked as mixed format

### Requirement: Estimated Cluster Saving

Each cluster SHALL report the total byte length of its non-canonical members as
the space recoverable by reducing the cluster to its canonical member.

#### Scenario: Three-member cluster

- **WHEN** a cluster holds a canonical 40 KB file and two non-canonical files of 12 KB and 9 KB
- **THEN** the cluster reports 21 KB as its estimated saving

#### Scenario: Canonical changed

- **WHEN** the user designates a different member as canonical
- **THEN** the estimated saving is recomputed against the new canonical member

