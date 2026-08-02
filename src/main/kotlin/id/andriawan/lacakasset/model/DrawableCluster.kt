package id.andriawan.lacakasset.model

/**
 * A group of drawables judged to be the same asset.
 *
 * Clusters are connected components over above-threshold similarity links, so a drawable
 * belongs to exactly one cluster. That is what makes [canonical] — the copy to keep — and
 * the deletion of the remaining members well defined.
 *
 * Similarity is not transitive, so a member can be drawn in through a chain rather than by
 * resembling every other member. [weakestSimilarity] exposes that: a cluster whose range is
 * wide contains at least one loosely-attached member.
 */
data class DrawableCluster(
    val members: List<HashedDrawable>,
    val canonical: HashedDrawable,
    val strongestSimilarity: Double,
    val weakestSimilarity: Double,
    /** True when the cluster describes a file that is not part of the project. */
    val isExternal: Boolean = false
) {
    /** Members that would be removed by reducing the cluster to its canonical copy. */
    val redundantMembers: List<HashedDrawable>
        get() = members.filter { it.file.path != canonical.file.path }

    /**
     * True when members do not all share one format. Deleting across formats swaps a raster
     * for a vector or the reverse, which changes rendering rather than merely removing a copy.
     */
    val isMixedFormat: Boolean
        get() = members.map { it.file.format }.distinct().size > 1

    /** Bytes recoverable by keeping only the canonical member. */
    val estimatedSaving: Long
        get() = redundantMembers.sumOf { it.file.byteSize }

    val memberCount: Int get() = members.size

    val strongestPercent: Int get() = (strongestSimilarity * 100).toInt()
    val weakestPercent: Int get() = (weakestSimilarity * 100).toInt()

    /** Reads as "100%" for a uniform cluster and "96-78%" when a member is loosely attached. */
    val similarityRangeLabel: String
        get() = if (strongestPercent == weakestPercent) {
            "$strongestPercent%"
        } else {
            "$strongestPercent-$weakestPercent%"
        }

    fun contains(path: String): Boolean = members.any { it.file.path == path }

    /** Returns this cluster with [path] as its canonical member, or unchanged if absent. */
    fun withCanonical(path: String): DrawableCluster {
        val member = members.find { it.file.path == path } ?: return this
        return copy(canonical = member)
    }
}
