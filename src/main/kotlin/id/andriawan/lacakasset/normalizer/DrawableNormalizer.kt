package id.andriawan.lacakasset.normalizer

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import id.andriawan.lacakasset.engine.SimilarityEngine
import id.andriawan.lacakasset.model.DrawableFile
import id.andriawan.lacakasset.model.DrawableFormat
import id.andriawan.lacakasset.model.HashedDrawable
import id.andriawan.lacakasset.service.DrawableHashCacheService
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class DrawableNormalizer {

    companion object {
        const val HASH_RENDER_SIZE = 128
        const val THUMBNAIL_SIZE = 48
        private val log = Logger.getInstance(DrawableNormalizer::class.java)
    }

    private val svgRenderer = SvgRenderer()
    private val colorResolver = ColorResourceResolver()
    private val vectorConverter = AndroidVectorToSvgConverter(colorResolver)

    fun normalizeAndHash(
        files: List<DrawableFile>,
        project: Project,
        cacheService: DrawableHashCacheService,
        similarityEngine: SimilarityEngine
    ): List<HashedDrawable> {
        val results = mutableListOf<HashedDrawable>()

        for (file in files) {
            try {
                // Check cache first
                val cached = cacheService.getCached(file.virtualFile.path)
                if (cached != null && cached.modificationStamp == file.virtualFile.modificationStamp) {
                    results.add(cached.copy(file = file))
                    continue
                }

                val thumbImage = loadAsBufferedImage(file, project) ?: continue

                val structuralFingerprint = if (file.format == DrawableFormat.ANDROID_VECTOR &&
                    vectorConverter.isAaptVector(file.virtualFile)
                ) {
                    vectorConverter.extractStructuralFingerprint(file.virtualFile)
                } else null

                val normalized = normalizeImage(thumbImage)
                val thumbnail = createThumbnail(thumbImage)

                val hashed = similarityEngine.computeHashes(file, normalized, thumbnail, structuralFingerprint)
                cacheService.put(file.virtualFile.path, hashed)
                results.add(hashed)

                // Release image memory
                thumbImage.flush()
                normalized.flush()
            } catch (e: Exception) {
                log.warn("Failed to process drawable: ${file.virtualFile.path}", e)
            }
        }

        return results
    }

    private fun loadAsBufferedImage(file: DrawableFile, project: Project): BufferedImage? {
        return when (file.format) {
            DrawableFormat.PNG, DrawableFormat.JPG, DrawableFormat.WEBP -> {
                ImageIO.read(file.virtualFile.inputStream)
            }

            DrawableFormat.SVG -> {
                svgRenderer.renderSvg(
                    file.virtualFile.inputStream,
                    HASH_RENDER_SIZE,
                    HASH_RENDER_SIZE
                )
            }

            DrawableFormat.ANDROID_VECTOR -> {
                val svg = vectorConverter.convertToSvg(project, file.virtualFile) ?: return null
                svgRenderer.renderSvgFromString(svg, HASH_RENDER_SIZE, HASH_RENDER_SIZE)
            }
        }
    }

    private fun normalizeImage(image: BufferedImage): BufferedImage {
        val normalized = BufferedImage(HASH_RENDER_SIZE, HASH_RENDER_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g2d = normalized.createGraphics()

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        // Mid-gray background so light-colored AND dark-colored icons are both visible.
        // White background causes white/light icons on transparent to composite to all-white,
        // producing a flat all-zero hash that matches every other blank image at 100%.
        g2d.color = Color(128, 128, 128)
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

        g2d.color = Color.WHITE
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
