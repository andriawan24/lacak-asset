package id.andriawan.lacakasset.model

import com.intellij.openapi.vfs.VirtualFile

data class DrawableFile(
    val virtualFile: VirtualFile,
    val resourceName: String,
    val format: DrawableFormat,
    val densityQualifier: String,
    val modulePath: String
)

enum class DrawableFormat(val extensions: Set<String>) {
    PNG(setOf("png")),
    JPG(setOf("jpg", "jpeg")),
    WEBP(setOf("webp")),
    SVG(setOf("svg")),
    ANDROID_VECTOR(setOf("xml"));

    companion object {
        fun fromExtension(extension: String): DrawableFormat? {
            return entries.find { extension.lowercase() in it.extensions }
        }
    }
}
