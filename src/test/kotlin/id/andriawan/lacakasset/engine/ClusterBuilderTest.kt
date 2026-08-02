package id.andriawan.lacakasset.engine

import id.andriawan.lacakasset.hashed
import id.andriawan.lacakasset.lookup
import id.andriawan.lacakasset.pair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterBuilderTest {

    @Test
    fun `a single pair forms one cluster of two`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")

        val clusters = ClusterBuilder.build(listOf(pair(a, b, 0.95)), lookup(a, b), threshold = 0.9)

        assertEquals(1, clusters.size)
        assertEquals(setOf("/a.png", "/b.png"), clusters[0].members.map { it.file.path }.toSet())
    }

    @Test
    fun `a transitive chain merges into one cluster even without a direct link`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")
        val c = hashed("/c.png")

        // A~B and B~C are retained; A~C is not, yet all three describe one asset.
        val clusters = ClusterBuilder.build(
            listOf(pair(a, b, 0.92), pair(b, c, 0.92)),
            lookup(a, b, c),
            threshold = 0.9
        )

        assertEquals(1, clusters.size)
        assertEquals(3, clusters[0].memberCount)
    }

    @Test
    fun `unlinked groups stay separate`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")
        val c = hashed("/c.png")
        val d = hashed("/d.png")

        val clusters = ClusterBuilder.build(
            listOf(pair(a, b, 0.95), pair(c, d, 0.95)),
            lookup(a, b, c, d),
            threshold = 0.9
        )

        assertEquals(2, clusters.size)
        assertTrue(clusters.all { it.memberCount == 2 })
    }

    @Test
    fun `a drawable belongs to exactly one cluster`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")
        val c = hashed("/c.png")

        val clusters = ClusterBuilder.build(
            listOf(pair(a, b, 0.95), pair(b, c, 0.95), pair(a, c, 0.95)),
            lookup(a, b, c),
            threshold = 0.9
        )

        val allPaths = clusters.flatMap { cluster -> cluster.members.map { it.file.path } }
        assertEquals(allPaths.size, allPaths.toSet().size)
    }

    @Test
    fun `pairs below the threshold do not link`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")

        val clusters = ClusterBuilder.build(listOf(pair(a, b, 0.78)), lookup(a, b), threshold = 0.9)

        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `lowering the threshold merges previously separate clusters`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")
        val c = hashed("/c.png")
        val pairs = listOf(pair(a, b, 0.95), pair(b, c, 0.80))
        val drawables = lookup(a, b, c)

        val strict = ClusterBuilder.build(pairs, drawables, threshold = 0.9)
        val loose = ClusterBuilder.build(pairs, drawables, threshold = 0.75)

        assertEquals(2, strict.single().memberCount)
        assertEquals(3, loose.single().memberCount)
    }

    @Test
    fun `a pair naming an unknown path is skipped rather than failing`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")

        // Only /a.png is known: the file behind /b.png was deleted after the scan.
        val clusters = ClusterBuilder.build(listOf(pair(a, b, 0.95)), lookup(a), threshold = 0.9)

        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `a uniform cluster reports one similarity figure`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")
        val c = hashed("/c.png")

        val cluster = ClusterBuilder.build(
            listOf(pair(a, b, 1.0), pair(b, c, 1.0)),
            lookup(a, b, c),
            threshold = 0.9
        ).single()

        assertEquals(100, cluster.strongestPercent)
        assertEquals(100, cluster.weakestPercent)
        assertEquals("100%", cluster.similarityRangeLabel)
    }

    @Test
    fun `a chained cluster exposes its weakest link`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")
        val c = hashed("/c.png")

        val cluster = ClusterBuilder.build(
            listOf(pair(a, b, 0.96), pair(b, c, 0.78)),
            lookup(a, b, c),
            threshold = 0.75
        ).single()

        assertEquals(96, cluster.strongestPercent)
        assertEquals(78, cluster.weakestPercent)
        assertEquals("96-78%", cluster.similarityRangeLabel)
    }

    @Test
    fun `a two-member cluster reports its single score as both bounds`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")

        val cluster = ClusterBuilder.build(listOf(pair(a, b, 0.93)), lookup(a, b), threshold = 0.9).single()

        assertEquals(93, cluster.strongestPercent)
        assertEquals(93, cluster.weakestPercent)
    }

    @Test
    fun `clusters are ordered by descending estimated saving`() {
        val small1 = hashed("/small1.png", byteSize = 100)
        val small2 = hashed("/small2.png", byteSize = 100)
        val big1 = hashed("/big1.png", byteSize = 90_000)
        val big2 = hashed("/big2.png", byteSize = 90_000)

        val clusters = ClusterBuilder.build(
            listOf(pair(small1, small2, 0.95), pair(big1, big2, 0.95)),
            lookup(small1, small2, big1, big2),
            threshold = 0.9
        )

        assertTrue(clusters[0].estimatedSaving > clusters[1].estimatedSaving)
    }

    @Test
    fun `an empty pair list produces no clusters`() {
        assertTrue(ClusterBuilder.build(emptyList(), emptyMap(), threshold = 0.9).isEmpty())
    }

    @Test
    fun `clustering a large chain terminates and keeps every member`() {
        // Guards the union-find's path compression against a pathological chain.
        val drawables = (0 until 500).map { hashed("/icon$it.png") }
        val pairs = drawables.zipWithNext { a, b -> pair(a, b, 0.95) }

        val cluster = ClusterBuilder.build(
            pairs,
            drawables.associateBy { it.file.path },
            threshold = 0.9
        ).single()

        assertEquals(500, cluster.memberCount)
    }

    @Test
    fun `members are ordered deterministically regardless of pair order`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")
        val c = hashed("/c.png")
        val drawables = lookup(a, b, c)

        val one = ClusterBuilder.build(listOf(pair(a, b, 0.95), pair(b, c, 0.95)), drawables, 0.9).single()
        val other = ClusterBuilder.build(listOf(pair(c, b, 0.95), pair(b, a, 0.95)), drawables, 0.9).single()

        assertEquals(one.members.map { it.file.path }, other.members.map { it.file.path })
    }

    @Test
    fun `a cluster of one format is not mixed`() {
        val a = hashed("/a.png")
        val b = hashed("/b.png")

        val cluster = ClusterBuilder.build(listOf(pair(a, b, 0.95)), lookup(a, b), 0.9).single()

        assertFalse(cluster.isMixedFormat)
    }
}
