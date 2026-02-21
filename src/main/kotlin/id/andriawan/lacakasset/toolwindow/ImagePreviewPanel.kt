package id.andriawan.lacakasset.toolwindow

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.UIManager

class ImagePreviewPanel : JPanel() {

    private var image: BufferedImage? = null
    private var label: String = ""

    init {
        preferredSize = Dimension(200, 200)
        minimumSize = Dimension(100, 100)
    }

    fun setPreview(img: BufferedImage?, fileName: String) {
        image = img
        label = fileName
        repaint()
    }

    fun clearPreview() {
        image = null
        label = ""
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val img = image
        if (img != null) {
            val labelHeight = 24
            val availableHeight = height - labelHeight

            val scale = minOf(
                width.toDouble() / img.width,
                availableHeight.toDouble() / img.height,
                4.0 // Max 4x zoom for small icons
            )
            val drawWidth = (img.width * scale).toInt()
            val drawHeight = (img.height * scale).toInt()
            val x = (width - drawWidth) / 2
            val y = (availableHeight - drawHeight) / 2

            // Draw a checkerboard pattern for transparency
            drawCheckerboard(g2, x, y, drawWidth, drawHeight)

            g2.drawImage(img, x, y, drawWidth, drawHeight, null)

            // Draw label
            g2.color = UIManager.getColor("Label.foreground") ?: JBColor.BLACK
            g2.font = UIManager.getFont("Label.font") ?: Font("Dialog", Font.PLAIN, 12)
            val fm = g2.fontMetrics
            val labelText = if (fm.stringWidth(label) > width - 8) {
                truncateWithEllipsis(label, fm, width - 8)
            } else {
                label
            }
            g2.drawString(labelText, (width - fm.stringWidth(labelText)) / 2, height - 6)
        } else {
            g2.color = UIManager.getColor("Label.disabledForeground") ?: JBColor.GRAY
            g2.font = UIManager.getFont("Label.font") ?: Font("Dialog", Font.PLAIN, 12)
            val msg = "No image selected"
            val fm = g2.fontMetrics
            g2.drawString(msg, (width - fm.stringWidth(msg)) / 2, height / 2)
        }
    }

    private fun drawCheckerboard(g2: Graphics2D, x: Int, y: Int, w: Int, h: Int) {
        val checkSize = 8
        val light = Gray._204
        val dark = Gray._153

        g2.clip = Rectangle(x, y, w, h)
        for (row in 0..(h / checkSize)) {
            for (col in 0..(w / checkSize)) {
                g2.color = if ((row + col) % 2 == 0) light else dark
                g2.fillRect(x + col * checkSize, y + row * checkSize, checkSize, checkSize)
            }
        }
        g2.clip = null
    }

    private fun truncateWithEllipsis(text: String, fm: FontMetrics, maxWidth: Int): String {
        val ellipsis = "..."
        val ellipsisWidth = fm.stringWidth(ellipsis)
        if (maxWidth <= ellipsisWidth) return ellipsis

        for (i in text.length downTo 1) {
            if (fm.stringWidth(text.substring(0, i)) + ellipsisWidth <= maxWidth) {
                return text.substring(0, i) + ellipsis
            }
        }
        return ellipsis
    }
}
