# drawable-normalization Specification

## Purpose

Convert every supported drawable format into a directly comparable raster image,
so that visually identical assets stored in different formats produce comparable
hashes.

Implemented by `normalizer/DrawableNormalizer.kt`, `normalizer/SvgRenderer.kt`,
`normalizer/AndroidVectorToSvgConverter.kt`, and
`normalizer/ColorResourceResolver.kt`.

## Requirements

### Requirement: Format-Specific Decoding

The system SHALL decode each supported format to a `BufferedImage`:
PNG, JPG, and WebP via `ImageIO`; SVG via the Apache Batik PNG transcoder;
and Android vector XML by first converting it to SVG and then rendering that SVG
via Batik.

#### Scenario: Raster formats

- **WHEN** a PNG, JPEG, or WebP file is normalized
- **THEN** it is decoded with `ImageIO.read`

#### Scenario: SVG file

- **WHEN** an SVG file is normalized
- **THEN** Batik renders it at 128x128

#### Scenario: Android vector drawable

- **WHEN** an Android vector XML file is normalized
- **THEN** it is first converted to an SVG string
- **AND** that SVG string is rendered by Batik at 128x128

### Requirement: Vector Root Tag Validation

The system SHALL treat an XML file as an Android vector drawable only when its
root element local name is `vector`, and SHALL skip the file otherwise.

#### Scenario: Animated vector drawable

- **WHEN** an XML file in a drawable directory has root element `animated-vector`
- **THEN** conversion returns no SVG
- **AND** the file is skipped without failing the scan

#### Scenario: Non-drawable XML reached via external comparison

- **WHEN** an external XML file that is not a `<vector>` is submitted for comparison
- **THEN** normalization returns no result

### Requirement: Vector-to-SVG Element Translation

The system SHALL translate `<path>` and `<group>` elements from the Android
vector namespace into their SVG equivalents, preserving path data, fill colour,
fill alpha, fill rule, stroke colour, stroke width, stroke alpha, stroke line
cap, stroke line join, and group translate/rotate/scale transforms about their
pivot.

#### Scenario: Path with fill and stroke

- **WHEN** a path declares `android:fillColor`, `android:strokeColor`, and a positive `android:strokeWidth`
- **THEN** the emitted SVG path carries `fill`, `fill-opacity`, `stroke`, `stroke-opacity`, and `stroke-width`

#### Scenario: Path with no resolvable fill

- **WHEN** a path has no fill colour and no inline aapt fill
- **THEN** the emitted SVG path uses `fill="none"`

#### Scenario: Even-odd fill rule

- **WHEN** a path declares `android:fillType="evenOdd"`
- **THEN** the emitted SVG path carries `fill-rule="evenodd"`

#### Scenario: Group with pivot-based scale

- **WHEN** a group declares a non-unit scale and a non-zero pivot
- **THEN** the emitted transform translates to the pivot, scales, and translates back

### Requirement: Colour Reference Resolution

The system SHALL resolve colour references: literal `#RGB`, `#RRGGBB`, and
`#AARRGGBB` values are used directly; `@color/name` is resolved from the
project's `res/values/colors.xml` files; `@android:color/name` is resolved from a
built-in framework colour table; theme attribute references beginning with `?`
fall back to black; and an unresolvable `@color/` reference falls back to black.

#### Scenario: Project colour reference

- **WHEN** a path declares `android:fillColor="@color/brand_primary"`
- **AND** `colors.xml` defines `brand_primary` as `#FF6200EE`
- **THEN** the emitted fill uses that colour

#### Scenario: Framework colour reference

- **WHEN** a path declares `android:fillColor="@android:color/white"`
- **THEN** the emitted fill is `#FFFFFF`

#### Scenario: Unresolvable reference

- **WHEN** a path declares a `@color/` name absent from every `colors.xml`
- **THEN** the emitted fill is black

#### Scenario: Theme attribute reference

- **WHEN** a path declares `android:fillColor="?attr/colorPrimary"`
- **THEN** the emitted fill is black

### Requirement: Inline Gradient Fallback

The system SHALL render a path whose fill is supplied by an `aapt:attr` child as
solid black, so that the path shape remains visible for thumbnail display.

#### Scenario: Path with inline gradient

- **WHEN** a path has an `aapt:attr` child instead of a `fillColor` attribute
- **THEN** the emitted SVG path is filled solid black

### Requirement: Structural Fingerprint Extraction

For Android vector drawables the system SHALL compute a SHA-256 fingerprint over
the viewport dimensions plus, in document order, each path's whitespace-collapsed
path data together with its fill colour, stroke colour, stroke width, fill alpha,
stroke alpha, and fill type, and each group's translate, rotation, and scale
values.

#### Scenario: Two vectors with identical structure

- **WHEN** two vector XML files declare the same viewport, path data, and attributes
- **THEN** their structural fingerprints are equal

#### Scenario: Vectors differing only in whitespace

- **WHEN** two vector files declare the same path data with different internal whitespace
- **THEN** their structural fingerprints are equal

#### Scenario: Vectors differing in fill colour

- **WHEN** two vector files share path data but declare different `fillColor` values
- **THEN** their structural fingerprints differ

#### Scenario: Non-vector XML

- **WHEN** fingerprint extraction is attempted on an XML file whose root is not `vector`
- **THEN** no fingerprint is produced

### Requirement: Canonical Hash Image

The system SHALL produce, for every drawable, a 128x128 ARGB image with a neutral
grey background onto which the decoded image is drawn scaled to fit and centred,
preserving aspect ratio.

#### Scenario: Non-square source image

- **WHEN** a 200x100 source image is normalized
- **THEN** the result is 128x128
- **AND** the drawn content preserves the 2:1 aspect ratio and is centred

### Requirement: Thumbnail Generation

The system SHALL produce a 48x48 ARGB thumbnail per drawable, on a white
background, with the decoded image scaled to fit and centred, for display in the
results UI.

#### Scenario: Thumbnail for a scanned drawable

- **WHEN** a drawable is normalized
- **THEN** a 48x48 thumbnail accompanies its hashes

### Requirement: Fault Isolation

The system SHALL log and skip any drawable that fails to decode, parse, or
render, and SHALL continue processing the remaining files.

#### Scenario: Corrupted image file

- **WHEN** one drawable in the project cannot be decoded
- **THEN** a warning is logged
- **AND** the remaining drawables are still processed
