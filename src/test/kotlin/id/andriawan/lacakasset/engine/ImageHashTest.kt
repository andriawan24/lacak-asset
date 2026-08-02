package id.andriawan.lacakasset.engine

import id.andriawan.lacakasset.hashWithSetBits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class ImageHashTest {

    private fun solid(color: Color, size: Int = 128): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, size, size)
        g.dispose()
        return image
    }

    private fun halfSplit(size: Int = 128): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.BLACK
        g.fillRect(0, 0, size / 2, size)
        g.color = Color.WHITE
        g.fillRect(size / 2, 0, size / 2, size)
        g.dispose()
        return image
    }

    @Test
    fun `dHash is sixty four bits wide`() {
        assertEquals(64, DHash.compute(solid(Color.RED)).bitLength)
    }

    @Test
    fun `pHash is two hundred and fifty five bits wide`() {
        assertEquals(255, PHash.compute(solid(Color.RED)).bitLength)
    }

    @Test
    fun `identical images produce identical hashes`() {
        assertEquals(DHash.compute(solid(Color.BLUE)), DHash.compute(solid(Color.BLUE)))
        assertEquals(PHash.compute(halfSplit()), PHash.compute(halfSplit()))
    }

    @Test
    fun `hashing is deterministic across repeated calls`() {
        val image = halfSplit()

        assertEquals(PHash.compute(image), PHash.compute(image))
    }

    @Test
    fun `structurally different images produce different perceptual hashes`() {
        assertNotEquals(PHash.compute(solid(Color.WHITE)), PHash.compute(halfSplit()))
    }

    @Test
    fun `a zero Hamming distance means full similarity`() {
        val hash = hashWithSetBits(0, 64)

        assertEquals(0, hash.hammingDistance(hash))
        assertEquals(1.0, hash.normalizedSimilarity(hash), 0.0)
    }

    @Test
    fun `Hamming distance counts differing bits`() {
        val none = hashWithSetBits(0, 64)
        val twelve = hashWithSetBits(12, 64)

        assertEquals(12, none.hammingDistance(twelve))
    }

    @Test
    fun `Hamming distance is symmetric`() {
        val a = hashWithSetBits(7, 64)
        val b = hashWithSetBits(20, 64)

        assertEquals(a.hammingDistance(b), b.hammingDistance(a))
    }

    @Test
    fun `normalized similarity divides by the bit length`() {
        val none = hashWithSetBits(0, 64)
        val sixteen = hashWithSetBits(16, 64)

        assertEquals(0.75, none.normalizedSimilarity(sixteen), 1e-9)
    }

    @Test
    fun `a fully inverted hash has zero similarity`() {
        val none = hashWithSetBits(0, 64)
        val all = hashWithSetBits(64, 64)

        assertEquals(0.0, none.normalizedSimilarity(all), 1e-9)
    }

    @Test
    fun `hashes spanning several longs compare correctly`() {
        val none = hashWithSetBits(0, 255)
        val across = hashWithSetBits(100, 255)

        assertEquals(100, none.hammingDistance(across))
    }

    @Test
    fun `equal hashes agree on equality and hash code`() {
        val one = hashWithSetBits(9, 64)
        val other = hashWithSetBits(9, 64)

        assertEquals(one, other)
        assertEquals(one.hashCode(), other.hashCode())
    }

    @Test
    fun `similarity never leaves the unit interval`() {
        val a = hashWithSetBits(31, 255)
        val b = hashWithSetBits(200, 255)

        val similarity = a.normalizedSimilarity(b)

        assertTrue(similarity in 0.0..1.0)
    }
}
