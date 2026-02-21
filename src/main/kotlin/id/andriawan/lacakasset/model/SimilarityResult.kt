package id.andriawan.lacakasset.model

data class SimilarityResult(
    val fileA: DrawableFile,
    val fileB: DrawableFile,
    val similarityPercent: Int,
    val normalizedSimilarity: Double
)
