package id.andriawan.lacakasset.model

import com.intellij.openapi.vfs.VirtualFile

/**
 * A drawable discovered in (or offered to) the project.
 *
 * [path] and [byteSize] are captured during discovery, while a read action is held, rather
 * than being read from [virtualFile] on demand. Results are sorted and repainted often, and
 * each of those reads would otherwise touch the virtual file system from the EDT.
 */
data class DrawableFile(
    val virtualFile: VirtualFile,
    val path: String,
    val byteSize: Long,
    val resourceName: String,
    val format: DrawableFormat,
    val densityQualifier: String,
    val modulePath: String,
    val sourceSet: String = "main",
    val resourceOrigin: ResourceOrigin = ResourceOrigin.ANDROID_RES
) {
    val fileName: String get() = "$resourceName.${format.extensions.first()}"

    /** Reads as `:app (main, Android Res)`. */
    val sourceDescription: String get() = "$modulePath ($sourceSet, ${resourceOrigin.label})"
}

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
