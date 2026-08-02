## MODIFIED Requirements

### Requirement: Cache Invalidation on File Events

The system SHALL invalidate the cached entry for a drawable when the virtual file
system reports a create, delete, content-change, move, or copy event affecting a
file with a supported drawable extension inside a recognized drawable directory.
The recognition rule SHALL be the same one used during discovery, so a
`drawable` or `drawable-*` directory qualifies only when it sits directly under
`res` or `composeResources`.

#### Scenario: Drawable content changed on disk

- **WHEN** a PNG under `res/drawable/` is modified outside the IDE and the change is detected
- **THEN** the cache entry for that path is removed

#### Scenario: Unrelated file changed

- **WHEN** a Kotlin source file is modified
- **THEN** no cache entry is invalidated

#### Scenario: Drawable-named directory outside a resource root

- **WHEN** a PNG under an unrelated `assets/drawable/` directory is modified
- **THEN** no cache entry is invalidated, because discovery never indexed that file

## REMOVED Requirements

### Requirement: Stale-Results Flag

**Reason**: Nothing reads the flag. It is written on invalidation and cleared on
scan completion, but no code path or user-visible behaviour depends on its value,
so it records state that never influences anything. The settings toggle that was
presumably intended to surface it is likewise never consulted.

**Migration**: None. No behaviour observable by the user changes. If a
stale-results banner is wanted later, it should be specified together with the UI
that presents it.
