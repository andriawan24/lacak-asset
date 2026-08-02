## MODIFIED Requirements

### Requirement: Similarity Threshold

The system SHALL expose a similarity threshold as an integer percentage between
70 and 100 inclusive, adjustable in steps of 5, defaulting to 90, and SHALL apply
it as the initial position of the tool window's threshold slider. Comparison
itself SHALL retain pairs down to a fixed floor independent of this value.

#### Scenario: Default threshold

- **WHEN** a project has never configured the threshold
- **THEN** the tool window's slider starts at 90%

#### Scenario: Threshold lowered

- **WHEN** the user sets the threshold to 75 and opens the tool window
- **THEN** the slider starts at 75% and clusters are formed from pairs at 75% and above

#### Scenario: Slider does not write back

- **WHEN** the user moves the tool window slider
- **THEN** the stored configuration value is unchanged

## REMOVED Requirements

### Requirement: Modification Tracking

**Reason**: The requirement exists to describe the hand-written modified/reset
handling for the excluded-directories text field, which was needed only because
that one field was not bound like the others. The field is now bound through the
same mechanism as every other setting, so the behaviour is provided by the
platform rather than specified here.

**Migration**: None. The settings page still reports itself as modified when a
field differs from the stored state and still restores stored values on reset;
this is now standard bound-configurable behaviour rather than a distinct
requirement of this plugin.
