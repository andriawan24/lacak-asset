package id.andriawan.lacakasset.toolwindow

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
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

    init {
        title = "Similarity Check"
        setCancelButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JPanel {
        setupTable()

        // Header row: small thumbnail + filename
        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(headerPreview)
            add(javax.swing.Box.createHorizontalStrut(8))
            add(fileNameLabel)
        }

        // Loading card
        val loadingPanel = JPanel(BorderLayout(0, 8)).apply {
            add(JProgressBar().apply { isIndeterminate = true }, BorderLayout.NORTH)
            add(JBLabel("Analysing…", SwingConstants.CENTER), BorderLayout.CENTER)
        }

        // Results card
        val tableScroll = JBScrollPane(table)

        // Empty card
        val emptyPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("No similar drawables found.", SwingConstants.CENTER), BorderLayout.CENTER)
        }

        // Error card
        val errorPanel = JPanel(BorderLayout()).apply {
            add(errorLabel, BorderLayout.CENTER)
        }

        contentPanel.apply {
            add(loadingPanel, CARD_LOADING)
            add(tableScroll, CARD_RESULTS)
            add(emptyPanel, CARD_EMPTY)
            add(errorPanel, CARD_ERROR)
        }
        cardLayout.show(contentPanel, CARD_LOADING)

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                if (row >= 0) {
                    val result = tableModel.getResultAt(row) ?: return
                    if (!project.isDisposed) {
                        FileEditorManager.getInstance(project).openFile(result.fileB.virtualFile, true)
                    }
                }
            }
        })

        return JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            preferredSize = Dimension(640, 400)
            add(headerPanel, BorderLayout.NORTH)
            add(contentPanel, BorderLayout.CENTER)
        }
    }

    private fun setupTable() {
        table.apply {
            setShowGrid(false)
            isStriped = true
            selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            rowHeight = 52
            setDefaultRenderer(BufferedImage::class.java, ThumbnailCellRenderer())
        }
        // Fix thumbnail column width
        table.columnModel.getColumn(0).apply {
            preferredWidth = 56
            maxWidth = 56
            minWidth = 56
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
        headerPreview.clearPreview()
        tableModel.setResults(emptyList())
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

    companion object {
        private const val CARD_LOADING = "loading"
        private const val CARD_RESULTS = "results"
        private const val CARD_EMPTY = "empty"
        private const val CARD_ERROR = "error"
    }
}
