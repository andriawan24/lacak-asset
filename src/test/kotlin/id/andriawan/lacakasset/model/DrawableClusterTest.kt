package id.andriawan.lacakasset.model

import id.andriawan.lacakasset.hashed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawableClusterTest {

    private fun cluster(
        members: List<HashedDrawable>,
        canonical: HashedDrawable = members.first(),
        strongest: Double = 1.0,
        weakest: Double = 1.0
    ) = DrawableCluster(members, canonical, strongest, weakest)

    @Test
    fun `mixed raster and vector is flagged`() {
        val png = hashed("/ic_close.png", format = DrawableFormat.PNG)
        val vector = hashed("/ic_close.xml", format = DrawableFormat.ANDROID_VECTOR)

        assertTrue(cluster(listOf(png, vector)).isMixedFormat)
    }

    @Test
    fun `two raster formats also count as mixed`() {
        val png = hashed("/a.png", format = DrawableFormat.PNG)
        val jpg = hashed("/a.jpg", format = DrawableFormat.JPG)

        assertTrue(cluster(listOf(png, jpg)).isMixedFormat)
    }

    @Test
    fun `one format is not mixed`() {
        val members = listOf(hashed("/a.png"), hashed("/b.png"), hashed("/c.png"))

        assertFalse(cluster(members).isMixedFormat)
    }

    @Test
    fun `saving totals every member except the canonical one`() {
        val canonical = hashed("/keep.png", byteSize = 40_000)
        val first = hashed("/drop1.png", byteSize = 12_000)
        val second = hashed("/drop2.png", byteSize = 9_000)

        val result = cluster(listOf(canonical, first, second), canonical = canonical)

        assertEquals(21_000L, result.estimatedSaving)
    }

    @Test
    fun `changing the canonical member recomputes the saving`() {
        val big = hashed("/big.png", byteSize = 40_000)
        val small = hashed("/small.png", byteSize = 12_000)
        val original = cluster(listOf(big, small), canonical = big)

        val flipped = original.withCanonical("/small.png")

        assertEquals(12_000L, original.estimatedSaving)
        assertEquals(40_000L, flipped.estimatedSaving)
    }

    @Test
    fun `redundant members exclude the canonical one`() {
        val canonical = hashed("/keep.png")
        val other = hashed("/drop.png")

        val redundant = cluster(listOf(canonical, other), canonical = canonical).redundantMembers

        assertEquals(listOf("/drop.png"), redundant.map { it.file.path })
    }

    @Test
    fun `naming an absent path leaves the canonical member unchanged`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")
        val original = cluster(listOf(a, b), canonical = a)

        assertEquals(a, original.withCanonical("/not-here.png").canonical)
    }

    @Test
    fun `contains matches by path`() {
        val result = cluster(listOf(hashed("/a.png"), hashed("/b.png")))

        assertTrue(result.contains("/a.png"))
        assertFalse(result.contains("/c.png"))
    }

    @Test
    fun `a two-member cluster of equal size still reports the redundant copy`() {
        val a = hashed("/a.png", byteSize = 5_000)
        val b = hashed("/b.png", byteSize = 5_000)

        assertEquals(5_000L, cluster(listOf(a, b), canonical = a).estimatedSaving)
    }
}
