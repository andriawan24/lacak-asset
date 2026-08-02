package id.andriawan.lacakasset.normalizer

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import id.andriawan.lacakasset.engine.SimilarityEngine
import id.andriawan.lacakasset.model.DrawableFile
import id.andriawan.lacakasset.model.DrawableFormat
import id.andriawan.lacakasset.model.HashedDrawable
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Turns raw drawable bytes into a canonical image, a thumbnail, and the hashes derived
 * from them.
 *
 * Holds a [SvgRenderer], whose underlying Batik transcoder is not documented as
 * thread-safe, so one instance is created per worker rather than shared.
 */
class DrawableNormalizer {

    companion object {
        const val HASH_RENDER_SIZE = 128
        const val THUMBNAIL_SIZE = 48
        private val log = Logger.getInstance(DrawableNormalizer::class.java)
    }

    private val svgRenderer = SvgRenderer()
    private val vectorConverter = AndroidVectorToSvgConverter()

    /**
     * Decodes [bytes], renders the canonical image and thumbnail, and computes the hashes.
     * Returns null when the content cannot be decoded, or when an XML file turns out not to
     * be a vector drawable (a layout or manifest, say).
     *
     * Performs no virtual file system access, so it is safe to call outside a read action
     * and from multiple threads at once.
     */
    fun hash(
        file: DrawableFile,
        bytes: ByteArray,
        colors: Map<String, String>,
        similarityEngine: SimilarityEngine
    ): HashedDrawable? {
        return try {
            val path = file.path

            var structuralFingerprint: String? = null
            val image = when (file.format) {
                DrawableFormat.PNG, DrawableFormat.JPG, DrawableFormat.WEBP -> {
                    ImageIO.read(ByteArrayInputStream(bytes))
                }

                DrawableFormat.SVG -> {
                    svgRenderer.renderSvg(ByteArrayInputStream(bytes), HASH_RENDER_SIZE, HASH_RENDER_SIZE)
                }

                DrawableFormat.ANDROID_VECTOR -> {
                    val root = vectorConverter.parseVectorRoot(bytes, path) ?: return null
                    structuralFingerprint = vectorConverter.fingerprint(root)
                    val svg = vectorConverter.toSvg(root, colors)
                    svgRenderer.renderSvgFromString(svg, HASH_RENDER_SIZE, HASH_RENDER_SIZE)
                }
            } ?: return null

            val normalized = normalizeImage(image)
            val thumbnail = createThumbnail(image)

            val hashed = similarityEngine.computeHashes(
                file = file,
                normalizedImage = normalized,
                thumbnail = thumbnail,
                structuralFingerprint = structuralFingerprint,
                sourceWidth = image.width,
                sourceHeight = image.height
            )

            image.flush()
            normalized.flush()

            hashed
        } catch (e: Exception) {
            log.warn("Failed to process drawable: ${file.path}", e)
            null
        }
    }

    private fun normalizeImage(image: BufferedImage): BufferedImage {
        val normalized = BufferedImage(HASH_RENDER_SIZE, HASH_RENDER_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g2d = normalized.createGraphics()

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        g2d.color = Gray._128
        g2d.fillRect(0, 0, HASH_RENDER_SIZE, HASH_RENDER_SIZE)

        // Scale to fit maintaining aspect ratio (letterbox)
        val scale = minOf(
            HASH_RENDER_SIZE.toDouble() / image.width,
            HASH_RENDER_SIZE.toDouble() / image.height
        )
        val scaledWidth = (image.width * scale).toInt()
        val scaledHeight = (image.height * scale).toInt()
        val x = (HASH_RENDER_SIZE - scaledWidth) / 2
        val y = (HASH_RENDER_SIZE - scaledHeight) / 2

        g2d.drawImage(image, x, y, scaledWidth, scaledHeight, null)
        g2d.dispose()

        return normalized
    }

    private fun createThumbnail(image: BufferedImage): BufferedImage {
        val thumb = BufferedImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g2d = thumb.createGraphics()
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        g2d.color = JBColor.WHITE
        g2d.fillRect(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE)

        val scale = minOf(
            THUMBNAIL_SIZE.toDouble() / image.width,
            THUMBNAIL_SIZE.toDouble() / image.height
        )
        val w = (image.width * scale).toInt()
        val h = (image.height * scale).toInt()
        val x = (THUMBNAIL_SIZE - w) / 2
        val y = (THUMBNAIL_SIZE - h) / 2

        g2d.drawImage(image, x, y, w, h, null)
        g2d.dispose()

        return thumb
    }
}
