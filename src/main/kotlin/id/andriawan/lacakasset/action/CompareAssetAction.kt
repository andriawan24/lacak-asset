package id.andriawan.lacakasset.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import id.andriawan.lacakasset.service.DrawableScanService
import id.andriawan.lacakasset.toolwindow.DuplicateDrawableToolWindowFactory
import java.io.File

/**
 * Checks a file the user picks — typically one not yet in the project — against the project's
 * drawables. Results land in the tool window as a pinned group, the same place a dragged file
 * ends up.
 */
class CompareAssetAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle("Select Drawable to Compare")
            .withDescription("Choose a drawable file to find similar assets in this project")

        FileChooser.chooseFile(descriptor, project, null) { chosen ->
            DuplicateDrawableToolWindowFactory.withPanel(project) { panel ->
                panel.checkExternalFile(File(chosen.path))
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val service = project?.let { DrawableScanService.getInstance(it) }
        e.presentation.isEnabled = service != null && !service.isScanning && !service.isDropAnalysisRunning
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
