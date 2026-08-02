package id.andriawan.lacakasset.engine

import id.andriawan.lacakasset.hashed
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalSelectionTest {

    @Test
    fun `higher density wins`() {
        val hdpi = hashed("/hdpi/ic.png", densityQualifier = "hdpi")
        val xxhdpi = hashed("/xxhdpi/ic.png", densityQualifier = "xxhdpi")

        assertEquals(xxhdpi, ClusterBuilder.selectCanonical(listOf(hdpi, xxhdpi)))
    }

    @Test
    fun `an unqualified variant outranks every qualified one`() {
        val xxxhdpi = hashed("/xxxhdpi/ic.png", densityQualifier = "xxxhdpi")
        val unqualified = hashed("/drawable/ic.png", densityQualifier = "")

        assertEquals(unqualified, ClusterBuilder.selectCanonical(listOf(xxxhdpi, unqualified)))
    }

    @Test
    fun `reference count decides when density ties`() {
        val rarely = hashed("/a.png")
        val often = hashed("/b.png")

        val canonical = ClusterBuilder.selectCanonical(
            listOf(rarely, often),
            referenceCounts = mapOf("/a.png" to 1, "/b.png" to 7)
        )

        assertEquals(often, canonical)
    }

    @Test
    fun `density outranks reference count`() {
        val lowDensityPopular = hashed("/mdpi/ic.png", densityQualifier = "mdpi")
        val highDensityUnused = hashed("/xxhdpi/ic.png", densityQualifier = "xxhdpi")

        val canonical = ClusterBuilder.selectCanonical(
            listOf(lowDensityPopular, highDensityUnused),
            referenceCounts = mapOf("/mdpi/ic.png" to 99)
        )

        assertEquals(highDensityUnused, canonical)
    }

    @Test
    fun `pixel area decides when density and references tie`() {
        val small = hashed("/a.png", width = 24, height = 24)
        val large = hashed("/b.png", width = 96, height = 96)

        assertEquals(large, ClusterBuilder.selectCanonical(listOf(small, large)))
    }

    @Test
    fun `smaller file wins when dimensions also tie`() {
        val bloated = hashed("/a.png", byteSize = 40_000, width = 96, height = 96)
        val lean = hashed("/b.png", byteSize = 9_000, width = 96, height = 96)

        assertEquals(lean, ClusterBuilder.selectCanonical(listOf(bloated, lean)))
    }

    @Test
    fun `a full tie is broken by path so the result is deterministic`() {
        val z = hashed("/z.png")
        val a = hashed("/a.png")

        assertEquals(a, ClusterBuilder.selectCanonical(listOf(z, a)))
        assertEquals(a, ClusterBuilder.selectCanonical(listOf(a, z)))
    }

    @Test
    fun `repeated selection over a full tie is stable`() {
        val members = listOf(hashed("/c.png"), hashed("/a.png"), hashed("/b.png"))

        val picks = (1..20).map { ClusterBuilder.selectCanonical(members.shuffled()).file.path }

        assertEquals(setOf("/a.png"), picks.toSet())
    }

    @Test
    fun `an override beats every heuristic rule`() {
        val highDensity = hashed("/xxhdpi/ic.png", densityQualifier = "xxhdpi")
        val lowDensity = hashed("/ldpi/ic.png", densityQualifier = "ldpi")

        val canonical = ClusterBuilder.selectCanonical(
            listOf(highDensity, lowDensity),
            canonicalOverrides = setOf("/ldpi/ic.png")
        )

        assertEquals(lowDensity, canonical)
    }

    @Test
    fun `an override naming an absent path falls back to the heuristic`() {
        val a = hashed("/a.png", densityQualifier = "xxhdpi")
        val b = hashed("/b.png", densityQualifier = "hdpi")

        val canonical = ClusterBuilder.selectCanonical(
            listOf(a, b),
            canonicalOverrides = setOf("/somewhere-else.png")
        )

        assertEquals(a, canonical)
    }

    @Test
    fun `an unknown density qualifier ranks below every known one`() {
        val known = hashed("/a.png", densityQualifier = "ldpi")
        val unknown = hashed("/b.png", densityQualifier = "nodpi")

        assertEquals(known, ClusterBuilder.selectCanonical(listOf(known, unknown)))
    }
}
