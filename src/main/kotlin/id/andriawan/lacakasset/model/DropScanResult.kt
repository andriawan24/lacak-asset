package id.andriawan.lacakasset.model

import java.awt.image.BufferedImage

sealed class DropScanResult {
    data class Success(
        val results: List<SimilarityResult>,
        val droppedThumbnail: BufferedImage
    ) : DropScanResult()

    data class Error(val message: String) : DropScanResult()
}
