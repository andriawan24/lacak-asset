package id.andriawan.lacakasset.normalizer

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.w3c.dom.Element
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Converts Android vector drawable XML to SVG, so Batik can render it reliably.
 * This avoids the Java2D rendering path and all its color-resolution edge cases.
 *
 * Vectors that use xmlns:aapt (inline gradients) are rendered with their path
 * shapes visible (aapt-filled paths fall back to black), and are compared via
 * a structural fingerprint (SHA-256 of pathData + attributes) rather than
 * perceptual hashing.
 */
class AndroidVectorToSvgConverter(private val colorResolver: ColorResourceResolver) {

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val AAPT_NS = "http://schemas.android.com/aapt"
        private val log = Logger.getInstance(AndroidVectorToSvgConverter::class.java)

        private val ANDROID_COLORS = mapOf(
            "@android:color/white" to "#FFFFFF",
            "@android:color/black" to "#000000",
            "@android:color/transparent" to "#00000000",
            "@android:color/darker_gray" to "#444444",
            "@android:color/dark_gray" to "#444444",
            "@android:color/gray" to "#888888",
            "@android:color/light_gray" to "#CCCCCC",
            "@android:color/background_dark" to "#000000",
            "@android:color/background_light" to "#FFFFFF",
            "@android:color/holo_blue_bright" to "#00DDFF",
            "@android:color/holo_blue_dark" to "#0099CC",
            "@android:color/holo_blue_light" to "#33B5E5",
            "@android:color/holo_green_dark" to "#669900",
            "@android:color/holo_green_light" to "#99CC00",
            "@android:color/holo_orange_dark" to "#FF8800",
            "@android:color/holo_orange_light" to "#FFBB33",
            "@android:color/holo_red_dark" to "#CC0000",
            "@android:color/holo_red_light" to "#FF4444",
            "@android:color/holo_purple" to "#AA66CC"
        )
    }

    /** Returns true if the XML root tag is `<vector>` (i.e. a valid Android vector drawable). */
    fun isVectorDrawable(file: VirtualFile): Boolean {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val root = factory.newDocumentBuilder().parse(file.inputStream).documentElement
            (root.localName ?: root.tagName) == "vector"
        } catch (e: Exception) {
            false
        }
    }

    /** Returns true if the vector uses the aapt: namespace (inline gradients etc.). */
    fun isAaptVector(file: VirtualFile): Boolean {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val root = factory.newDocumentBuilder().parse(file.inputStream).documentElement
            root.lookupNamespaceURI("aapt") != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Converts the vector to an SVG string for rendering.
     * Returns null if the file is not a valid Android vector drawable.
     * For aapt vectors, paths whose fill is defined via aapt:attr are rendered as black
     * so the shape is still visible for thumbnail display.
     */
    fun convertToSvg(project: Project, file: VirtualFile): String? {
        val root = parseVectorRoot(file) ?: return null
        val vpWidth = getAttr(root, "viewportWidth")?.toFloatOrNull() ?: 24f
        val vpHeight = getAttr(root, "viewportHeight")?.toFloatOrNull() ?: 24f

        return buildString {
            append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $vpWidth $vpHeight">""")
            appendChildren(project, root)
            append("</svg>")
        }
    }

    /**
     * Extracts a structural fingerprint (SHA-256) from the vector's pathData and
     * key attributes. Used for comparing aapt vectors by code structure instead
     * of perceptual image similarity.
     */
    fun extractStructuralFingerprint(file: VirtualFile): String? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val root = factory.newDocumentBuilder().parse(file.inputStream).documentElement
            if ((root.localName ?: root.tagName) != "vector") return null

            val sb = StringBuilder()
            sb.append(getAttr(root, "viewportWidth") ?: "24")
            sb.append("x")
            sb.append(getAttr(root, "viewportHeight") ?: "24")
            collectStructure(root, sb)

            MessageDigest.getInstance("SHA-256")
                .digest(sb.toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            log.warn("Failed to extract structural fingerprint: ${file.path}", e)
            null
        }
    }

    private fun collectStructure(parent: Element, sb: StringBuilder) {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            when (node.localName ?: node.tagName) {
                "path" -> {
                    val pathData = getAttr(node, "pathData") ?: continue
                    sb.append("|path:").append(pathData.replace(Regex("\\s+"), " ").trim())
                    getAttr(node, "fillColor")?.let { sb.append(":fc=$it") }
                    getAttr(node, "strokeColor")?.let { sb.append(":sc=$it") }
                    getAttr(node, "strokeWidth")?.let { sb.append(":sw=$it") }
                    getAttr(node, "fillAlpha")?.let { sb.append(":fa=$it") }
                    getAttr(node, "strokeAlpha")?.let { sb.append(":sa=$it") }
                    getAttr(node, "fillType")?.let { sb.append(":ft=$it") }
                }
                "group" -> {
                    sb.append("|group")
                    getAttr(node, "translateX")?.let { sb.append(":tx=$it") }
                    getAttr(node, "translateY")?.let { sb.append(":ty=$it") }
                    getAttr(node, "rotation")?.let { sb.append(":r=$it") }
                    getAttr(node, "scaleX")?.let { sb.append(":sx=$it") }
                    getAttr(node, "scaleY")?.let { sb.append(":sy=$it") }
                    collectStructure(node, sb)
                    sb.append("|/group")
                }
            }
        }
    }

    private fun parseVectorRoot(file: VirtualFile): Element? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val root = factory.newDocumentBuilder().parse(file.inputStream).documentElement
            val rootName = root.localName ?: root.tagName
            if (rootName == "vector") root else null
        } catch (e: Exception) {
            log.warn("Failed to parse vector drawable: ${file.path}", e)
            null
        }
    }

    private fun StringBuilder.appendChildren(project: Project, parent: Element) {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node !is Element) continue
            when (node.localName ?: node.tagName) {
                "path" -> appendPath(project, node)
                "group" -> appendGroup(project, node)
            }
        }
    }

    private fun StringBuilder.appendPath(project: Project, element: Element) {
        val pathData = getAttr(element, "pathData") ?: return
        val fillAlpha = getAttr(element, "fillAlpha")?.toFloatOrNull() ?: 1f
        val strokeAlpha = getAttr(element, "strokeAlpha")?.toFloatOrNull() ?: 1f
        val strokeWidth = getAttr(element, "strokeWidth")?.toFloatOrNull()
        val fillType = getAttr(element, "fillType")

        // Resolve fill: use declared fillColor, or fall back to black if the fill is
        // defined via aapt:attr (inline gradient) so the shape remains visible.
        val fillColorStr = resolveColor(project, getAttr(element, "fillColor"))
            ?: if (hasAaptChild(element)) "#000000" else null

        val strokeColorStr = resolveColor(project, getAttr(element, "strokeColor"))

        append("""<path d="${escapeXml(pathData)}" """)

        if (fillColorStr != null) {
            val (r, g, b, a) = parseArgb(fillColorStr)
            val opacity = (a / 255f * fillAlpha).coerceIn(0f, 1f)
            append("""fill="rgb($r,$g,$b)" fill-opacity="$opacity" """)
        } else {
            append("""fill="none" """)
        }

        if (fillType != null) {
            val rule = if (fillType.equals("evenOdd", ignoreCase = true)) "evenodd" else "nonzero"
            append("""fill-rule="$rule" """)
        }

        if (strokeColorStr != null && strokeWidth != null && strokeWidth > 0f) {
            val (r, g, b, a) = parseArgb(strokeColorStr)
            val opacity = (a / 255f * strokeAlpha).coerceIn(0f, 1f)
            append("""stroke="rgb($r,$g,$b)" stroke-opacity="$opacity" stroke-width="$strokeWidth" """)
            getAttr(element, "strokeLineCap")?.let { append("""stroke-linecap="${it.lowercase()}" """) }
            getAttr(element, "strokeLineJoin")?.let { append("""stroke-linejoin="${it.lowercase()}" """) }
        }

        append("/>")
    }

    private fun StringBuilder.appendGroup(project: Project, element: Element) {
        val translateX = getAttr(element, "translateX")?.toFloatOrNull() ?: 0f
        val translateY = getAttr(element, "translateY")?.toFloatOrNull() ?: 0f
        val rotation = getAttr(element, "rotation")?.toFloatOrNull() ?: 0f
        val pivotX = getAttr(element, "pivotX")?.toFloatOrNull() ?: 0f
        val pivotY = getAttr(element, "pivotY")?.toFloatOrNull() ?: 0f
        val scaleX = getAttr(element, "scaleX")?.toFloatOrNull() ?: 1f
        val scaleY = getAttr(element, "scaleY")?.toFloatOrNull() ?: 1f

        val transform = buildString {
            if (translateX != 0f || translateY != 0f) append("translate($translateX,$translateY) ")
            if (rotation != 0f) append("rotate($rotation,$pivotX,$pivotY) ")
            if (scaleX != 1f || scaleY != 1f) {
                if (pivotX != 0f || pivotY != 0f) {
                    append("translate($pivotX,$pivotY) scale($scaleX,$scaleY) translate(${-pivotX},${-pivotY}) ")
                } else {
                    append("scale($scaleX,$scaleY) ")
                }
            }
        }.trim()

        if (transform.isEmpty()) append("<g>") else append("""<g transform="$transform">""")
        appendChildren(project, element)
        append("</g>")
    }

    /** Returns true if any immediate child element belongs to the aapt namespace. */
    private fun hasAaptChild(element: Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.namespaceURI == AAPT_NS) return true
        }
        return false
    }

    private fun resolveColor(project: Project, color: String?): String? {
        if (color == null) return null
        if (color.startsWith("#")) return color
        if (color.startsWith("@color/")) return colorResolver.resolve(project, color)
        if (color.startsWith("@android:color/")) return ANDROID_COLORS[color] ?: "#000000"
        if (color.startsWith("?")) return "#000000"
        return null
    }

    private fun parseArgb(colorStr: String): IntArray {
        val hex = colorStr.removePrefix("#")
        return when (hex.length) {
            8 -> intArrayOf(
                hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16),
                hex.substring(6, 8).toInt(16), hex.substring(0, 2).toInt(16)
            )
            6 -> intArrayOf(
                hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16), 255
            )
            3 -> intArrayOf(
                hex.substring(0, 1).repeat(2).toInt(16), hex.substring(1, 2).repeat(2).toInt(16),
                hex.substring(2, 3).repeat(2).toInt(16), 255
            )
            else -> intArrayOf(0, 0, 0, 255)
        }
    }

    private fun getAttr(element: Element, localName: String): String? {
        return element.getAttributeNS(ANDROID_NS, localName).ifEmpty { null }
    }

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
