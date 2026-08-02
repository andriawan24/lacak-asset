## Context

The plugin is a 2400-line IntelliJ plugin with no test sources. Its core is sound —
perceptual hashing, vector-to-SVG conversion, density deduplication — but the layer above
has accreted three parallel implementations of one idea.

`DrawableScanService` holds three pipelines (`performScan`, `performSingleFileScan`,
`scanDroppedFile`). Each re-reads settings, re-parses the excluded-directories string,
walks the project, filters by enabled format, and hashes. They have already drifted:
two run image decoding inside `readAction {}`, the third deliberately does not and
carries a comment saying so. Results reach the UI through four mutable callback
properties on the service that the panel assigns and nulls out on dispose — a
single-subscriber channel that loses results when the tool window is reopened, because
the service still holds `_results` but nothing re-reads it.

The UI has the same shape of duplication: two table models, two copies of a percent
renderer, two copies of `formatFileSize`, and a second results surface
(`DropCheckDialog`) that exists only because the main panel could not host a second
kind of result. `DrawableFileChangeListener` keeps its own copy of the drawable
extension set and a weaker copy of the drawable-directory test that omits the
`res`/`composeResources` parent check.

Constraints: no new dependencies; JDK 21; IntelliJ 2024.2+; Batik and TwelveMonkeys
already present. The user is not available during implementation, and the plugin must
not be launched for UI verification — correctness has to come from compilation, unit
tests, and reading.

## Goals / Non-Goals

**Goals:**

- One scan pipeline serving all three invocation modes.
- Results modelled as clusters, so a drawable appears exactly once in the UI.
- One results surface in the tool window; no second dialog.
- Actionable results: delete a redundant copy without leaving the tool window.
- Observable scan state that survives tool-window close and reopen.
- Image decoding off the read lock and parallelised.
- First tests in the repository, covering the logic this change introduces.

**Non-Goals:**

- Reference rewriting or consolidation. Deleting a drawable and repointing every
  `R.drawable.x`, `@drawable/x`, and `Res.drawable.x` reference at a survivor is a
  separate feature with its own risk profile; this change delegates deletion to the
  platform's Safe Delete and stops there.
- Changing the hashing algorithms, the vector-to-SVG converter, or the normalization
  geometry. Those are the parts that work.
- Persisting scan results across IDE restarts.
- Any UI verification by running the IDE.

## Decisions

### Clusters via union-find over retained pairs

Similarity is not transitive, so "a group of duplicates" needs a definition. Connected
components (union-find) is chosen over strict cliques.

Cliques are more defensible per-group, but a drawable can belong to several cliques,
which puts the same file back in multiple rows — the exact problem this change exists to
remove — and makes "which copy is redundant" ill-defined. Connected components guarantee
one drawable, one cluster, which is what makes a canonical member and a delete action
coherent.

The cost is chaining: A~B at 92% and B~C at 92% merges A and C even at 80%. This is
mitigated by exposing the cluster's weakest linking similarity in the UI rather than
only its best, so a chained-in outlier is visible. Union-find with path compression over
at most 50000 pairs is microseconds, so re-clustering on every slider movement is free.

### Retention floor of 70%, threshold applied afterwards

Comparison already examines every pair; the threshold only decides what is kept. Moving
the cutoff to a fixed 0.70 floor and applying the user's threshold as a post-filter makes
the slider instant at negligible scan cost. The dHash pre-filter at 0.80 is unchanged and
still eliminates most pairs before the pHash comparison.

Retention is capped at 50000 pairs, keeping the highest-scoring, with a log line when
truncation occurs. A silent cap would misrepresent a truncated result as complete.

### Canonical selection deferred, not eliminated

Reference counting is the most useful signal for "which copy do we keep", and the least
affordable: it is a project-wide search per file. Running it for every member of every
cluster during a scan would dominate scan time.

The heuristic therefore runs in two stages. During the scan, canonical selection uses only
free signals — density, pixel area, byte size, path. When the user selects a cluster,
references are counted for that cluster's members only and the canonical designation is
recomputed. A user override pins the choice and suppresses both stages.

Path ordering is the final tie-breaker specifically so the result is deterministic; a
non-deterministic canonical would make the delete action's target shift between runs.

### Safe Delete over a hand-written deletion

`SafeDeleteHandler` already knows how to find usages across Kotlin, Java, and XML, shows
a conflict preview, and integrates with undo. Writing our own would mean reimplementing
that for Android `R.drawable`, XML `@drawable/`, and Compose Multiplatform `Res.drawable`
— three reference syntaxes — while remaining unable to detect string-based
`getIdentifier()` lookups at all. Delegating keeps the risk with the platform.

The canonical member is never offered for deletion, so the action cannot empty a cluster.
Mixed-format clusters get an extra confirmation naming both formats, because deleting a
PNG in favour of a vector is a rendering change, not a copy removal.

### StateFlow over MessageBus or callback lists

The service already receives a `CoroutineScope`, so `StateFlow` costs nothing to adopt.
It retains the last value, which fixes the reopen-loses-results defect for free, and
supports multiple observers without the null-on-dispose dance the four callback
properties require today.

MessageBus is the platform-idiomatic alternative but has no retained value, so a panel
attaching after a scan would still show nothing — the defect would survive. The state is
a sealed hierarchy: `Idle`, `Scanning(processed, total)`, `Ready(pairs, clusters)`,
`Failed(message)`. `Ready` carries the raw pairs alongside the clusters so the slider can
re-cluster without involving the service.

### Read lock held only for VFS access

The pipeline reads file bytes into memory inside `readAction`, then decodes, renders, and
hashes outside it. Holding the read lock through Batik SVG transcoding can stall write
actions for as long as rendering takes, which on a large SVG is not bounded.

Hashing is parallelised over `Dispatchers.Default`. Batik's transcoder is not documented
as thread-safe, so `SvgRenderer` is instantiated per worker rather than shared as it is
today. `ImageIO.read` is safe to call concurrently. The hash output is independent of
scheduling, so parallelism does not change results.

### Master-detail layout

The tool window is anchored to the bottom of the IDE: wide and short. The current design
splits vertically, so the table and the previews each get half the available height and
neither has enough. A horizontal split puts a compact cluster list on the leading side and
gives the detail pane the width it needs for member cards with previews and per-member
actions.

A TreeTable was considered — clusters as parent rows, members as children — and rejected
because per-member action buttons inside tree cells are awkward in Swing, and the member
previews that make the results judgeable have nowhere to go.

### External candidates as a pinned pseudo-cluster

Folding the drop dialog into the tool window deletes `DropCheckDialog`,
`DropCheckTableModel`, and two duplicated renderers, and gives all three entry points one
destination. The candidate becomes the canonical member of a cluster pinned to the top of
the list, marked external, with deletion and canonical override disabled on it.

The trade-off is real: the dialog was modeless, so a user could compare a candidate while
the main results stayed visible. Pinning preserves most of that — existing results remain
listed below the pinned cluster — but only one cluster's detail is visible at a time.

## Risks / Trade-offs

- **Chained clusters merge visually distinct icons** → The similarity range column exposes
  a weak link. At a 70% floor with the slider low, clusters will get large; this is the
  first thing to look at when the UI is reviewable.
- **Safe Delete cannot see string-based resource lookups** (`getIdentifier`, generated
  accessors) → Inherent to the approach and to Android resources generally. The mixed-format
  confirmation and the platform's usage preview are the guardrails; the canonical member is
  never deletable.
- **Batik thread safety is assumed, not documented** → Per-worker renderer instances rather
  than a shared one. If a threading defect still appears, the parallel stage can be reduced
  to sequential without touching anything else, since it is one dispatcher call.
- **No UI verification this pass** → The user is AFK and has excluded running the IDE.
  Correctness rests on compilation, unit tests over the extracted logic, and the fact that
  the pure-logic layer is now separable from Swing. Swing wiring is the part that stays
  unverified; it is called out in the completion report rather than glossed.
- **50000-pair cap could truncate a pathological project** → Logged, not silent. The cap
  exists so a project with thousands of near-identical assets cannot exhaust memory.
- **Cluster membership shifts as the slider moves** → A user could mark a canonical member,
  move the slider, and find the cluster has split or merged. Overrides are keyed by file
  path and reapplied where the file is still present.

## Migration Plan

Four phases, each compiling and passing tests on its own:

1. **Internals.** Remove dead code, collapse the three pipelines into one, replace the
   callback properties with `StateFlow`, move decoding off the read lock, parallelise
   hashing, deduplicate the extension set and directory test into the scanner. No visible
   change except that reopening the tool window keeps results and the unused refresh-reminder
   setting disappears.
2. **Cluster model.** Union-find, canonical heuristic, mixed-format detection, saving
   calculation, plus the tests. The existing pair table keeps working, fed by clusters
   flattened back to pairs, so the UI is untouched.
3. **UI.** Master-detail panel, member cards, filters, slider. Delete `DropCheckDialog`,
   `DropCheckTableModel`, `SimilarityTableModel`, and the duplicated renderers. Fold the
   external candidate in as a pinned cluster.
4. **Actions.** Safe Delete with the canonical guard and the mixed-format confirmation;
   deferred reference counting on cluster selection.

Rollback is per-phase: phases 1 and 2 are behaviour-preserving and can stand alone if
3 and 4 are abandoned. There is no persisted state to migrate — settings keys are only
removed, and the removed key is one nothing reads.

## Open Questions

None blocking. Two deferred by explicit decision: consolidation with reference rewriting
(rejected for this change, viable as a follow-up on top of the cluster model), and whether
the canonical heuristic's weighting is right in practice — that needs real projects and
cannot be settled before the UI is usable.
