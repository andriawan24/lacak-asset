package id.andriawan.lacakasset.engine

import id.andriawan.lacakasset.hashed
import id.andriawan.lacakasset.hashedAtDistance
import id.andriawan.lacakasset.model.DrawableFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityEngineTest {

    private val engine = SimilarityEngine()

    /** Identical to a zero-hash drawable, so every comparison against it scores 1.0. */
    private fun reference(path: String, densityQualifier: String = "", modulePath: String = ":app") =
        hashedAtDistance(path, dHashDistance = 0, pHashDistance = 0, densityQualifier = densityQualifier, modulePath = modulePath)

    @Test
    fun `identical hashes score one hundred percent`() {
        val pairs = engine.findRetainedPairs(listOf(reference("/a.png"), reference("/b.png")))

        assertEquals(1, pairs.size)
        assertEquals(100, pairs[0].similarityPercent)
    }

    @Test
    fun `a pair below the retention floor is discarded`() {
        // pHash distance 90 of 255 leaves a similarity near 0.65, under the 0.70 floor.
        val a = reference("/a.png")
        val b = hashedAtDistance("/b.png", dHashDistance = 0, pHashDistance = 90)

        assertTrue(engine.findRetainedPairs(listOf(a, b)).isEmpty())
    }

    @Test
    fun `a pair between the floor and a typical threshold is still retained`() {
        // pHash distance 38 of 255 gives roughly 0.85: below a 0.90 threshold, above the floor.
        val a = reference("/a.png")
        val b = hashedAtDistance("/b.png", dHashDistance = 0, pHashDistance = 38)

        val pairs = engine.findRetainedPairs(listOf(a, b))

        assertEquals(1, pairs.size)
        assertTrue(pairs[0].normalizedSimilarity >= SimilarityEngine.RETENTION_FLOOR)
        assertTrue(pairs[0].normalizedSimilarity < 0.90)
    }

    @Test
    fun `the dHash pre-filter rejects a pair before the perceptual hash is consulted`() {
        // dHash distance 40 of 64 is far below the 0.80 pre-filter, despite identical pHashes.
        val a = reference("/a.png")
        val b = hashedAtDistance("/b.png", dHashDistance = 40, pHashDistance = 0)

        assertTrue(engine.findRetainedPairs(listOf(a, b)).isEmpty())
    }

    @Test
    fun `equal structural fingerprints report one hundred percent without perceptual agreement`() {
        val a = hashed("/a.xml", format = DrawableFormat.ANDROID_VECTOR, structuralFingerprint = "same")
        val b = hashed("/b.xml", format = DrawableFormat.ANDROID_VECTOR, structuralFingerprint = "same")

        val pairs = engine.findRetainedPairs(listOf(a, b))

        assertEquals(100, pairs.single().similarityPercent)
    }

    @Test
    fun `differing fingerprints fall through to perceptual comparison`() {
        val a = hashedAtDistance("/a.xml", 0, 0, format = DrawableFormat.ANDROID_VECTOR)
            .copy(structuralFingerprint = "one")
        val b = hashedAtDistance("/b.xml", 0, 90, format = DrawableFormat.ANDROID_VECTOR)
            .copy(structuralFingerprint = "two")

        // Perceptual comparison then rejects them, proving the short-circuit did not fire.
        assertTrue(engine.findRetainedPairs(listOf(a, b)).isEmpty())
    }

    @Test
    fun `results are ordered by descending similarity`() {
        val base = reference("/base.png")
        val near = hashedAtDistance("/near.png", 0, 5)
        val far = hashedAtDistance("/far.png", 0, 40)

        val pairs = engine.findRetainedPairs(listOf(base, near, far))

        assertEquals(
            pairs.map { it.normalizedSimilarity },
            pairs.map { it.normalizedSimilarity }.sortedDescending()
        )
    }

    @Test
    fun `only the highest density variant of a resource participates`() {
        val hdpi = reference("/res/drawable-hdpi/ic_home.png", densityQualifier = "hdpi")
        val xhdpi = reference("/res/drawable-xhdpi/ic_home.png", densityQualifier = "xhdpi")
        val xxhdpi = reference("/res/drawable-xxhdpi/ic_home.png", densityQualifier = "xxhdpi")

        val pairs = engine.findRetainedPairs(listOf(hdpi, xhdpi, xxhdpi))

        // All three are the same resource in one module, so no pair should be reported.
        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `an unqualified variant is preferred over a qualified one`() {
        val unqualified = reference("/res/drawable/ic_home.png", densityQualifier = "")
        val xxxhdpi = reference("/res/drawable-xxxhdpi/ic_home.png", densityQualifier = "xxxhdpi")
        val other = reference("/res/drawable/ic_other.png").copy(
            file = reference("/res/drawable/ic_other.png").file.copy(resourceName = "ic_other")
        )

        val pairs = engine.findRetainedPairs(listOf(unqualified, xxxhdpi, other))

        assertEquals("/res/drawable/ic_home.png", pairs.single().fileA.path)
    }

    @Test
    fun `the same resource name in different modules is still compared`() {
        val app = reference("/app/res/drawable/ic_home.png", modulePath = ":app")
        val core = reference("/core/res/drawable/ic_home.png", modulePath = ":core:ui")

        assertEquals(1, engine.findRetainedPairs(listOf(app, core)).size)
    }

    @Test
    fun `a target is never reported as a match against itself`() {
        val target = reference("/a.png")
        val other = reference("/b.png")

        val pairs = engine.findRetainedPairsForTarget(target, listOf(target, other))

        assertEquals(1, pairs.size)
        assertEquals("/b.png", pairs.single().fileB.path)
    }

    @Test
    fun `a target with no candidates returns nothing`() {
        assertTrue(engine.findRetainedPairsForTarget(reference("/a.png"), emptyList()).isEmpty())
    }

    @Test
    fun `retention is capped at the documented limit`() {
        // 400 mutually identical drawables yield 79800 pairs, above the 50000 cap.
        val drawables = (0 until 400).map { reference("/icon$it.png") }

        val pairs = engine.findRetainedPairs(drawables)

        assertEquals(SimilarityEngine.RETENTION_CAP, pairs.size)
    }

    @Test
    fun `the cap keeps the highest scoring pairs`() {
        val drawables = (0 until 400).map { reference("/icon$it.png") }

        val pairs = engine.findRetainedPairs(drawables)

        assertEquals(
            pairs.map { it.normalizedSimilarity },
            pairs.map { it.normalizedSimilarity }.sortedDescending()
        )
    }

    @Test
    fun `a single drawable produces no pairs`() {
        assertTrue(engine.findRetainedPairs(listOf(reference("/only.png"))).isEmpty())
    }

    @Test
    fun `an empty input produces no pairs`() {
        assertTrue(engine.findRetainedPairs(emptyList()).isEmpty())
    }
}
