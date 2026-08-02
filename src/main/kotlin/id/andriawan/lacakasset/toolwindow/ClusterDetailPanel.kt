package id.andriawan.lacakasset.toolwindow

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import id.andriawan.lacakasset.model.DrawableCluster
import id.andriawan.lacakasset.model.HashedDrawable
import id.andriawan.lacakasset.util.formatFileSize
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants

/**
 * Shows every member of the selected cluster, so the user decides once per asset rather than
 * once per pair.
 */
class ClusterDetailPanel(
    private val onOpen: (HashedDrawable) -> Unit,
    private val onReveal: (HashedDrawable) -> Unit,
    private val onMakeCanonical: (HashedDrawable) -> Unit,
    private val onDelete: (HashedDrawable) -> Unit
) : JPanel(BorderLayout()) {

    private val headerLabel = JBLabel("")
    private val subheaderLabel = JBLabel("").apply { foreground = UIUtil.getContextHelpForeground() }
    private val warningLabel = JBLabel("").apply { foreground = JBColor.ORANGE }
    private val cardContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val placeholder = JBLabel("Select a group to see its copies", SwingConstants.CENTER).apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    private val scrollPane = JBScrollPane(cardContainer).apply {
        border = JBUI.Borders.empty()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    init {
        border = JBUI.Borders.empty(8)
        add(buildHeader(), BorderLayout.NORTH)
        add(placeholder, BorderLayout.CENTER)
    }

    private fun buildHeader() = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.emptyBottom(8)

        headerLabel.font = headerLabel.font.deriveFont(headerLabel.font.style or java.awt.Font.BOLD)
        add(row(headerLabel))
        add(row(subheaderLabel))
        add(row(warningLabel))
    }

    private fun row(component: javax.swing.JComponent) =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, JBUI.scale(1))).apply {
            isOpaque = false
            add(component)
        }

    fun showCluster(cluster: DrawableCluster) {
        headerLabel.text = cluster.canonical.file.fileName
        subheaderLabel.text = buildString {
            append("${cluster.memberCount} copies")
            append("  ·  similarity ${cluster.similarityRangeLabel}")
            if (!cluster.isExternal) {
                append("  ·  ${formatFileSize(cluster.estimatedSaving)} recoverable")
            }
        }

        warningLabel.text = when {
            cluster.isExternal ->
                "This file is not in the project. Nothing here will be added or written."

            cluster.isMixedFormat ->
                "Mixed formats. Raster and vector drawables are not interchangeable — " +
                        "check rendering and tinting before deleting either."

            else -> ""
        }
        warningLabel.isVisible = warningLabel.text.isNotEmpty()

        cardContainer.removeAll()
        val canonicalPath = cluster.canonical.file.path
        // Canonical first: the copy being kept is the reference the others are judged against.
        val ordered = listOf(cluster.canonical) + cluster.members.filter { it.file.path != canonicalPath }
        for (member in ordered) {
            cardContainer.add(
                MemberCard(
                    member = member,
                    isCanonical = member.file.path == canonicalPath,
                    isExternal = cluster.isExternal && member.file.path == canonicalPath,
                    inExternalCluster = cluster.isExternal,
                    onOpen = onOpen,
                    onReveal = onReveal,
                    onMakeCanonical = onMakeCanonical,
                    onDelete = onDelete
                )
            )
        }

        setBody(scrollPane)
    }

    fun showPlaceholder(text: String) {
        headerLabel.text = ""
        subheaderLabel.text = ""
        warningLabel.text = ""
        warningLabel.isVisible = false
        placeholder.text = text
        setBody(placeholder)
    }

    private fun setBody(component: java.awt.Component) {
        (layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)?.let { remove(it) }
        add(component, BorderLayout.CENTER)
        revalidate()
        repaint()
    }
}
