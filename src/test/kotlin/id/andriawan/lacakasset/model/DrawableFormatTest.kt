package id.andriawan.lacakasset.model

import id.andriawan.lacakasset.drawableFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawableFormatTest {

    @Test
    fun `each supported extension maps to its format`() {
        assertEquals(DrawableFormat.PNG, DrawableFormat.fromExtension("png"))
        assertEquals(DrawableFormat.JPG, DrawableFormat.fromExtension("jpg"))
        assertEquals(DrawableFormat.JPG, DrawableFormat.fromExtension("jpeg"))
        assertEquals(DrawableFormat.WEBP, DrawableFormat.fromExtension("webp"))
        assertEquals(DrawableFormat.SVG, DrawableFormat.fromExtension("svg"))
        assertEquals(DrawableFormat.ANDROID_VECTOR, DrawableFormat.fromExtension("xml"))
    }

    @Test
    fun `extension matching ignores case`() {
        assertEquals(DrawableFormat.PNG, DrawableFormat.fromExtension("PNG"))
        assertEquals(DrawableFormat.JPG, DrawableFormat.fromExtension("JPeG"))
    }

    @Test
    fun `an unsupported extension yields null`() {
        assertNull(DrawableFormat.fromExtension("gif"))
        assertNull(DrawableFormat.fromExtension("kt"))
        assertNull(DrawableFormat.fromExtension(""))
    }

    @Test
    fun `a leading dot is not stripped, so callers must pass a bare extension`() {
        assertNull(DrawableFormat.fromExtension(".png"))
    }

    @Test
    fun `file name combines the resource name with the format's primary extension`() {
        val file = drawableFile("/res/drawable/ic_home.png", format = DrawableFormat.PNG)

        assertEquals("ic_home.png", file.fileName)
    }

    @Test
    fun `jpeg files report the primary jpg extension`() {
        val file = drawableFile("/res/drawable/photo.jpeg", format = DrawableFormat.JPG)

        assertEquals("photo.jpg", file.fileName)
    }

    @Test
    fun `source description combines module, source set, and origin`() {
        val file = DrawableFile(
            virtualFile = drawableFile("/x.png").virtualFile,
            path = "/x.png",
            byteSize = 1,
            resourceName = "x",
            format = DrawableFormat.PNG,
            densityQualifier = "",
            modulePath = ":shared",
            sourceSet = "commonMain",
            resourceOrigin = ResourceOrigin.COMPOSE_RESOURCES
        )

        assertEquals(":shared (commonMain, Compose Resources)", file.sourceDescription)
    }
}
