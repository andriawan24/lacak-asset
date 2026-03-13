package id.andriawan.lacakasset.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import id.andriawan.lacakasset.service.DrawableScanService

class ScanDrawablesAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val scanService = DrawableScanService.getInstance(project)
        if (!scanService.isScanning) {
            scanService.startScan()
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && !DrawableScanService.getInstance(project).isScanning
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
