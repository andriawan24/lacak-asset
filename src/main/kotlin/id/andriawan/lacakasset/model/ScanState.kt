package id.andriawan.lacakasset.model

import id.andriawan.lacakasset.engine.ClusterBuilder

/**
 * The current state of drawable analysis for a project.
 *
 * Exactly one value is current at any time, and it is retained, so a tool window that
 * attaches after a scan finished immediately sees the results rather than an empty panel.
 */
sealed interface ScanState {

    data object Idle : ScanState

    data class Scanning(val processed: Int, val total: Int) : ScanState

    /**
     * [pairs] holds every retained pair, down to the retention floor rather than the
     * displayed threshold, so the threshold can be changed without rescanning.
     * [drawablesByPath] carries the member data those pairs refer to.
     */
    data class Ready(
        val pairs: List<SimilarityResult>,
        val drawablesByPath: Map<String, HashedDrawable>
    ) : ScanState {

        /** Groups the retained pairs into clusters at [threshold]; cheap enough to call per keystroke. */
        fun clustersAt(
            threshold: Double,
            canonicalOverrides: Set<String> = emptySet(),
            referenceCounts: Map<String, Int> = emptyMap()
        ): List<DrawableCluster> = ClusterBuilder.build(
            pairs = pairs,
            drawablesByPath = drawablesByPath,
            threshold = threshold,
            canonicalOverrides = canonicalOverrides,
            referenceCounts = referenceCounts
        )
    }

    data class Failed(val message: String) : ScanState
}
