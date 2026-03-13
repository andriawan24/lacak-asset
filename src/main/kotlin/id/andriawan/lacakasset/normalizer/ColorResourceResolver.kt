package id.andriawan.lacakasset.normalizer

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import javax.xml.parsers.DocumentBuilderFactory

class ColorResourceResolver {

    private val colorCache = mutableMapOf<String, String>()

    fun resolve(project: Project, colorRef: String): String {
        if (!colorRef.startsWith("@color/")) return colorRef
        if (colorRef.startsWith("#")) return colorRef

        val colorName = colorRef.removePrefix("@color/")

        colorCache[colorName]?.let { return it }

        // Search for colors.xml in the project
        loadColorsFromProject(project)

        return colorCache[colorName] ?: "#000000"
    }

    private fun loadColorsFromProject(project: Project) {
        if (colorCache.isNotEmpty()) return

        val baseDir = project.guessProjectDir() ?: return
        VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (file.isDirectory) {
                    return file.name != "build" && !file.name.startsWith(".")
                }
                if (file.name == "colors.xml" && file.parent?.name == "values") {
                    parseColorsXml(file)
                }
                return true
            }
        })
    }

    private fun parseColorsXml(file: VirtualFile) {
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
                    colorCache[name] = value
                }
            }
        } catch (_: Exception) {
            // Silently skip unparseable colors.xml files
        }
    }

}
