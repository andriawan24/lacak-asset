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

                val result = when {
                    a.structuralFingerprint != null && b.structuralFingerprint != null -> {
                        if (a.structuralFingerprint == b.structuralFingerprint) {
                            SimilarityResult(
                                fileA = a.file,
                                fileB = b.file,
                                similarityPercent = 100,
                                normalizedSimilarity = 1.0
                            )
                        } else null
                    }

                    a.structuralFingerprint != null || b.structuralFingerprint != null -> null

                    else -> run {
                        val dSimilarity = a.dHash.normalizedSimilarity(b.dHash)
                        if (dSimilarity < DHASH_PRE_FILTER_THRESHOLD) return@run null

                        val pSimilarity = a.pHash.normalizedSimilarity(b.pHash)
                        if (pSimilarity < threshold) return@run null

                        SimilarityResult(
                            fileA = a.file,
                            fileB = b.file,
                            similarityPercent = (pSimilarity * 100).toInt(),
                            normalizedSimilarity = pSimilarity
                        )
                    }
                }

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

            val result = when {
                target.structuralFingerprint != null && candidate.structuralFingerprint != null -> {
                    if (target.structuralFingerprint == candidate.structuralFingerprint) {
                        SimilarityResult(
                            fileA = target.file,
                            fileB = candidate.file,
                            similarityPercent = 100,
                            normalizedSimilarity = 1.0
                        )
                    } else null
                }

                target.structuralFingerprint != null || candidate.structuralFingerprint != null -> null

                else -> run {
                    val dSimilarity = target.dHash.normalizedSimilarity(candidate.dHash)
                    if (dSimilarity < DHASH_PRE_FILTER_THRESHOLD) return@run null

                    val pSimilarity = target.pHash.normalizedSimilarity(candidate.pHash)
                    if (pSimilarity < threshold) return@run null

                    SimilarityResult(
                        fileA = target.file,
                        fileB = candidate.file,
                        similarityPercent = (pSimilarity * 100).toInt(),
                        normalizedSimilarity = pSimilarity
                    )
                }
            }

            if (result != null) results.add(result)
        }

        return results.sortedByDescending { it.normalizedSimilarity }
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
