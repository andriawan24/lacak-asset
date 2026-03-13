package id.andriawan.lacakasset.service

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import id.andriawan.lacakasset.engine.SimilarityEngine
import id.andriawan.lacakasset.model.DrawableFormat
import id.andriawan.lacakasset.model.SimilarityResult
import id.andriawan.lacakasset.normalizer.DrawableNormalizer
import id.andriawan.lacakasset.scanner.DrawableFileScanner
import id.andriawan.lacakasset.settings.DrawableAnalyzerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class DrawableScanService(
    private val project: Project,
    private val cs: CoroutineScope
) {
    private val log = Logger.getInstance(DrawableScanService::class.java)

    private val scanner = DrawableFileScanner()
    private val normalizer = DrawableNormalizer()
    private val similarityEngine = SimilarityEngine()

    private var scanJob: Job? = null
    private val _results = CopyOnWriteArrayList<SimilarityResult>()

    val isScanning: Boolean get() = scanJob?.isActive == true

    var onScanStarted: (() -> Unit)? = null
    var onScanCompleted: ((List<SimilarityResult>) -> Unit)? = null
    var onScanCancelled: ((List<SimilarityResult>) -> Unit)? = null
    var onScanError: ((Throwable) -> Unit)? = null

    fun startScan() {
        if (isScanning) return

        scanJob = cs.launch {
            try {
                onScanStarted?.invoke()
                val scanResults = performScan()
                _results.clear()
                _results.addAll(scanResults)
                onScanCompleted?.invoke(scanResults)
                notifyScanComplete(scanResults.size)
            } catch (e: kotlinx.coroutines.CancellationException) {
                onScanCancelled?.invoke(_results.toList())
                throw e
            } catch (e: Exception) {
                log.error("Drawable scan failed", e)
                onScanError?.invoke(e)
            }
        }
    }

    fun scanSingleFile(targetFile: VirtualFile) {
        if (isScanning) return

        scanJob = cs.launch {
            try {
                onScanStarted?.invoke()
                val scanResults = performSingleFileScan(targetFile)
                _results.clear()
                _results.addAll(scanResults)
                onScanCompleted?.invoke(scanResults)

                withContext(Dispatchers.EDT) {
                    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Lacak Asset")
                    if (toolWindow != null) {
                        toolWindow.show {
                            val ideFrame = toolWindow.component.rootPane
                            if (ideFrame != null) {
                                val targetHeight = ideFrame.height / 4
                                val currentHeight = toolWindow.component.height
                                if (currentHeight < targetHeight) {
                                    @Suppress("UnstableApiUsage")
                                    (toolWindow as? com.intellij.openapi.wm.ex.ToolWindowEx)
                                        ?.stretchHeight(targetHeight - currentHeight)
                                }
                            }
                        }
                    }
                }

                notifyScanComplete(scanResults.size)
            } catch (e: kotlinx.coroutines.CancellationException) {
                onScanCancelled?.invoke(_results.toList())
                throw e
            } catch (e: Exception) {
                log.error("Single file drawable scan failed", e)
                onScanError?.invoke(e)
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    private suspend fun performSingleFileScan(targetFile: VirtualFile): List<SimilarityResult> {
        return withBackgroundProgress(project, "Finding similar drawables...") {
            val settings = DrawableAnalyzerSettings.getInstance(project)
            val excludedDirs = (settings.state.excludedDirectories ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            val allFiles = readAction { scanner.findDrawableFiles(project, excludedDirs) }
            if (allFiles.isEmpty()) return@withBackgroundProgress emptyList()

            val files = if (settings.state.includeXmlDrawables) {
                allFiles
            } else {
                allFiles.filter { it.format != DrawableFormat.ANDROID_VECTOR }
            }

            files.find { it.virtualFile.path == targetFile.path }
                ?: return@withBackgroundProgress emptyList()

            val cacheService = DrawableHashCacheService.getInstance(project)
            val hashedDrawables = readAction { normalizer.normalizeAndHash(files, project, cacheService, similarityEngine) }

            val hashedTarget = hashedDrawables.find { it.file.virtualFile.path == targetFile.path }
                ?: return@withBackgroundProgress emptyList()

            val threshold = settings.state.similarityThreshold / 100.0
            similarityEngine.findSimilarToTarget(hashedTarget, hashedDrawables, threshold)
        }
    }

    private suspend fun performScan(): List<SimilarityResult> {
        return withBackgroundProgress(project, "Scanning drawable resources...") {
            val settings = DrawableAnalyzerSettings.getInstance(project)
            val excludedDirs = (settings.state.excludedDirectories ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            val allFiles = readAction { scanner.findDrawableFiles(project, excludedDirs) }
            if (allFiles.isEmpty()) return@withBackgroundProgress emptyList()

            val files = if (settings.state.includeXmlDrawables) {
                allFiles
            } else {
                allFiles.filter { it.format != DrawableFormat.ANDROID_VECTOR }
            }

            // Step 2: Hash all files
            val cacheService = DrawableHashCacheService.getInstance(project)
            val hashedDrawables = readAction { normalizer.normalizeAndHash(files, project, cacheService, similarityEngine) }

            // Step 3: Find similar pairs
            val threshold = settings.state.similarityThreshold / 100.0
            similarityEngine.findSimilarPairs(hashedDrawables, threshold)
        }
    }

    private fun notifyScanComplete(count: Int) {
        val notification = if (count > 0) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Lacak Asset")
                .createNotification(
                    "Drawable Analysis Complete",
                    "$count similar drawable pairs found",
                    NotificationType.WARNING
                )
        } else {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Lacak Asset")
                .createNotification(
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
