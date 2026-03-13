package id.andriawan.lacakasset.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import id.andriawan.lacakasset.scanner.DrawableFileScanner
import id.andriawan.lacakasset.service.DrawableScanService

class FindSimilarDrawableAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val scanService = DrawableScanService.getInstance(project)
        if (!scanService.isScanning) {
            scanService.scanSingleFile(file)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (project == null || file == null || file.isDirectory
            || file.extension?.lowercase() !in DrawableFileScanner.DRAWABLE_EXTENSIONS
            || !DrawableFileScanner.isInDrawableDirectory(file)
        ) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isVisible = true
        e.presentation.isEnabled = !DrawableScanService.getInstance(project).isScanning
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
