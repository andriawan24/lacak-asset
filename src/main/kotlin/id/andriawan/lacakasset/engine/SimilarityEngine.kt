package id.andriawan.lacakasset.engine

import id.andriawan.lacakasset.model.DrawableFile
import id.andriawan.lacakasset.model.HashedDrawable
import id.andriawan.lacakasset.model.SimilarityResult
import java.awt.image.BufferedImage

class SimilarityEngine {

    companion object {
        private const val DHASH_PRE_FILTER_THRESHOLD = 0.80
        private val DENSITY_PRIORITY = mapOf(
            "" to 0,
            "xxxhdpi" to 1,
            "xxhdpi" to 2,
            "xhdpi" to 3,
            "hdpi" to 4,
            "mdpi" to 5,
            "ldpi" to 6
        )
    }

    fun computeHashes(
        file: DrawableFile,
        normalizedImage: BufferedImage,
        thumbnail: BufferedImage,
        structuralFingerprint: String? = null
    ): HashedDrawable {
        return HashedDrawable(
            file = file,
            dHash = DHash.compute(normalizedImage),
            pHash = PHash.compute(normalizedImage),
            thumbnail = thumbnail,
            modificationStamp = file.virtualFile.modificationStamp,
            structuralFingerprint = structuralFingerprint
        )
    }

    fun findSimilarPairs(
        hashedDrawables: List<HashedDrawable>,
        threshold: Double
    ): List<SimilarityResult> {
        val deduplicated = deduplicateDensityVariants(hashedDrawables)
        val results = mutableListOf<SimilarityResult>()

        for (i in deduplicated.indices) {
            for (j in i + 1 until deduplicated.size) {
                val a = deduplicated[i]
                val b = deduplicated[j]

                val result = computeSimilarity(a, b, threshold)

                if (result != null) results.add(result)
            }
        }

        return results.sortedByDescending { it.normalizedSimilarity }
    }

    fun findSimilarToTarget(
        target: HashedDrawable,
        candidates: List<HashedDrawable>,
        threshold: Double
    ): List<SimilarityResult> {
        val deduplicated = deduplicateDensityVariants(candidates)
        val results = mutableListOf<SimilarityResult>()

        for (candidate in deduplicated) {
            if (candidate.file.virtualFile.path == target.file.virtualFile.path) continue

            val result = computeSimilarity(target, candidate, threshold)

            if (result != null) results.add(result)
        }

        return results.sortedByDescending { it.normalizedSimilarity }
    }

    private fun computeSimilarity(a: HashedDrawable, b: HashedDrawable, threshold: Double): SimilarityResult? {
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
        if (pSimilarity < threshold) return null

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
