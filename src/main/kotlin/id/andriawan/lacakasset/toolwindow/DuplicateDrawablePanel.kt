package id.andriawan.lacakasset.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import id.andriawan.lacakasset.model.SimilarityResult
import id.andriawan.lacakasset.service.DrawableHashCacheService
import id.andriawan.lacakasset.service.DrawableScanService
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

class DuplicateDrawablePanel(
    private val project: Project
) : JPanel(BorderLayout()), Disposable {

    private val tableModel = SimilarityTableModel()
    private val table = JBTable(tableModel)
    private val leftPreview = ImagePreviewPanel()
    private val rightPreview = ImagePreviewPanel()
    private val statusLabel = JBLabel("", SwingConstants.CENTER)

    init {
        setupToolbar()
        setupContent()
        setupListeners()
        registerWithScanService()
    }

    private fun setupToolbar() {
        val actionGroup = DefaultActionGroup().apply {
            val scanAction = ActionManager.getInstance().getAction("LacakAsset.Scan")
            if (scanAction != null) add(scanAction)
        }

        val toolbar = ActionManager.getInstance().createActionToolbar("LacakAssetToolbar", actionGroup, true)
        toolbar.targetComponent = this
        add(toolbar.component, BorderLayout.NORTH)
    }

    private fun setupContent() {
        table.apply {
            setShowGrid(false)
            isStriped = true
            emptyText.text = "Click 'Scan' to find similar drawables"
            selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            autoCreateRowSorter = true
        }

        val imageSplitter = JBSplitter(false, 0.5f).apply {
            firstComponent = leftPreview
            secondComponent = rightPreview
            splitterProportionKey = "LacakAsset.ImageSplitter"
        }

        val previewPanel = JPanel(BorderLayout()).apply {
            add(imageSplitter, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }

        val mainSplitter = JBSplitter(true, 0.55f).apply {
            firstComponent = JBScrollPane(table)
            secondComponent = previewPanel
            splitterProportionKey = "LacakAsset.MainSplitter"
        }

        add(mainSplitter, BorderLayout.CENTER)
    }

    private fun setupListeners() {
        // Row selection -> update preview
        table.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                val row = table.selectedRow
                if (row >= 0) {
                    val modelRow = table.convertRowIndexToModel(row)
                    val result = tableModel.getResultAt(modelRow)
                    updatePreview(result)
                } else {
                    clearPreview()
                }
            }
        }

        // Double-click -> navigate to a file
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val row = table.rowAtPoint(e.point)
                    val col = table.columnAtPoint(e.point)
                    if (row >= 0) {
                        val modelRow = table.convertRowIndexToModel(row)
                        val result = tableModel.getResultAt(modelRow) ?: return
                        val file = if (col <= 2) result.fileA.virtualFile else result.fileB.virtualFile

                        FileEditorManager.getInstance(project).openFile(file, true)
                    }
                }
            }
        })
    }

    private fun registerWithScanService() {
        val scanService = DrawableScanService.getInstance(project)

        scanService.onScanStarted = {
            table.setPaintBusy(true)
            table.emptyText.text = "Scanning drawable resources..."
            statusLabel.text = ""
        }

        scanService.onScanCompleted = { results ->
            table.setPaintBusy(false)
            tableModel.setResults(results)
            DrawableHashCacheService.getInstance(project).clearChangedFlag()

            if (results.isEmpty()) {
                table.emptyText.text = "No similar drawables found"
                statusLabel.text = "Scan complete - no duplicates detected"
            } else {
                table.setRowSelectionInterval(0, 0)
                val totalBytes = results.sumOf { minOf(it.fileA.virtualFile.length, it.fileB.virtualFile.length) }
                val savings = formatFileSize(totalBytes)
                statusLabel.text = "Found ${results.size} similar pairs · ~$savings potential savings"
            }
        }

        scanService.onScanCancelled = { partialResults ->
            table.setPaintBusy(false)
            tableModel.setResults(partialResults)
            statusLabel.text = "Scan cancelled. Showing partial results."
        }

        scanService.onScanError = { error ->
            table.setPaintBusy(false)
            table.emptyText.text = "Scan failed: ${error.message}"
            statusLabel.text = "Error: ${error.message}"
        }
    }

    private fun updatePreview(result: SimilarityResult?) {
        if (result == null) {
            clearPreview()
            return
        }

        val cacheService = DrawableHashCacheService.getInstance(project)

        val cachedA = cacheService.getCached(result.fileA.virtualFile.path)
        val cachedB = cacheService.getCached(result.fileB.virtualFile.path)

        leftPreview.setPreview(
            cachedA?.thumbnail,
            result.fileA.virtualFile.name
        )
        rightPreview.setPreview(
            cachedB?.thumbnail,
            result.fileB.virtualFile.name
        )
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun clearPreview() {
        leftPreview.clearPreview()
        rightPreview.clearPreview()
    }

    override fun dispose() {
        val scanService = DrawableScanService.getInstance(project)
        scanService.onScanStarted = null
        scanService.onScanCompleted = null
        scanService.onScanCancelled = null
        scanService.onScanError = null
    }
}
