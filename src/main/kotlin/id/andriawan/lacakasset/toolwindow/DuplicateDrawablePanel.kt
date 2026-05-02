package id.andriawan.lacakasset.toolwindow

import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.DnDSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import id.andriawan.lacakasset.model.DrawableFormat
import id.andriawan.lacakasset.model.SimilarityResult
import id.andriawan.lacakasset.service.DrawableHashCacheService
import id.andriawan.lacakasset.service.DrawableScanService
import kotlinx.coroutines.Job
import java.awt.BorderLayout
import java.awt.datatransfer.DataFlavor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
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

    private var dropDialog: DropCheckDialog? = null
    private var dropJob: Job? = null

    init {
        setupToolbar()
        setupContent()
        setupListeners()
        registerWithScanService()
        setupDropTarget()
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
                        val modelCol = table.convertColumnIndexToModel(col)
                        val file =
                            if (modelCol == 1 || modelCol == 4) result.fileB.virtualFile else result.fileA.virtualFile

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

    private fun setupDropTarget() {
        val scanService = DrawableScanService.getInstance(project)

        DnDSupport.createBuilder(this)
            .enableAsNativeTarget()
            .setTargetChecker { event ->
                // All branches return false ("I handle this" per IntelliJ DnD convention)
                val file = extractSingleFile(event)
                if (file == null) {
                    event.setDropPossible(false)
                    return@setTargetChecker false
                }
                if (DrawableFormat.fromExtension(file.extension) == null || file.isDirectory) {
                    event.setDropPossible(false)
                    return@setTargetChecker false
                }
                if (scanService.isScanning || scanService.isDropAnalysisRunning) {
                    event.setDropPossible(false)
                    return@setTargetChecker false
                }
                event.setDropPossible(true)
                event.setHighlighting(this, DnDEvent.DropTargetHighlightingType.RECTANGLE)
                false
            }
            .setDropHandler { event ->
                val file = extractSingleFile(event) ?: return@setDropHandler
                val format = DrawableFormat.fromExtension(file.extension)

                dropJob?.cancel()

                val dialog = dropDialog
                if (dialog == null || dialog.isDisposed) {
                    val newDialog = DropCheckDialog(project, file.name)
                    dropDialog = newDialog
                    newDialog.show()
                } else {
                    dialog.resetToLoading(file.name)
                }

                if (format == null) {
                    dropDialog?.setState(id.andriawan.lacakasset.model.DropScanResult.Error("Unsupported file format"))
                    return@setDropHandler
                }

                dropJob = scanService.scanDroppedFile(file) { result ->
                    dropDialog?.setState(result)
                }
            }
            .setCleanUpOnLeaveCallback { repaint() }
            .setDisposableParent(this)
            .install()
    }

    private fun extractSingleFile(event: DnDEvent): File? {
        val info = event.attachedObject as? DnDNativeTarget.EventInfo ?: return null
        val transferable = info.transferable

        val files: List<File> = when {
            transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                @Suppress("UNCHECKED_CAST")
                transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File> ?: emptyList()
            }

            transferable.isDataFlavorSupported(DataFlavor.stringFlavor) -> {
                // Windows fallback: some apps deliver file path as plain string
                val path = transferable.getTransferData(DataFlavor.stringFlavor) as? String
                if (path != null) listOf(File(path.trim())) else emptyList()
            }

            else -> emptyList()
        }

        return files.singleOrNull()
    }

    override fun dispose() {
        val scanService = DrawableScanService.getInstance(project)
        scanService.onScanStarted = null
        scanService.onScanCompleted = null
        scanService.onScanCancelled = null
        scanService.onScanError = null

        dropJob?.cancel()
        dropJob = null
        dropDialog?.takeIf { !it.isDisposed }?.disposeIfNeeded()
        dropDialog = null
    }
}
