package id.andriawan.lacakasset.normalizer

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Collects `@color/...` definitions declared in the project's `values/colors.xml` files.
 *
 * Loading walks the virtual file system, so it runs once inside a read action before
 * hashing begins. The resulting map is then read without any lock by the vector
 * converter running on worker threads.
 */
class ColorResourceResolver {

    /** Walks the project and returns colour name to hex value, e.g. `brand_primary` to `#FF0000`. */
    fun loadColors(project: Project): Map<String, String> {
        val baseDir = project.guessProjectDir() ?: return emptyMap()
        val colors = mutableMapOf<String, String>()

        VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (file.isDirectory) {
                    return file.name != "build" && !file.name.startsWith(".")
                }
                if (file.name == "colors.xml" && file.parent?.name == "values") {
                    parseColorsXml(file, colors)
                }
                return true
            }
        })

        return colors
    }

    private fun parseColorsXml(file: VirtualFile, into: MutableMap<String, String>) {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val doc = factory.newDocumentBuilder().parse(file.inputStream)
            val colorElements = doc.getElementsByTagName("color")
            for (i in 0 until colorElements.length) {
                val element = colorElements.item(i)
                val name = element.attributes.getNamedItem("name")?.nodeValue ?: continue
                val value = element.textContent?.trim() ?: continue
                if (value.startsWith("#")) {
                    into[name] = value
                }
            }
        } catch (_: Exception) {
            // Silently skip unparseable colors.xml files
        }
    }
}
