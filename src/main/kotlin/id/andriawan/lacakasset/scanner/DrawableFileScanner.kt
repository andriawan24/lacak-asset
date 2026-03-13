package id.andriawan.lacakasset.scanner

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import id.andriawan.lacakasset.model.DrawableFile
import id.andriawan.lacakasset.model.DrawableFormat
import id.andriawan.lacakasset.model.ResourceOrigin

class DrawableFileScanner {

    companion object {
        val DRAWABLE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "svg", "xml")
        private val SKIP_DIRECTORIES = setOf("build", ".gradle", ".idea", ".git", "node_modules")

        /**
         * Checks if a file is inside a drawable directory.
         * Supports both traditional Android (res/drawable*) and Compose Multiplatform (composeResources/drawable*).
         */
        fun isInDrawableDirectory(file: VirtualFile): Boolean {
            val parent = file.parent ?: return false
            val parentName = parent.name
            if (parentName == "drawable" || parentName.startsWith("drawable-")) {
                val grandparent = parent.parent ?: return false
                return grandparent.name == "res" || grandparent.name == "composeResources"
            }
            return false
        }
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
                val origin = detectResourceOrigin(file)

                val drawableFile = DrawableFile(
                    virtualFile = file,
                    resourceName = extractResourceName(file),
                    format = format,
                    densityQualifier = extractDensityQualifier(file),
                    modulePath = extractModulePath(project, file),
                    sourceSet = extractSourceSet(project, file),
                    resourceOrigin = origin
                )
                drawableFiles.add(drawableFile)

                return true
            }
        })

        return drawableFiles
    }

    private fun isInDrawableDirectory(file: VirtualFile): Boolean =
        Companion.isInDrawableDirectory(file)

    private fun detectResourceOrigin(file: VirtualFile): ResourceOrigin {
        val grandparent = file.parent?.parent ?: return ResourceOrigin.ANDROID_RES
        return if (grandparent.name == "composeResources") {
            ResourceOrigin.COMPOSE_RESOURCES
        } else {
            ResourceOrigin.ANDROID_RES
        }
    }

    private fun extractResourceName(file: VirtualFile): String {
        val name = file.nameWithoutExtension
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

    private fun extractSourceSet(project: Project, file: VirtualFile): String {
        val basePath = project.basePath ?: return "main"
        val relativePath = file.path.removePrefix(basePath).trimStart('/')

        // Pattern: <module>/src/<sourceSet>/res/... or <module>/src/<sourceSet>/composeResources/...
        val srcIndex = relativePath.indexOf("/src/")
        if (srcIndex >= 0) {
            val afterSrc = relativePath.substring(srcIndex + 5) // skip "/src/"
            val nextSlash = afterSrc.indexOf('/')
            if (nextSlash > 0) {
                return afterSrc.substring(0, nextSlash)
            }
        }

        return "main"
    }

    private fun extractModulePath(project: Project, file: VirtualFile): String {
        val basePath = project.basePath ?: return ""
        val filePath = file.path
        val relativePath = filePath.removePrefix(basePath).trimStart('/')

        val srcIndex = relativePath.indexOf("/src/")
        if (srcIndex > 0) {
            val modulePart = relativePath.substring(0, srcIndex)
            return ":${modulePart.replace('/', ':')}"
        }

        return ":"
    }
}
