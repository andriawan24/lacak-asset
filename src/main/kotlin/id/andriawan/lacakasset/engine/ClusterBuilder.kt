package id.andriawan.lacakasset.engine

import id.andriawan.lacakasset.model.DrawableCluster
import id.andriawan.lacakasset.model.HashedDrawable
import id.andriawan.lacakasset.model.SimilarityResult

/**
 * Groups similarity pairs into clusters and picks each cluster's canonical member.
 *
 * Connected components are used rather than strict cliques: a clique-based grouping would
 * place one drawable in several groups, which puts the same file back into multiple rows
 * and leaves "which copy is redundant" undefined. The cost is that a chain of links can
 * merge two drawables that do not themselves resemble each other, which the cluster's
 * weakest similarity makes visible.
 */
object ClusterBuilder {

    /** Highest density first; an unqualified variant outranks every qualified one. */
    private val DENSITY_PRIORITY = mapOf(
        "" to 0,
        "xxxhdpi" to 1,
        "xxhdpi" to 2,
        "xhdpi" to 3,
        "hdpi" to 4,
        "mdpi" to 5,
        "ldpi" to 6
    )

    /**
     * Builds clusters from [pairs] scoring at or above [threshold].
     *
     * [drawablesByPath] supplies the member data; a pair naming a path that is absent — a
     * file deleted since the scan, say — is skipped rather than failing the build.
     *
     * [canonicalOverrides] pins a cluster's canonical member by path, so a user's choice
     * survives re-clustering when the threshold moves. [referenceCounts] supplies usage
     * counts where they have been computed; absent entries simply do not discriminate.
     */
    fun build(
        pairs: List<SimilarityResult>,
        drawablesByPath: Map<String, HashedDrawable>,
        threshold: Double,
        canonicalOverrides: Set<String> = emptySet(),
        referenceCounts: Map<String, Int> = emptyMap()
    ): List<DrawableCluster> {
        val links = pairs.filter { it.normalizedSimilarity >= threshold }
        if (links.isEmpty()) return emptyList()

        val unionFind = UnionFind()
        for (pair in links) {
            unionFind.union(pair.fileA.path, pair.fileB.path)
        }

        // Strongest and weakest link per component, so a chained-in member stays visible.
        val strongest = mutableMapOf<String, Double>()
        val weakest = mutableMapOf<String, Double>()
        for (pair in links) {
            val root = unionFind.find(pair.fileA.path)
            val score = pair.normalizedSimilarity
            strongest[root] = maxOf(strongest[root] ?: score, score)
            weakest[root] = minOf(weakest[root] ?: score, score)
        }

        val membersByRoot = linkedMapOf<String, MutableList<HashedDrawable>>()
        val seen = mutableSetOf<String>()
        for (pair in links) {
            for (path in listOf(pair.fileA.path, pair.fileB.path)) {
                if (!seen.add(path)) continue
                val drawable = drawablesByPath[path] ?: continue
                membersByRoot.getOrPut(unionFind.find(path)) { mutableListOf() }.add(drawable)
            }
        }

        return membersByRoot.mapNotNull { (root, members) ->
            // A component needs two survivors to describe a duplicate at all.
            if (members.size < 2) return@mapNotNull null

            DrawableCluster(
                members = members.sortedBy { it.file.path },
                canonical = selectCanonical(members, canonicalOverrides, referenceCounts),
                strongestSimilarity = strongest[root] ?: 0.0,
                weakestSimilarity = weakest[root] ?: 0.0
            )
        }.sortedByDescending { it.estimatedSaving }
    }

    /**
     * Picks the copy to keep by the first discriminating rule: highest density, then most
     * references, then largest pixel area, then smallest file, then lowest path.
     *
     * The final rule exists to guarantee determinism — a canonical member that shifted
     * between runs would move the delete action's target with it.
     */
    fun selectCanonical(
        members: List<HashedDrawable>,
        canonicalOverrides: Set<String> = emptySet(),
        referenceCounts: Map<String, Int> = emptyMap()
    ): HashedDrawable {
        members.find { it.file.path in canonicalOverrides }?.let { return it }

        return members.sortedWith(
            compareBy<HashedDrawable> { DENSITY_PRIORITY[it.file.densityQualifier] ?: Int.MAX_VALUE }
                .thenByDescending { referenceCounts[it.file.path] ?: 0 }
                .thenByDescending { it.pixelArea }
                .thenBy { it.file.byteSize }
                .thenBy { it.file.path }
        ).first()
    }

    /** Disjoint-set over file paths, with path compression and union by size. */
    private class UnionFind {
        private val parent = mutableMapOf<String, String>()
        private val size = mutableMapOf<String, Int>()

        fun find(path: String): String {
            var root = parent.getOrPut(path) { path }
            while (root != parent[root]) {
                val grandparent = parent[parent[root]!!]!!
                parent[root] = grandparent
                root = grandparent
            }
            return root
        }

        fun union(a: String, b: String) {
            val rootA = find(a)
            val rootB = find(b)
            if (rootA == rootB) return

            val sizeA = size.getOrPut(rootA) { 1 }
            val sizeB = size.getOrPut(rootB) { 1 }
            if (sizeA >= sizeB) {
                parent[rootB] = rootA
                size[rootA] = sizeA + sizeB
            } else {
                parent[rootA] = rootB
                size[rootB] = sizeA + sizeB
            }
        }
    }
}
