package id.andriawan.lacakasset.toolwindow

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.SwingConstants

class ImagePreviewPanel : JPanel(BorderLayout()) {

    private val canvas = ImageCanvas()
    private val nameLabel = JBLabel("Select a result to preview", SwingConstants.CENTER).apply {
        border = JBUI.Borders.empty(4, 6)
        foreground = UIUtil.getContextHelpForeground()
    }

    init {
        preferredSize = JBUI.size(220, 180)
        minimumSize = JBUI.size(80, 80)
        background = UIUtil.getPanelBackground()
        accessibleContext?.accessibleName = "Drawable preview"
        add(canvas, BorderLayout.CENTER)
        add(nameLabel, BorderLayout.SOUTH)
    }

    fun setPreview(img: BufferedImage?, fileName: String) {
        canvas.image = img
        if (img == null) {
            nameLabel.text = "Select a result to preview"
            nameLabel.foreground = UIUtil.getContextHelpForeground()
        } else {
            nameLabel.text = fileName
            nameLabel.toolTipText = fileName
            nameLabel.foreground = UIUtil.getLabelForeground()
        }
        canvas.repaint()
    }

    fun clearPreview() = setPreview(null, "")

    private class ImageCanvas : JPanel() {
        var image: BufferedImage? = null
            set(value) {
                field = value
                repaint()
            }

        init {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            minimumSize = Dimension(1, 1)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val img = image ?: return
            if (width <= 0 || height <= 0) return

            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val scale = minOf(
                width.toDouble() / img.width,
                height.toDouble() / img.height,
                4.0
            ).coerceAtLeast(0.01)
            val w = (img.width * scale).toInt().coerceAtLeast(1)
            val h = (img.height * scale).toInt().coerceAtLeast(1)
            val x = (width - w) / 2
            val y = (height - h) / 2

            drawCheckerboard(g2, x, y, w, h)
            g2.drawImage(img, x, y, w, h, null)
        }

        private fun drawCheckerboard(g2: Graphics2D, x: Int, y: Int, w: Int, h: Int) {
            val checkSize = JBUI.scale(8)
            val light = if (JBColor.isBright()) Gray._240 else Gray._80
            val dark = if (JBColor.isBright()) Gray._210 else Gray._55
            val oldClip = g2.clip

            g2.clip = Rectangle(x, y, w, h)
            for (row in 0..(h / checkSize)) {
                for (col in 0..(w / checkSize)) {
                    g2.color = if ((row + col) % 2 == 0) light else dark
                    g2.fillRect(x + col * checkSize, y + row * checkSize, checkSize, checkSize)
                }
            }
            g2.clip = oldClip
        }
    }
}
