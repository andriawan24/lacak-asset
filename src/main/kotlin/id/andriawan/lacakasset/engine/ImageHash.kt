package id.andriawan.lacakasset.engine

import java.awt.RenderingHints
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ColorConvertOp
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A compact perceptual hash stored as a LongArray.
 * Similarity is measured via normalized Hamming distance.
 */
data class ImageHash(val bits: LongArray, val bitLength: Int) {

    fun hammingDistance(other: ImageHash): Int {
        var distance = 0
        for (i in bits.indices) {
            distance += java.lang.Long.bitCount(bits[i] xor other.bits[i])
        }
        return distance
    }

    fun normalizedSimilarity(other: ImageHash): Double {
        return 1.0 - hammingDistance(other).toDouble() / bitLength
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageHash) return false
        return bits.contentEquals(other.bits) && bitLength == other.bitLength
    }

    override fun hashCode(): Int = bits.contentHashCode()
}

/**
 * Difference Hash (dHash) - compares adjacent pixels in a row.
 * Fast, good at detecting near-duplicates.
 * Produces a 64-bit hash from a 9x8 grayscale image.
 */
object DHash {

    fun compute(image: BufferedImage): ImageHash {
        val width = 9
        val height = 8
        val gray = toGrayscale(resize(image, width, height))

        var hash = 0L
        var bit = 0

        for (y in 0 until height) {
            for (x in 0 until width - 1) {
                val left = gray.getRGB(x, y) and 0xFF
                val right = gray.getRGB(x + 1, y) and 0xFF
                if (left > right) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }

        return ImageHash(longArrayOf(hash), 64)
    }
}

/**
 * Perceptual Hash (pHash) - uses DCT to capture frequency information.
 * More robust than dHash, better for cross-format comparison.
 * Produces a 256-bit hash from the DCT of a 32x32 grayscale image.
 */
object PHash {

    private const val SIZE = 32
    private const val SMALL = 16 // top-left 16x16 of DCT (= 256 bits)

    fun compute(image: BufferedImage): ImageHash {
        val gray = toGrayscale(resize(image, SIZE, SIZE))

        val pixels = Array(SIZE) { y ->
            DoubleArray(SIZE) { x ->
                (gray.getRGB(x, y) and 0xFF).toDouble()
            }
        }

        val dct = dct2d(pixels)

        val coefficients = mutableListOf<Double>()
        for (y in 0 until SMALL) {
            for (x in 0 until SMALL) {
                if (x == 0 && y == 0) continue
                coefficients.add(dct[y][x])
            }
        }

        val sorted = coefficients.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2]
        }

        val bitLength = coefficients.size
        val longs = LongArray((bitLength + 63) / 64)
        for (i in coefficients.indices) {
            if (coefficients[i] > median) {
                longs[i / 64] = longs[i / 64] or (1L shl (i % 64))
            }
        }

        return ImageHash(longs, bitLength)
    }

    private fun dct2d(input: Array<DoubleArray>): Array<DoubleArray> {
        val n = input.size
        val temp = Array(n) { DoubleArray(n) }
        val result = Array(n) { DoubleArray(n) }

        for (y in 0 until n) {
            for (u in 0 until n) {
                var sum = 0.0
                for (x in 0 until n) {
                    sum += input[y][x] * cos((2.0 * x + 1) * u * Math.PI / (2.0 * n))
                }
                temp[y][u] = sum * alpha(u, n)
            }
        }

        for (u in 0 until n) {
            for (v in 0 until n) {
                var sum = 0.0
                for (y in 0 until n) {
                    sum += temp[y][u] * cos((2.0 * y + 1) * v * Math.PI / (2.0 * n))
                }
                result[v][u] = sum * alpha(v, n)
            }
        }

        return result
    }

    private fun alpha(u: Int, n: Int): Double {
        return if (u == 0) sqrt(1.0 / n) else sqrt(2.0 / n)
    }
}

private fun resize(image: BufferedImage, width: Int, height: Int): BufferedImage {
    val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g2d = resized.createGraphics()
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g2d.drawImage(image, 0, 0, width, height, null)
    g2d.dispose()
    return resized
}

private fun toGrayscale(image: BufferedImage): BufferedImage {
    val grayImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
    val op = ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null)
    op.filter(image, grayImage)
    return grayImage
}
