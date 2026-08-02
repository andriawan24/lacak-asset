package id.andriawan.lacakasset.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

class DuplicateDrawableToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DuplicateDrawablePanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "Similar Drawables", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        const val TOOL_WINDOW_ID = "Lacak Asset"

        /**
         * Activates the tool window and hands its panel to [action].
         *
         * Activation is what forces a lazily-created tool window to build its content, so the
         * panel is looked up inside the activation callback rather than before it.
         */
        fun withPanel(project: Project, action: (DuplicateDrawablePanel) -> Unit) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
            toolWindow.activate {
                toolWindow.contentManager.contents
                    .firstNotNullOfOrNull { it.component as? DuplicateDrawablePanel }
                    ?.let(action)
            }
        }
    }
}
