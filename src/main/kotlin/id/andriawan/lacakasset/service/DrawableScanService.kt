package id.andriawan.lacakasset.service

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import id.andriawan.lacakasset.model.DrawableFile
import id.andriawan.lacakasset.model.DrawableFormat
import id.andriawan.lacakasset.model.DrawableCluster
import id.andriawan.lacakasset.model.ExternalCheckResult
import id.andriawan.lacakasset.model.HashedDrawable
import id.andriawan.lacakasset.model.ScanState
import id.andriawan.lacakasset.model.SimilarityResult
import id.andriawan.lacakasset.settings.DrawableAnalyzerSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns drawable analysis for one project and publishes its state.
 *
 * Every entry point runs the same [ScanPipeline]; they differ only in what they pass to it
 * and which part of the result they present. State is exposed as a [StateFlow] with a
 * retained current value, so a tool window opened after a scan finished renders the
 * existing results instead of an empty panel.
 */
@Service(Service.Level.PROJECT)
class DrawableScanService(
    private val project: Project,
    private val cs: CoroutineScope
) {
    private val log = Logger.getInstance(DrawableScanService::class.java)

    private val pipeline = ScanPipeline(project)

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var dropJob: Job? = null

    val isScanning: Boolean get() = scanJob?.isActive == true
    val isDropAnalysisRunning: Boolean get() = dropJob?.isActive == true

    /** Emitted when a targeted scan finishes, so the panel can focus the target's results. */
    var onTargetedScanCompleted: ((VirtualFile) -> Unit)? = null

    fun startScan() {
        if (isScanning) return

        scanJob = cs.launch {
            runScan { pairs -> pairs }
        }
    }

    /**
     * Runs the same scan as [startScan]; the caller narrows the presentation to the target.
     * The whole project is still hashed, because matching the target requires every
     * candidate's hash — the cache makes repeat runs cheap.
     */
    fun scanSingleFile(targetFile: VirtualFile) {
        if (isScanning) return

        scanJob = cs.launch {
            val completed = runScan { pairs -> pairs }
            if (completed) {
                withContext(Dispatchers.EDT) { onTargetedScanCompleted?.invoke(targetFile) }
            }
        }
    }

    /**
     * Runs the pipeline and publishes the outcome.
     * Returns true when the scan completed normally.
     */
    private suspend fun runScan(transform: (List<SimilarityResult>) -> List<SimilarityResult>): Boolean {
        return try {
            val hashed = withBackgroundProgress(project, "Scanning drawable resources...") {
                val files = pipeline.discover()
                if (files.isEmpty()) {
                    emptyList()
                } else {
                    _state.value = ScanState.Scanning(0, files.size)
                    pipeline.hashAll(files) { processed, total ->
                        _state.value = ScanState.Scanning(processed, total)
                    }
                }
            }

            val result = transform(pipeline.compareAll(hashed))
            _state.value = ScanState.Ready(result, hashed.associateBy { it.file.path })
            notifyScanComplete(countAtDisplayedThreshold(result))
            true
        } catch (e: CancellationException) {
            _state.value = ScanState.Idle
            throw e
        } catch (e: Exception) {
            log.error("Drawable scan failed", e)
            _state.value = ScanState.Failed(e.message ?: "Unknown error")
            false
        }
    }

    /**
     * Analyses a file that may not belong to the project, returning it and its matches as one
     * cluster. The candidate is never cached and never written to.
     */
    fun checkExternalFile(ioFile: File, callback: (ExternalCheckResult) -> Unit): Job {
        val job = cs.launch {
            try {
                withBackgroundProgress(project, "Checking similarity…") {
                    val virtualFile = readAction {
                        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)
                    }
                    if (virtualFile == null) {
                        deliver(callback, ExternalCheckResult.Error("File no longer accessible"))
                        return@withBackgroundProgress
                    }

                    val format = DrawableFormat.fromExtension(ioFile.extension)
                    if (format == null) {
                        deliver(callback, ExternalCheckResult.Error("Unsupported file format"))
                        return@withBackgroundProgress
                    }

                    val candidate = DrawableFile(
                        virtualFile = virtualFile,
                        path = virtualFile.path,
                        byteSize = virtualFile.length,
                        resourceName = ioFile.nameWithoutExtension,
                        format = format,
                        densityQualifier = "",
                        modulePath = "(external)",
                        sourceSet = "dropped"
                    )

                    val target = pipeline.hashSingle(candidate)
                    if (target == null) {
                        deliver(callback, ExternalCheckResult.Error("Could not process file"))
                        return@withBackgroundProgress
                    }

                    val cached = DrawableHashCacheService.getInstance(project).getAllCached()
                    val candidates = cached.ifEmpty { pipeline.hashAll(pipeline.discover()) }
                    val byPath = candidates.associateBy { it.file.path }

                    val threshold = displayedThreshold()
                    val matches = pipeline.compareToTarget(target, candidates)
                        .filter { it.normalizedSimilarity >= threshold }

                    deliver(
                        callback,
                        ExternalCheckResult.Completed(buildExternalCluster(target, matches, byPath), ioFile.name)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("External drawable check failed", e)
                deliver(callback, ExternalCheckResult.Error(e.message ?: "Unknown error"))
            }
        }
        dropJob = job
        return job
    }

    /**
     * The candidate is always the canonical member: it is not part of the project, so it is
     * never the copy to remove.
     */
    private fun buildExternalCluster(
        target: HashedDrawable,
        matches: List<SimilarityResult>,
        candidatesByPath: Map<String, HashedDrawable>
    ): DrawableCluster? {
        if (matches.isEmpty()) return null

        val members = matches.mapNotNull { candidatesByPath[it.fileB.path] }
        if (members.isEmpty()) return null

        val scores = matches.map { it.normalizedSimilarity }
        return DrawableCluster(
            members = listOf(target) + members,
            canonical = target,
            strongestSimilarity = scores.max(),
            weakestSimilarity = scores.min(),
            isExternal = true
        )
    }

    private suspend fun deliver(callback: (ExternalCheckResult) -> Unit, result: ExternalCheckResult) {
        withContext(Dispatchers.EDT) { callback(result) }
    }

    /** The similarity threshold currently being displayed, as a normalized fraction. */
    fun displayedThreshold(): Double =
        DrawableAnalyzerSettings.getInstance(project).state.similarityThreshold / 100.0

    private fun countAtDisplayedThreshold(pairs: List<SimilarityResult>): Int {
        val threshold = displayedThreshold()
        return pairs.count { it.normalizedSimilarity >= threshold }
    }

    private fun notifyScanComplete(count: Int) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup("Lacak Asset")
        val notification = if (count > 0) {
            group.createNotification(
                "Drawable Analysis Complete",
                "$count similar drawable pairs found",
                NotificationType.WARNING
            )
        } else {
            group.createNotification(
                "No similar drawables found in this project.",
                NotificationType.INFORMATION
            )
        }
        notification.notify(project)
    }

    companion object {
        fun getInstance(project: Project): DrawableScanService {
            return project.getService(DrawableScanService::class.java)
        }
    }
}
