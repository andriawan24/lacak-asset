package id.andriawan.lacakasset.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class DuplicateDrawableToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DuplicateDrawablePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "Similar Drawables", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}
