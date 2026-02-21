package id.andriawan.lacakasset.model

import id.andriawan.lacakasset.engine.ImageHash
import java.awt.image.BufferedImage

data class HashedDrawable(
    val file: DrawableFile,
    val dHash: ImageHash,
    val pHash: ImageHash,
    val thumbnail: BufferedImage,
    val modificationStamp: Long,
    val structuralFingerprint: String? = null
)
