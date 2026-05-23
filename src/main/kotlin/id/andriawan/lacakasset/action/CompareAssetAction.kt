package id.andriawan.lacakasset.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import id.andriawan.lacakasset.model.DrawableFormat
import id.andriawan.lacakasset.service.DrawableScanService
import id.andriawan.lacakasset.toolwindow.DropCheckDialog
import id.andriawan.lacakasset.model.DropScanResult
import java.io.File

class CompareAssetAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val descriptor = FileChooserDescriptor(
            true,
            false,
            false,
            false,
            false,
            false
        )
            .withTitle("Select Drawable to Compare")
            .withDescription("Choose a drawable file to find similar assets in this project")

        FileChooser.chooseFile(descriptor, project, null) { vFile ->
            val format = DrawableFormat.fromExtension(vFile.extension ?: "")
            val ioFile = File(vFile.path)

            val dialog = DropCheckDialog(project, vFile.name)
            dialog.show()

            if (format == null) {
                dialog.setState(DropScanResult.Error("Unsupported file format: ${vFile.extension}"))
                return@chooseFile
            }

            val scanService = DrawableScanService.getInstance(project)
            scanService.scanDroppedFile(ioFile) { result ->
                dialog.setState(result)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
                !DrawableScanService.getInstance(project).isScanning &&
                !DrawableScanService.getInstance(project).isDropAnalysisRunning
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
