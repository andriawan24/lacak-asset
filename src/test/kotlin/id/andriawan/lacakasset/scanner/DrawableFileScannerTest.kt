package id.andriawan.lacakasset.scanner

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import id.andriawan.lacakasset.model.DrawableFormat
import id.andriawan.lacakasset.model.ResourceOrigin

/**
 * Discovery is the one part of the pipeline that cannot be exercised without a project and a
 * virtual file system, so it gets a platform fixture; everything else is covered by plain
 * unit tests.
 */
class DrawableFileScannerTest : BasePlatformTestCase() {

    private val scanner = DrawableFileScanner()

    private fun scan(excluded: Set<String> = emptySet()) =
        scanner.findDrawableFiles(project, excluded)

    private fun paths(excluded: Set<String> = emptySet()) =
        scan(excluded).map { it.path.substringAfterLast("/src/") }.toSet()

    fun testFindsDrawablesUnderAndroidResources() {
        myFixture.addFileToProject("app/src/main/res/drawable/ic_home.png", "x")
        myFixture.addFileToProject("app/src/main/res/drawable-xxhdpi/ic_home.png", "x")

        val found = scan().map { it.virtualFile.name }

        assertContainsElements(found, "ic_home.png")
        assertEquals(2, found.size)
    }

    fun testFindsDrawablesUnderComposeResources() {
        myFixture.addFileToProject("shared/src/commonMain/composeResources/drawable/ic_star.png", "x")

        val file = scan().single()

        assertEquals("ic_star.png", file.virtualFile.name)
        assertEquals(ResourceOrigin.COMPOSE_RESOURCES, file.resourceOrigin)
    }

    fun testIgnoresDrawableDirectoriesOutsideAResourceRoot() {
        // A `drawable` directory not parented by `res` or `composeResources` is not a
        // resource directory, so its contents are not project drawables.
        myFixture.addFileToProject("app/src/main/assets/drawable/not_a_resource.png", "x")

        assertEmpty(scan())
    }

    fun testIgnoresNonDrawableDirectories() {
        myFixture.addFileToProject("app/src/main/res/layout/activity_main.xml", "<x/>")
        myFixture.addFileToProject("app/src/main/res/values/colors.xml", "<x/>")

        assertEmpty(scan())
    }

    fun testIgnoresUnsupportedExtensions() {
        myFixture.addFileToProject("app/src/main/res/drawable/animation.gif", "x")
        myFixture.addFileToProject("app/src/main/res/drawable/notes.txt", "x")

        assertEmpty(scan())
    }

    fun testSkipsNinePatchFiles() {
        myFixture.addFileToProject("app/src/main/res/drawable/button.9.png", "x")
        myFixture.addFileToProject("app/src/main/res/drawable/plain.png", "x")

        assertEquals(listOf("plain.png"), scan().map { it.virtualFile.name })
    }

    fun testExtractsDensityQualifier() {
        myFixture.addFileToProject("app/src/main/res/drawable-xxhdpi/ic_home.png", "x")
        myFixture.addFileToProject("app/src/main/res/drawable/ic_other.png", "x")

        val byName = scan().associateBy { it.resourceName }

        assertEquals("xxhdpi", byName.getValue("ic_home").densityQualifier)
        assertEquals("", byName.getValue("ic_other").densityQualifier)
    }

    fun testExtractsModuleAndSourceSet() {
        myFixture.addFileToProject("core/ui/src/debug/res/drawable/ic_home.png", "x")

        val file = scan().single()

        // The fixture's in-memory project root is itself a directory named `src`, which the
        // module path picks up as a prefix. A real project root does not, so the assertion
        // checks the module suffix rather than encoding the fixture's own layout.
        assertTrue("expected module path ending in :core:ui, was ${file.modulePath}",
            file.modulePath.endsWith(":core:ui"))
        assertEquals("debug", file.sourceSet)
    }

    fun testCapturesPathAndByteSize() {
        myFixture.addFileToProject("app/src/main/res/drawable/ic_home.png", "12345")

        val file = scan().single()

        assertEquals(file.virtualFile.path, file.path)
        assertEquals(5L, file.byteSize)
    }

    fun testRecognisesEverySupportedFormat() {
        myFixture.addFileToProject("app/src/main/res/drawable/a.png", "x")
        myFixture.addFileToProject("app/src/main/res/drawable/b.jpg", "x")
        myFixture.addFileToProject("app/src/main/res/drawable/c.webp", "x")
        myFixture.addFileToProject("app/src/main/res/drawable/d.svg", "<svg/>")
        myFixture.addFileToProject("app/src/main/res/drawable/e.xml", "<vector/>")

        val formats = scan().map { it.format }.toSet()

        assertEquals(
            setOf(
                DrawableFormat.PNG,
                DrawableFormat.JPG,
                DrawableFormat.WEBP,
                DrawableFormat.SVG,
                DrawableFormat.ANDROID_VECTOR
            ),
            formats
        )
    }

    fun testHonoursUserSuppliedExclusions() {
        myFixture.addFileToProject("app/src/main/res/drawable/keep.png", "x")
        myFixture.addFileToProject("sampledata/src/main/res/drawable/skip.png", "x")

        assertEquals(listOf("keep.png"), scan(setOf("sampledata")).map { it.virtualFile.name })
    }

    fun testSkipsBuildDirectoriesWithoutBeingAsked() {
        myFixture.addFileToProject("app/build/generated/res/drawable/generated.png", "x")
        myFixture.addFileToProject("app/src/main/res/drawable/real.png", "x")

        assertEquals(listOf("real.png"), scan().map { it.virtualFile.name })
    }

    fun testReturnsEmptyForAProjectWithoutDrawables() {
        myFixture.addFileToProject("app/src/main/kotlin/Main.kt", "fun main() {}")

        assertEmpty(scan())
    }

    fun testIsInDrawableDirectoryAcceptsBothResourceRoots() {
        val androidRes = myFixture.addFileToProject("app/src/main/res/drawable/a.png", "x").virtualFile
        val compose = myFixture
            .addFileToProject("shared/src/commonMain/composeResources/drawable-hdpi/b.png", "x").virtualFile
        val neither = myFixture.addFileToProject("app/src/main/res/layout/c.xml", "<x/>").virtualFile

        assertTrue(DrawableFileScanner.isInDrawableDirectory(androidRes))
        assertTrue(DrawableFileScanner.isInDrawableDirectory(compose))
        assertFalse(DrawableFileScanner.isInDrawableDirectory(neither))
    }
}
