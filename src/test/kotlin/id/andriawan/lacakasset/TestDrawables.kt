package id.andriawan.lacakasset

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import id.andriawan.lacakasset.engine.ImageHash
import id.andriawan.lacakasset.model.DrawableFile
import id.andriawan.lacakasset.model.DrawableFormat
import id.andriawan.lacakasset.model.HashedDrawable
import id.andriawan.lacakasset.model.SimilarityResult
import java.awt.image.BufferedImage
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal [VirtualFile] for tests.
 *
 * The platform's own `LightVirtualFile` cannot be used here: its static initialiser reaches
 * for a running Application, which plain unit tests do not have. Only the members the
 * production code actually touches are implemented; the rest throw so a test that starts
 * depending on them fails loudly rather than silently reading a default.
 */
class StubVirtualFile(
    private val filePath: String,
    private val byteLength: Long = 0
) : VirtualFile() {
    override fun getName(): String = filePath.substringAfterLast('/')
    override fun getPath(): String = filePath
    override fun getLength(): Long = byteLength
    override fun isWritable(): Boolean = true
    override fun isDirectory(): Boolean = false
    override fun isValid(): Boolean = true
    override fun getParent(): VirtualFile? = null
    override fun getChildren(): Array<VirtualFile> = emptyArray()
    override fun getTimeStamp(): Long = 0
    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) = Unit
    override fun contentsToByteArray(): ByteArray = ByteArray(0)

    override fun getFileSystem(): VirtualFileSystem = throw UnsupportedOperationException()
    override fun getInputStream(): InputStream = throw UnsupportedOperationException()
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream =
        throw UnsupportedOperationException()
}

fun drawableFile(
    path: String,
    byteSize: Long = 1000,
    format: DrawableFormat = DrawableFormat.PNG,
    densityQualifier: String = "",
    modulePath: String = ":app",
    resourceName: String = path.substringAfterLast('/').substringBeforeLast('.')
): DrawableFile = DrawableFile(
    virtualFile = StubVirtualFile(path, byteSize),
    path = path,
    byteSize = byteSize,
    resourceName = resourceName,
    format = format,
    densityQualifier = densityQualifier,
    modulePath = modulePath
)

/** A hash whose every bit is zero, so similarity is decided by the test's own pair scores. */
private fun zeroHash(bits: Int) = ImageHash(LongArray((bits + 63) / 64), bits)

/**
 * A hash of [bitLength] bits with the lowest [setBits] set. Comparing it against a zero hash
 * yields a Hamming distance of exactly [setBits], which is how the engine tests dial a pair
 * to a chosen similarity.
 */
fun hashWithSetBits(setBits: Int, bitLength: Int): ImageHash {
    val longs = LongArray((bitLength + 63) / 64)
    repeat(setBits) { i -> longs[i / 64] = longs[i / 64] or (1L shl (i % 64)) }
    return ImageHash(longs, bitLength)
}

/**
 * Builds a drawable whose hashes sit at chosen Hamming distances from a zero-hash
 * counterpart, so a test can place a pair precisely relative to the retention floor.
 */
fun hashedAtDistance(
    path: String,
    dHashDistance: Int,
    pHashDistance: Int,
    byteSize: Long = 1000,
    format: DrawableFormat = DrawableFormat.PNG,
    densityQualifier: String = "",
    modulePath: String = ":app",
    resourceName: String = path.substringAfterLast('/').substringBeforeLast('.')
): HashedDrawable = HashedDrawable(
    file = drawableFile(path, byteSize, format, densityQualifier, modulePath, resourceName),
    dHash = hashWithSetBits(dHashDistance, 64),
    pHash = hashWithSetBits(pHashDistance, 255),
    thumbnail = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
    modificationStamp = 0,
    sourceWidth = 100,
    sourceHeight = 100
)

fun hashed(
    path: String,
    byteSize: Long = 1000,
    format: DrawableFormat = DrawableFormat.PNG,
    densityQualifier: String = "",
    modulePath: String = ":app",
    width: Int = 100,
    height: Int = 100,
    structuralFingerprint: String? = null
): HashedDrawable = HashedDrawable(
    file = drawableFile(path, byteSize, format, densityQualifier, modulePath),
    dHash = zeroHash(64),
    pHash = zeroHash(255),
    thumbnail = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
    modificationStamp = 0,
    structuralFingerprint = structuralFingerprint,
    sourceWidth = width,
    sourceHeight = height
)

fun pair(a: HashedDrawable, b: HashedDrawable, similarity: Double): SimilarityResult =
    SimilarityResult(
        fileA = a.file,
        fileB = b.file,
        similarityPercent = (similarity * 100).toInt(),
        normalizedSimilarity = similarity
    )

fun lookup(vararg drawables: HashedDrawable): Map<String, HashedDrawable> =
    drawables.associateBy { it.file.path }
