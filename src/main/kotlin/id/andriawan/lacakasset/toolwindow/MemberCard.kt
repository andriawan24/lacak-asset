package id.andriawan.lacakasset.toolwindow

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import id.andriawan.lacakasset.model.HashedDrawable
import id.andriawan.lacakasset.util.formatFileSize
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * One drawable within a cluster: what it looks like, where it lives, and what can be done
 * with it.
 *
 * The canonical member is badged and offers no delete action — a cluster must always keep
 * one copy, so the action that would empty it is simply absent rather than disabled.
 */
class MemberCard(
    private val member: HashedDrawable,
    private val isCanonical: Boolean,
    /** True for the dropped or picked file itself, which lives outside the project. */
    private val isExternal: Boolean,
    /** True for every card in an external check, where the candidate is fixed as canonical. */
    private val inExternalCluster: Boolean,
    private val onOpen: (HashedDrawable) -> Unit,
    private val onReveal: (HashedDrawable) -> Unit,
    private val onMakeCanonical: (HashedDrawable) -> Unit,
    private val onDelete: (HashedDrawable) -> Unit
) : JPanel(BorderLayout(JBUI.scale(10), 0)) {

    init {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
            JBUI.Borders.empty(8, 6)
        )
        isOpaque = false
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(84))

        add(buildPreview(), BorderLayout.WEST)
        add(buildDetails(), BorderLayout.CENTER)

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) onOpen(member)
            }
        })
        toolTipText = member.file.path
    }

    private fun buildPreview() = ImagePreviewPanel().apply {
        preferredSize = Dimension(JBUI.scale(64), JBUI.scale(64))
        minimumSize = preferredSize
        maximumSize = preferredSize
        setPreview(member.thumbnail, member.file.fileName)
        showCaption(false)
    }

    private fun buildDetails() = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)

        add(buildTitleRow())
        add(JBLabel(member.file.sourceDescription).apply {
            foreground = UIUtil.getContextHelpForeground()
            alignmentX = LEFT_ALIGNMENT
        })
        add(JBLabel(describeSize()).apply {
            foreground = UIUtil.getContextHelpForeground()
            alignmentX = LEFT_ALIGNMENT
        })
        add(buildActionRow())
    }

    private fun describeSize(): String {
        val size = formatFileSize(member.file.byteSize)
        return if (member.sourceWidth > 0 && member.sourceHeight > 0) {
            "$size  ·  ${member.sourceWidth}x${member.sourceHeight}"
        } else {
            size
        }
    }

    private fun buildTitleRow() = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT

        add(JBLabel(member.file.fileName).apply {
            font = font.deriveFont(font.style or java.awt.Font.BOLD)
        })
        if (isCanonical) add(badge(if (isExternal) "Dropped file" else "Keep this one"))
    }

    private fun buildActionRow() = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(10), 0)).apply {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT

        // An external candidate has no place in the project, so it cannot be opened as one.
        if (!isExternal || !isCanonical) {
            add(ActionLink("Open") { onOpen(member) })
        }
        add(ActionLink("Show in files") { onReveal(member) })

        // The candidate of an external check is fixed as canonical — it is not part of the
        // project, so no project file can take its place. Offering the choice there would be
        // a control that does nothing.
        if (!isCanonical && !inExternalCluster) {
            add(ActionLink("Keep this instead") { onMakeCanonical(member) })
        }
        if (!isCanonical) {
            add(ActionLink("Delete…") { onDelete(member) })
        }
    }

    private fun badge(text: String) = JBLabel(text, SwingConstants.CENTER).apply {
        border = JBUI.Borders.empty(1, 6)
        isOpaque = true
        background = ColorUtil.withAlpha(JBColor.BLUE, 0.12)
        foreground = JBColor.BLUE
        font = font.deriveFont(font.size2D - 1f)
    }
}
