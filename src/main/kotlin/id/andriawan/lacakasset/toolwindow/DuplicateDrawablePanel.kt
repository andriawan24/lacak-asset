package id.andriawan.lacakasset.toolwindow

import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.DnDSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import id.andriawan.lacakasset.model.DrawableCluster
import id.andriawan.lacakasset.model.DrawableFormat
import id.andriawan.lacakasset.model.ExternalCheckResult
import id.andriawan.lacakasset.model.HashedDrawable
import id.andriawan.lacakasset.model.ScanState
import id.andriawan.lacakasset.engine.ClusterBuilder
import id.andriawan.lacakasset.service.DrawableReferenceCounter
import id.andriawan.lacakasset.service.DrawableScanService
import id.andriawan.lacakasset.util.formatFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.datatransfer.DataFlavor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

/**
 * The plugin's single results surface.
 *
 * A cluster list on the leading side, the selected cluster's members on the trailing side.
 * The split runs horizontally because the tool window is anchored to the bottom of the IDE,
 * where width is plentiful and height is not.
 *
 * All three entry points land here: a full scan fills the list, a targeted scan selects one
 * of its rows, and an external candidate appears as a pinned row at the top.
 */
class DuplicateDrawablePanel(
    private val project: Project
) : JPanel(BorderLayout()), Disposable {

    private val clusterModel = ClusterTableModel()
    private val clusterTable = JBTable(clusterModel)
    private val statusLabel = JBLabel("", SwingConstants.LEFT)

    private val detailPanel = ClusterDetailPanel(
        onOpen = ::openInEditor,
        onReveal = ::revealInFiles,
        onMakeCanonical = ::makeCanonical,
        onDelete = ::deleteMember
    )

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)

    private val filterBar = ResultFilterBar(
        initialThresholdPercent = (DrawableScanService.getInstance(project).displayedThreshold() * 100).toInt(),
        onChanged = { rebuildClusters() }
    )

    private val referenceCounter = DrawableReferenceCounter(project)
    private val deleter = RedundantDrawableDeleter(project)

    /** Retained so the list can be rebuilt without rescanning. */
    private var readyState: ScanState.Ready? = null
    private var externalCluster: DrawableCluster? = null

    /** Canonical choices the user made, keyed by path so they survive re-clustering. */
    private val canonicalOverrides = mutableSetOf<String>()

    /**
     * Usage counts for clusters the user has looked at. Counting is a project-wide search, so
     * it happens on selection and the answers are kept.
     */
    private val referenceCounts = mutableMapOf<String, Int>()
    private val countedClusters = mutableSetOf<String>()

    private var externalJob: Job? = null

    init {
        setupHeader()
        setupContent()
        setupSelectionListener()
        observeScanState()
        setupDropTarget()
        detailPanel.showPlaceholder("Run Scan All Drawables to find duplicate resources")
    }

    private fun setupHeader() {
        val actionGroup = DefaultActionGroup().apply {
            ActionManager.getInstance().getAction("LacakAsset.Scan")?.let { add(it) }
            ActionManager.getInstance().getAction("LacakAsset.CompareAsset")?.let { add(it) }
        }

        val toolbar = ActionManager.getInstance().createActionToolbar("LacakAssetToolbar", actionGroup, true)
        toolbar.targetComponent = this

        val topRow = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.WEST)
            statusLabel.border = JBUI.Borders.empty(0, 8)
            statusLabel.foreground = UIUtil.getContextHelpForeground()
            add(statusLabel, BorderLayout.CENTER)
        }

        val header = JPanel(BorderLayout()).apply {
            add(topRow, BorderLayout.NORTH)
            add(filterBar, BorderLayout.SOUTH)
        }
        add(header, BorderLayout.NORTH)
    }

    private fun setupContent() {
        clusterTable.apply {
            setShowGrid(false)
            isStriped = true
            rowHeight = JBUI.scale(44)
            emptyText.text = "Run Scan All Drawables to find duplicate resources"
            selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            autoCreateRowSorter = true
            accessibleContext?.accessibleName = "Duplicate drawable groups"
            setDefaultRenderer(java.awt.image.BufferedImage::class.java, ThumbnailCellRenderer())
            setDefaultRenderer(Long::class.javaObjectType, FileSizeCellRenderer())
        }
        setupColumns()

        clusterTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                selectedCluster()?.let { openInEditor(it.canonical) }
            }
        })

        val splitter = JBSplitter(false, 0.42f).apply {
            firstComponent = JBScrollPane(clusterTable)
            secondComponent = detailPanel
            splitterProportionKey = "LacakAsset.MasterDetailSplitter"
        }

        add(splitter, BorderLayout.CENTER)
    }

    private fun setupColumns() {
        val columns = clusterTable.columnModel

        columns.getColumn(ClusterTableModel.COLUMN_PREVIEW).apply {
            preferredWidth = JBUI.scale(48)
            maxWidth = JBUI.scale(48)
            minWidth = JBUI.scale(48)
        }
        columns.getColumn(ClusterTableModel.COLUMN_NAME).preferredWidth = JBUI.scale(220)
        columns.getColumn(ClusterTableModel.COLUMN_COUNT).apply {
            preferredWidth = JBUI.scale(80)
            maxWidth = JBUI.scale(100)
            cellRenderer = CountCellRenderer()
        }
        columns.getColumn(ClusterTableModel.COLUMN_SIMILARITY).apply {
            preferredWidth = JBUI.scale(88)
            maxWidth = JBUI.scale(110)
            cellRenderer = PercentCellRenderer()
        }
        columns.getColumn(ClusterTableModel.COLUMN_SAVING).apply {
            preferredWidth = JBUI.scale(100)
            maxWidth = JBUI.scale(130)
        }
    }

    private fun setupSelectionListener() {
        clusterTable.selectionModel.addListSelectionListener { event ->
            if (event.valueIsAdjusting) return@addListSelectionListener
            val cluster = selectedCluster()
            if (cluster == null) {
                detailPanel.showPlaceholder("Select a group to see its copies")
            } else {
                detailPanel.showCluster(cluster)
                countReferencesFor(cluster)
            }
        }
    }

    /**
     * Counts usages for the selected cluster and re-picks its canonical member from the
     * result. Counting is a project-wide search, so it runs once per cluster and only for
     * clusters the user actually looks at.
     */
    private fun countReferencesFor(cluster: DrawableCluster) {
        if (cluster.isExternal) return
        val key = cluster.canonical.file.path
        if (!countedClusters.add(key)) return

        uiScope.launch {
            val counts = referenceCounter.countFor(cluster.members)
            if (counts.isEmpty()) return@launch

            referenceCounts.putAll(counts)
            // Only re-rank when the counts actually change which copy would be kept.
            val repicked = ClusterBuilder.selectCanonical(
                members = cluster.members,
                canonicalOverrides = canonicalOverrides,
                referenceCounts = referenceCounts
            )
            if (repicked.file.path != cluster.canonical.file.path) {
                rebuildClusters()
            }
        }
    }

    private fun selectedCluster(): DrawableCluster? {
        val row = clusterTable.selectedRow
        if (row < 0) return null
        return clusterModel.getClusterAt(clusterTable.convertRowIndexToModel(row))
    }

    /**
     * Renders whatever the service's current state is, now and on every change. Because the
     * state is retained, reopening the tool window after a scan shows the existing results
     * rather than an empty list.
     */
    private fun observeScanState() {
        val scanService = DrawableScanService.getInstance(project)

        scanService.onTargetedScanCompleted = { targetFile ->
            revealToolWindow()
            selectClusterContaining(targetFile.path)
        }

        uiScope.launch {
            scanService.state.collectLatest { render(it) }
        }
    }

    private fun render(state: ScanState) {
        when (state) {
            is ScanState.Idle -> {
                clusterTable.setPaintBusy(false)
                statusLabel.text = ""
            }

            is ScanState.Scanning -> {
                clusterTable.setPaintBusy(true)
                clusterTable.emptyText.text = "Scanning drawable resources..."
                statusLabel.text = if (state.total > 0) {
                    "Hashing ${state.processed} of ${state.total} drawables..."
                } else {
                    "Scanning..."
                }
            }

            is ScanState.Ready -> {
                clusterTable.setPaintBusy(false)
                readyState = state
                rebuildClusters(selectFirst = true)
            }

            is ScanState.Failed -> {
                clusterTable.setPaintBusy(false)
                clusterTable.emptyText.text = "Scan failed: ${state.message}"
                statusLabel.text = "Scan failed. Check the IDE log for details."
                detailPanel.showPlaceholder("Scan failed: ${state.message}")
            }
        }
    }

    /**
     * Recomputes clusters from the retained pairs. Grouping is cheap, so this runs on any
     * change to the threshold or to a canonical choice rather than triggering a rescan.
     */
    private fun rebuildClusters(selectFirst: Boolean = false) {
        val previouslySelected = selectedCluster()?.canonical?.file?.path

        val allClusters = readyState?.clustersAt(
            threshold = filterBar.threshold,
            canonicalOverrides = canonicalOverrides,
            referenceCounts = referenceCounts
        ).orEmpty()

        filterBar.refreshChoices(allClusters)
        val projectClusters = allClusters.filter { filterBar.accepts(it) }

        // The external candidate is pinned above project results rather than sorted among them.
        val clusters = listOfNotNull(externalCluster) + projectClusters
        clusterModel.setClusters(clusters)

        updateStatus(allClusters, projectClusters)

        when {
            clusters.isNotEmpty() && previouslySelected != null && !selectFirst ->
                selectClusterBy { it.contains(previouslySelected) }

            clusters.isNotEmpty() -> selectRow(0)

            // Distinguish "nothing matched" from "your filters are hiding everything":
            // telling a user there are no duplicates when there are is actively misleading.
            allClusters.isNotEmpty() -> {
                clusterTable.emptyText.text = "No groups match the current filters"
                detailPanel.showPlaceholder(
                    "${allClusters.size} groups are hidden by the current filters. " +
                            "Clear them to see everything."
                )
            }

            else -> {
                clusterTable.emptyText.text = "No similar drawables found at the current threshold"
                detailPanel.showPlaceholder("No duplicate drawables found at the current threshold")
            }
        }
    }

    private fun updateStatus(allClusters: List<DrawableCluster>, visible: List<DrawableCluster>) {
        statusLabel.text = when {
            allClusters.isEmpty() && externalCluster != null -> "Checked one external file."
            allClusters.isEmpty() -> "Scan complete. No cleanup needed."
            visible.isEmpty() -> "${allClusters.size} groups hidden by filters."
            else -> {
                val total = visible.sumOf { it.estimatedSaving }
                val hidden = allClusters.size - visible.size
                buildString {
                    append("${visible.size} duplicate groups · ${formatFileSize(total)} recoverable")
                    if (hidden > 0) append("  ($hidden hidden by filters)")
                }
            }
        }
    }

    private fun selectRow(viewRow: Int) {
        if (clusterModel.rowCount == 0) return
        clusterTable.setRowSelectionInterval(viewRow, viewRow)
    }

    private fun selectClusterBy(predicate: (DrawableCluster) -> Boolean) {
        val modelRow = clusterModel.indexOfCluster(predicate)
        if (modelRow < 0) {
            selectRow(0)
            return
        }
        val viewRow = clusterTable.convertRowIndexToView(modelRow)
        clusterTable.setRowSelectionInterval(viewRow, viewRow)
        clusterTable.scrollRectToVisible(clusterTable.getCellRect(viewRow, 0, true))
    }

    private fun selectClusterContaining(path: String) {
        if (clusterModel.indexOfCluster { it.contains(path) } < 0) {
            detailPanel.showPlaceholder("Nothing similar to that drawable was found.")
            return
        }
        selectClusterBy { it.contains(path) }
    }

    private fun openInEditor(member: HashedDrawable) {
        if (project.isDisposed) return
        FileEditorManager.getInstance(project).openFile(member.file.virtualFile, true)
    }

    private fun revealInFiles(member: HashedDrawable) {
        RevealFileAction.openFile(File(member.file.path))
    }

    private fun makeCanonical(member: HashedDrawable) {
        val cluster = selectedCluster() ?: return
        // One override per cluster: drop any previous choice among these members first.
        cluster.members.forEach { canonicalOverrides.remove(it.file.path) }
        canonicalOverrides.add(member.file.path)
        rebuildClusters()
    }

    private fun deleteMember(member: HashedDrawable) {
        val cluster = selectedCluster() ?: return

        deleter.delete(cluster, member) {
            // Safe Delete's callback fires whether or not the user went through with it, so
            // the file itself is the source of truth rather than the fact we were called.
            if (!member.file.virtualFile.isValid) {
                dropDeletedMember(member)
            }
        }
    }

    /** Removes a deleted drawable from the results, discarding the group when one copy is left. */
    private fun dropDeletedMember(member: HashedDrawable) {
        val path = member.file.path
        canonicalOverrides.remove(path)
        referenceCounts.remove(path)

        readyState?.let { state ->
            readyState = ScanState.Ready(
                pairs = state.pairs.filterNot { it.fileA.path == path || it.fileB.path == path },
                drawablesByPath = state.drawablesByPath - path
            )
        }

        externalCluster?.let { cluster ->
            val remaining = cluster.members.filter { it.file.path != path }
            externalCluster = if (remaining.size < 2) null else cluster.copy(members = remaining)
        }

        rebuildClusters()
    }

    /**
     * Shows the tool window and gives it at least a quarter of the frame height, so results
     * arriving from elsewhere in the IDE are actually visible.
     */
    private fun revealToolWindow() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(DuplicateDrawableToolWindowFactory.TOOL_WINDOW_ID) ?: return
        toolWindow.show {
            val ideFrame = toolWindow.component.rootPane ?: return@show
            val targetHeight = ideFrame.height / 4
            val currentHeight = toolWindow.component.height
            if (currentHeight < targetHeight) {
                @Suppress("UnstableApiUsage")
                (toolWindow as? ToolWindowEx)?.stretchHeight(targetHeight - currentHeight)
            }
        }
    }

    // --- External candidates -------------------------------------------------------------

    /** Starts a check for [file], replacing any candidate already displayed. */
    fun checkExternalFile(file: File) {
        externalJob?.cancel()
        externalCluster = null
        rebuildClusters()

        revealToolWindow()
        detailPanel.showPlaceholder("Checking ${file.name} against the project's drawables...")
        statusLabel.text = "Checking ${file.name}..."

        externalJob = DrawableScanService.getInstance(project).checkExternalFile(file) { result ->
            showExternalResult(result)
        }
    }

    private fun showExternalResult(result: ExternalCheckResult) {
        when (result) {
            is ExternalCheckResult.Completed -> {
                externalCluster = result.cluster
                rebuildClusters()
                if (result.cluster == null) {
                    detailPanel.showPlaceholder(
                        "Nothing in this project looks like ${result.candidateName} " +
                                "at the current threshold."
                    )
                } else {
                    selectClusterBy { it.isExternal }
                }
            }

            is ExternalCheckResult.Error -> {
                externalCluster = null
                rebuildClusters()
                detailPanel.showPlaceholder(result.message)
                statusLabel.text = "Check failed."
            }
        }
    }

    private fun setupDropTarget() {
        val scanService = DrawableScanService.getInstance(project)

        DnDSupport.createBuilder(this)
            .enableAsNativeTarget()
            .setTargetChecker { event ->
                // All branches return false ("I handle this" per IntelliJ DnD convention)
                val file = extractSingleFile(event)
                val acceptable = file != null &&
                        !file.isDirectory &&
                        DrawableFormat.fromExtension(file.extension) != null &&
                        !scanService.isScanning &&
                        !scanService.isDropAnalysisRunning

                event.setDropPossible(acceptable)
                if (acceptable) {
                    event.setHighlighting(this, DnDEvent.DropTargetHighlightingType.RECTANGLE)
                }
                false
            }
            .setDropHandler { event ->
                extractSingleFile(event)?.let { checkExternalFile(it) }
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
        DrawableScanService.getInstance(project).onTargetedScanCompleted = null
        uiScope.cancel()
        externalJob?.cancel()
        externalJob = null
    }
}
