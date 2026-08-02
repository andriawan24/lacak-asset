package id.andriawan.lacakasset.service

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import id.andriawan.lacakasset.engine.SimilarityEngine
import id.andriawan.lacakasset.model.DrawableFile
import id.andriawan.lacakasset.model.DrawableFormat
import id.andriawan.lacakasset.model.HashedDrawable
import id.andriawan.lacakasset.normalizer.ColorResourceResolver
import id.andriawan.lacakasset.normalizer.DrawableNormalizer
import id.andriawan.lacakasset.scanner.DrawableFileScanner
import id.andriawan.lacakasset.settings.DrawableAnalyzerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * The single scan implementation behind every entry point.
 *
 * Full scans, targeted scans, and external candidate checks differ only in what they ask
 * of this pipeline and which part of its output they present; discovery, hashing, and
 * comparison are performed the same way for all three.
 *
 * Read actions cover virtual file system traversal, modification stamps, and reading file
 * bytes. Decoding, rendering, and hashing run outside the lock and in parallel, so a slow
 * SVG cannot block write actions for the duration of its rendering.
 */
class ScanPipeline(private val project: Project) {

    companion object {
        /** Files whose bytes are held in memory at once, bounding peak usage on large projects. */
        private const val BATCH_SIZE = 32
        private val log = Logger.getInstance(ScanPipeline::class.java)
    }

    private val scanner = DrawableFileScanner()
    private val colorResolver = ColorResourceResolver()
    private val similarityEngine = SimilarityEngine()

    /**
     * Batik's transcoder is not documented as thread-safe, so each worker thread gets its
     * own normalizer rather than sharing one.
     */
    private val normalizers = ThreadLocal.withInitial { DrawableNormalizer() }

    /** Discovers the project's drawables, honouring exclusions and the enabled format set. */
    suspend fun discover(): List<DrawableFile> {
        val settings = DrawableAnalyzerSettings.getInstance(project).state
        val excludedDirs = settings.excludedDirectorySet()
        val enabledFormats = settings.enabledFormats()

        return readAction { scanner.findDrawableFiles(project, excludedDirs) }
            .filter { it.format in enabledFormats }
    }

    /**
     * Normalizes and hashes [files], reusing cached entries whose modification stamp still
     * matches. [onProgress] is invoked as files complete.
     */
    suspend fun hashAll(
        files: List<DrawableFile>,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<HashedDrawable> {
        if (files.isEmpty()) return emptyList()

        val cache = DrawableHashCacheService.getInstance(project)
        val colors = loadColorsIfNeeded(files)
        val results = mutableListOf<HashedDrawable>()
        var processed = 0

        for (batch in files.chunked(BATCH_SIZE)) {
            coroutineContext.ensureActive()

            val prepared = readAction { batch.map { prepare(it, cache) } }

            val hashed = coroutineScope {
                prepared.map { item ->
                    async(Dispatchers.Default) {
                        when (item) {
                            is Prepared.Cached -> item.hashed
                            is Prepared.Raw -> normalizers.get()
                                .hash(item.file, item.bytes, colors, similarityEngine)
                                ?.also { cache.put(item.file.path, it) }

                            is Prepared.Unreadable -> null
                        }
                    }
                }.awaitAll()
            }

            results.addAll(hashed.filterNotNull())
            processed += batch.size
            onProgress(processed, files.size)
        }

        return results
    }

    /**
     * Hashes one drawable that may live outside the project, so it is never cached and its
     * bytes are read directly.
     */
    suspend fun hashSingle(file: DrawableFile): HashedDrawable? {
        val colors = loadColorsIfNeeded(listOf(file))
        val bytes = readAction {
            runCatching { file.virtualFile.contentsToByteArray() }.getOrNull()
        } ?: return null

        return normalizers.get().hash(file, bytes, colors, similarityEngine)
    }

    /** Compares every unordered pair, retaining matches down to the retention floor. */
    fun compareAll(hashed: List<HashedDrawable>): List<id.andriawan.lacakasset.model.SimilarityResult> =
        similarityEngine.findRetainedPairs(hashed)

    /** Compares one drawable against all candidates, retaining matches down to the floor. */
    fun compareToTarget(
        target: HashedDrawable,
        candidates: List<HashedDrawable>
    ): List<id.andriawan.lacakasset.model.SimilarityResult> =
        similarityEngine.findRetainedPairsForTarget(target, candidates)

    /**
     * Colour resolution walks the virtual file system, so it runs once under a read action
     * and only when a vector drawable is actually present.
     */
    private suspend fun loadColorsIfNeeded(files: List<DrawableFile>): Map<String, String> {
        if (files.none { it.format == DrawableFormat.ANDROID_VECTOR }) return emptyMap()
        return readAction { colorResolver.loadColors(project) }
    }

    private fun prepare(file: DrawableFile, cache: DrawableHashCacheService): Prepared {
        val virtualFile = file.virtualFile
        val stamp = virtualFile.modificationStamp
        val cached = cache.getCached(virtualFile.path)

        if (cached != null && cached.modificationStamp == stamp) {
            return Prepared.Cached(cached.copy(file = file))
        }

        val bytes = try {
            virtualFile.contentsToByteArray()
        } catch (e: Exception) {
            log.warn("Failed to read drawable: ${virtualFile.path}", e)
            return Prepared.Unreadable
        }

        return Prepared.Raw(file, bytes)
    }

    private sealed interface Prepared {
        data class Cached(val hashed: HashedDrawable) : Prepared

        /** Not a data class: [bytes] must not participate in equality. */
        class Raw(val file: DrawableFile, val bytes: ByteArray) : Prepared

        data object Unreadable : Prepared
    }
}
