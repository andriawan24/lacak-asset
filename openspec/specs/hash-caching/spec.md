# hash-caching Specification

## Purpose

Avoid recomputing hashes and thumbnails for drawables that have not changed since
the previous scan, and invalidate cached entries when the underlying files change
on disk.

Implemented by `service/DrawableHashCacheService.kt` and
`listener/DrawableFileChangeListener.kt`.

## Requirements

### Requirement: Per-Project Cache

The system SHALL maintain one hash cache per open project, keyed by absolute file
path, holding the computed hashes, structural fingerprint, thumbnail, and the
modification stamp the entry was computed from.

#### Scenario: Two projects open

- **WHEN** two projects are open simultaneously
- **THEN** each has an independent cache

### Requirement: Modification-Stamp Cache Validity

The system SHALL reuse a cached entry only when its stored modification stamp
equals the file's current modification stamp, and SHALL recompute otherwise.

#### Scenario: Unchanged file on a repeat scan

- **WHEN** a drawable is rescanned and its modification stamp is unchanged
- **THEN** the cached hashes are reused
- **AND** the image is not decoded again

#### Scenario: File edited between scans

- **WHEN** a drawable's content changes and its modification stamp advances
- **THEN** the cached entry is not reused
- **AND** the drawable is re-decoded and re-hashed

### Requirement: Cache Invalidation on File Events

The system SHALL invalidate the cached entry for a drawable when the virtual file
system reports a create, delete, content-change, move, or copy event affecting a
file with a supported drawable extension inside a `drawable` or `drawable-*`
directory.

#### Scenario: Drawable content changed on disk

- **WHEN** a PNG under `res/drawable/` is modified outside the IDE and the change is detected
- **THEN** the cache entry for that path is removed

#### Scenario: Unrelated file changed

- **WHEN** a Kotlin source file is modified
- **THEN** no cache entry is invalidated

### Requirement: Stale-Results Flag

The system SHALL record that drawables have changed since the last completed
scan, and SHALL clear that record when a scan completes.

#### Scenario: Change after a completed scan

- **WHEN** a scan has completed and a drawable is then modified
- **THEN** the cache reports that a change has occurred since the last scan

#### Scenario: Rescan clears the flag

- **WHEN** a subsequent scan completes
- **THEN** the change record is cleared

### Requirement: Cache Disposal

The system SHALL clear the cache when the project is disposed.

#### Scenario: Project closed

- **WHEN** the project is closed
- **THEN** all cached entries are released
