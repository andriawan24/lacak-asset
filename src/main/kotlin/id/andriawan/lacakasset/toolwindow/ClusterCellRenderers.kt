package id.andriawan.lacakasset.toolwindow

import id.andriawan.lacakasset.util.formatFileSize
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer

/** Draws a cached thumbnail centred in its cell, scaled down but never up. */
class ThumbnailCellRenderer : DefaultTableCellRenderer() {
    private var image: BufferedImage? = null

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        image = value as? BufferedImage
        background = if (isSelected) table.selectionBackground else table.background
        text = ""
        return this
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val source = image ?: return
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        val scale = minOf(
            (width - 4).toDouble() / source.width,
            (height - 4).toDouble() / source.height
        ).coerceAtMost(1.0)
        val w = (source.width * scale).toInt()
        val h = (source.height * scale).toInt()
        g2d.drawImage(source, (width - w) / 2, (height - h) / 2, w, h, null)
    }
}

class PercentCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        horizontalAlignment = SwingConstants.RIGHT
        text = "${(value as? Number)?.toInt() ?: 0}%"
        return this
    }
}

class FileSizeCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        horizontalAlignment = SwingConstants.RIGHT
        text = formatFileSize((value as? Number)?.toLong() ?: 0L)
        return this
    }
}

class CountCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        horizontalAlignment = SwingConstants.RIGHT
        val count = (value as? Number)?.toInt() ?: 0
        text = if (count == 1) "1 copy" else "$count copies"
        return this
    }
}
