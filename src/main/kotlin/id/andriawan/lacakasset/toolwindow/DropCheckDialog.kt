package id.andriawan.lacakasset.toolwindow

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import id.andriawan.lacakasset.model.DropScanResult
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer

class DropCheckDialog(
    private val project: Project,
    initialFileName: String
) : DialogWrapper(project, null, false, IdeModalityType.MODELESS) {

    private val tableModel = DropCheckTableModel(project)
    private val table = JBTable(tableModel)

    private val headerPreview = ImagePreviewPanel().apply {
        preferredSize = Dimension(52, 52)
        minimumSize = Dimension(52, 52)
        maximumSize = Dimension(52, 52)
    }
    private val fileNameLabel = JBLabel(initialFileName)

    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout)
    private val errorLabel = JBLabel("", SwingConstants.CENTER)
    private val summaryLabel = JBLabel("")

    init {
        title = "Check Similar Drawable"
        setCancelButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JPanel {
        setupTable()

        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.emptyBottom(8)
            fileNameLabel.toolTipText = fileNameLabel.text
            add(headerPreview)
            add(javax.swing.Box.createHorizontalStrut(JBUI.scale(12)))
            add(fileNameLabel)
        }

        val loadingPanel = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(24, 16)
            add(JProgressBar().apply { isIndeterminate = true }, BorderLayout.NORTH)
            add(JBLabel("Checking project drawables...", SwingConstants.CENTER), BorderLayout.CENTER)
        }

        summaryLabel.foreground = UIUtil.getContextHelpForeground()
        summaryLabel.border = JBUI.Borders.emptyBottom(6)
        val resultsPanel = JPanel(BorderLayout()).apply {
            add(summaryLabel, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
        }

        val emptyPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(24, 16)
            add(JBLabel("No similar drawables found at the current threshold.", SwingConstants.CENTER), BorderLayout.CENTER)
        }

        val errorPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(24, 16)
            errorLabel.foreground = UIUtil.getErrorForeground()
            add(errorLabel, BorderLayout.CENTER)
        }

        contentPanel.apply {
            add(loadingPanel, CARD_LOADING)
            add(resultsPanel, CARD_RESULTS)
            add(emptyPanel, CARD_EMPTY)
            add(errorPanel, CARD_ERROR)
        }
        cardLayout.show(contentPanel, CARD_LOADING)

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val row = table.rowAtPoint(e.point)
                if (row >= 0) {
                    val modelRow = table.convertRowIndexToModel(row)
                    val result = tableModel.getResultAt(modelRow) ?: return
                    if (!project.isDisposed) {
                        FileEditorManager.getInstance(project).openFile(result.fileB.virtualFile, true)
                    }
                }
            }
        })

        return JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(10, 10, 10, 10)
            preferredSize = JBUI.size(720, 440)
            add(headerPanel, BorderLayout.NORTH)
            add(contentPanel, BorderLayout.CENTER)
        }
    }

    private fun setupTable() {
        table.apply {
            setShowGrid(false)
            isStriped = true
            selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            autoCreateRowSorter = true
            rowHeight = JBUI.scale(52)
            toolTipText = "Double-click a match to open it"
            accessibleContext?.accessibleName = "Similar drawable matches"
            setDefaultRenderer(BufferedImage::class.java, ThumbnailCellRenderer())
            setDefaultRenderer(Int::class.javaObjectType, PercentCellRenderer())
        }
        table.columnModel.getColumn(0).apply {
            preferredWidth = JBUI.scale(56)
            maxWidth = JBUI.scale(56)
            minWidth = JBUI.scale(56)
        }
        table.columnModel.getColumn(1).apply {
            preferredWidth = JBUI.scale(88)
            maxWidth = JBUI.scale(120)
        }
    }

    override fun getDimensionServiceKey(): String = "LacakAsset.DropCheckDialog"

    override fun createActions(): Array<Action> = arrayOf(cancelAction)

    fun setState(result: DropScanResult) {
        when (result) {
            is DropScanResult.Success -> {
                headerPreview.setPreview(result.droppedThumbnail, fileNameLabel.text)
                if (result.results.isEmpty()) {
                    cardLayout.show(contentPanel, CARD_EMPTY)
                } else {
                    tableModel.setResults(result.results)
                    summaryLabel.text = "${result.results.size} matches found. Double-click a row to open the asset."
                    cardLayout.show(contentPanel, CARD_RESULTS)
                }
            }

            is DropScanResult.Error -> {
                errorLabel.text = result.message
                cardLayout.show(contentPanel, CARD_ERROR)
            }
        }
    }

    fun resetToLoading(newFileName: String) {
        fileNameLabel.text = newFileName
        fileNameLabel.toolTipText = newFileName
        headerPreview.clearPreview()
        tableModel.setResults(emptyList())
        summaryLabel.text = ""
        errorLabel.text = ""
        cardLayout.show(contentPanel, CARD_LOADING)
    }

    private class ThumbnailCellRenderer : DefaultTableCellRenderer() {
        private var img: BufferedImage? = null

        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): java.awt.Component {
            img = value as? BufferedImage
            background = if (isSelected) table.selectionBackground else table.background
            text = ""
            return this
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val image = img ?: return
            val g2d = g as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val scale = minOf(
                (width - 4).toDouble() / image.width,
                (height - 4).toDouble() / image.height
            ).coerceAtMost(1.0)
            val w = (image.width * scale).toInt()
            val h = (image.height * scale).toInt()
            val x = (width - w) / 2
            val y = (height - h) / 2
            g2d.drawImage(image, x, y, w, h, null)
        }
    }

    private class PercentCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            horizontalAlignment = SwingConstants.RIGHT
            text = "${(value as? Number)?.toInt() ?: 0}%"
            return this
        }
    }

    companion object {
        private const val CARD_LOADING = "loading"
        private const val CARD_RESULTS = "results"
        private const val CARD_EMPTY = "empty"
        private const val CARD_ERROR = "error"
    }
}
