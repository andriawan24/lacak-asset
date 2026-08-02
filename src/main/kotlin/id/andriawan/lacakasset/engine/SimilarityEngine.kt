package id.andriawan.lacakasset.engine

import com.intellij.openapi.diagnostic.Logger
import id.andriawan.lacakasset.model.DrawableFile
import id.andriawan.lacakasset.model.HashedDrawable
import id.andriawan.lacakasset.model.SimilarityResult
import java.awt.image.BufferedImage

class SimilarityEngine {

    companion object {
        private const val DHASH_PRE_FILTER_THRESHOLD = 0.80

        /**
         * Pairs are retained down to this score regardless of the user's threshold, so the
         * displayed threshold can be lowered without recomparing. Comparison cost is
         * unchanged: every pair is examined either way, only retention differs.
         */
        const val RETENTION_FLOOR = 0.70

        /** Bounds memory on projects with pathologically many near-identical assets. */
        const val RETENTION_CAP = 50_000

        private val DENSITY_PRIORITY = mapOf(
            "" to 0,
            "xxxhdpi" to 1,
            "xxhdpi" to 2,
            "xhdpi" to 3,
            "hdpi" to 4,
            "mdpi" to 5,
            "ldpi" to 6
        )

        private val log = Logger.getInstance(SimilarityEngine::class.java)
    }

    fun computeHashes(
        file: DrawableFile,
        normalizedImage: BufferedImage,
        thumbnail: BufferedImage,
        structuralFingerprint: String? = null,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0
    ): HashedDrawable {
        return HashedDrawable(
            file = file,
            dHash = DHash.compute(normalizedImage),
            pHash = PHash.compute(normalizedImage),
            thumbnail = thumbnail,
            modificationStamp = file.virtualFile.modificationStamp,
            structuralFingerprint = structuralFingerprint,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight
        )
    }

    /**
     * Compares every unordered pair exactly once and returns those at or above the
     * retention floor, ordered by descending similarity.
     */
    fun findRetainedPairs(hashedDrawables: List<HashedDrawable>): List<SimilarityResult> {
        val deduplicated = deduplicateDensityVariants(hashedDrawables)
        val results = mutableListOf<SimilarityResult>()

        for (i in deduplicated.indices) {
            for (j in i + 1 until deduplicated.size) {
                computeSimilarity(deduplicated[i], deduplicated[j])?.let { results.add(it) }
            }
        }

        return capped(results)
    }

    /**
     * Compares [target] against each candidate, excluding the target's own file, and
     * returns those at or above the retention floor, ordered by descending similarity.
     */
    fun findRetainedPairsForTarget(
        target: HashedDrawable,
        candidates: List<HashedDrawable>
    ): List<SimilarityResult> {
        val deduplicated = deduplicateDensityVariants(candidates)
        val results = mutableListOf<SimilarityResult>()

        for (candidate in deduplicated) {
            if (candidate.file.path == target.file.path) continue
            computeSimilarity(target, candidate)?.let { results.add(it) }
        }

        return capped(results)
    }

    /**
     * Keeps the highest-scoring pairs when the cap is exceeded, and records the loss —
     * silently truncating would present a partial result as a complete one.
     */
    private fun capped(results: MutableList<SimilarityResult>): List<SimilarityResult> {
        val sorted = results.sortedByDescending { it.normalizedSimilarity }
        if (sorted.size <= RETENTION_CAP) return sorted

        log.warn("Retained pairs capped at $RETENTION_CAP; dropped ${sorted.size - RETENTION_CAP} lower-scoring pairs")
        return sorted.take(RETENTION_CAP)
    }

    private fun computeSimilarity(a: HashedDrawable, b: HashedDrawable): SimilarityResult? {
        // Exact structural match (same paths, same attributes) → 100%
        if (a.structuralFingerprint != null && b.structuralFingerprint != null &&
            a.structuralFingerprint == b.structuralFingerprint
        ) {
            return SimilarityResult(
                fileA = a.file,
                fileB = b.file,
                similarityPercent = 100,
                normalizedSimilarity = 1.0
            )
        }

        // Fall through to perceptual hash for near-matches and cross-format comparisons
        val dSimilarity = a.dHash.normalizedSimilarity(b.dHash)
        if (dSimilarity < DHASH_PRE_FILTER_THRESHOLD) return null

        val pSimilarity = a.pHash.normalizedSimilarity(b.pHash)
        if (pSimilarity < RETENTION_FLOOR) return null

        return SimilarityResult(
            fileA = a.file,
            fileB = b.file,
            similarityPercent = (pSimilarity * 100).toInt(),
            normalizedSimilarity = pSimilarity
        )
    }

    private fun deduplicateDensityVariants(drawables: List<HashedDrawable>): List<HashedDrawable> {
        return drawables
            .groupBy { it.file.resourceName to it.file.modulePath }
            .values
            .map { group ->
                group.minByOrNull { DENSITY_PRIORITY[it.file.densityQualifier] ?: Int.MAX_VALUE }!!
            }
    }
}
