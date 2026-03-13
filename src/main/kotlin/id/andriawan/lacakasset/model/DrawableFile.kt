package id.andriawan.lacakasset.model

import com.intellij.openapi.vfs.VirtualFile

data class DrawableFile(
    val virtualFile: VirtualFile,
    val resourceName: String,
    val format: DrawableFormat,
    val densityQualifier: String,
    val modulePath: String,
    val sourceSet: String = "main",
    val resourceOrigin: ResourceOrigin = ResourceOrigin.ANDROID_RES
)

enum class ResourceOrigin(val label: String) {
    ANDROID_RES("Android Res"),
    COMPOSE_RESOURCES("Compose Resources")
}

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
