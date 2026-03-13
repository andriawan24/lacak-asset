package id.andriawan.lacakasset.scanner

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import id.andriawan.lacakasset.model.DrawableFile
import id.andriawan.lacakasset.model.DrawableFormat

class DrawableFileScanner {

    companion object {
        private val DRAWABLE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "svg", "xml")
        private val SKIP_DIRECTORIES = setOf("build", ".gradle", ".idea", ".git", "node_modules")
    }

    fun findDrawableFiles(project: Project, excludedDirs: Set<String> = emptySet()): List<DrawableFile> {
        val baseDir = project.guessProjectDir() ?: return emptyList()
        val drawableFiles = mutableListOf<DrawableFile>()
        val allExcluded = SKIP_DIRECTORIES + excludedDirs

        VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (file.isDirectory) {
                    return file.name !in allExcluded && !file.name.startsWith(".")
                }

                val extension = file.extension?.lowercase() ?: return true
                if (extension !in DRAWABLE_EXTENSIONS) return true
                if (!isInDrawableDirectory(file)) return true

                // Skip nine-patch files
                if (file.name.endsWith(".9.png")) return true

                val format = DrawableFormat.fromExtension(extension) ?: return true

                // For XML files, only include vector drawables (checked later during normalization)
                val drawableFile = DrawableFile(
                    virtualFile = file,
                    resourceName = extractResourceName(file),
                    format = format,
                    densityQualifier = extractDensityQualifier(file),
                    modulePath = extractModulePath(project, file)
                )
                drawableFiles.add(drawableFile)

                return true
            }
        })

        return drawableFiles
    }

    private fun isInDrawableDirectory(file: VirtualFile): Boolean {
        val parent = file.parent ?: return false
        val parentName = parent.name
        if (parentName == "drawable" || parentName.startsWith("drawable-")) {
            // Verify parent's parent is a "res" directory
            val grandparent = parent.parent ?: return false
            return grandparent.name == "res"
        }
        return false
    }

    private fun extractResourceName(file: VirtualFile): String {
        val name = file.nameWithoutExtension
        // Remove .9 suffix for nine-patch (shouldn't reach here, but defensive)
        return if (name.endsWith(".9")) name.removeSuffix(".9") else name
    }

    private fun extractDensityQualifier(file: VirtualFile): String {
        val dirName = file.parent?.name ?: return ""
        return if (dirName.startsWith("drawable-")) {
            dirName.removePrefix("drawable-")
        } else {
            ""
        }
    }

    private fun extractModulePath(project: Project, file: VirtualFile): String {
        val basePath = project.basePath ?: return ""
        val filePath = file.path
        val relativePath = filePath.removePrefix(basePath).trimStart('/')

        // Extract module path from relative path (e.g., "app/src/main/res/..." -> ":app")
        val srcIndex = relativePath.indexOf("/src/")
        if (srcIndex > 0) {
            val modulePart = relativePath.substring(0, srcIndex)
            return ":${modulePart.replace('/', ':')}"
        }

        // If directly in project root's src
        return ":"
    }
}
