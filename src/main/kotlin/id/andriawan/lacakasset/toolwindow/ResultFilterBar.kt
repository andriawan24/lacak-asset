package id.andriawan.lacakasset.toolwindow

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import id.andriawan.lacakasset.model.DrawableCluster
import id.andriawan.lacakasset.model.DrawableFormat
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel
import javax.swing.JSlider

/**
 * Threshold and scope controls for the results already in memory.
 *
 * Everything here filters what is displayed; nothing triggers a scan or writes to
 * configuration. The threshold works because comparison retains pairs down to a fixed floor,
 * so lowering it reveals pairs that were already computed.
 */
class ResultFilterBar(
    initialThresholdPercent: Int,
    private val onChanged: () -> Unit
) : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(10), JBUI.scale(2))) {

    companion object {
        const val MIN_THRESHOLD_PERCENT = 70
        const val MAX_THRESHOLD_PERCENT = 100
        private const val ALL_MODULES = "All modules"
        private const val ALL_FORMATS = "All formats"
    }

    private val thresholdValueLabel = JBLabel("")

    private val thresholdSlider = JSlider(
        MIN_THRESHOLD_PERCENT,
        MAX_THRESHOLD_PERCENT,
        initialThresholdPercent.coerceIn(MIN_THRESHOLD_PERCENT, MAX_THRESHOLD_PERCENT)
    ).apply {
        preferredSize = JBUI.size(140, 24)
        toolTipText = "Only show groups whose copies are at least this similar"
        addChangeListener {
            updateThresholdLabel()
            // Re-cluster continuously: grouping in memory is fast enough to track the drag.
            onChanged()
        }
    }

    private val moduleCombo = ComboBox(DefaultComboBoxModel(arrayOf(ALL_MODULES))).apply {
        toolTipText = "Show only groups with a copy in this module"
        addActionListener { onChanged() }
    }

    private val formatCombo = ComboBox(DefaultComboBoxModel(arrayOf(ALL_FORMATS))).apply {
        toolTipText = "Show only groups containing this format"
        addActionListener { onChanged() }
    }

    init {
        border = JBUI.Borders.empty(2, 6)
        add(JBLabel("Similarity:").apply { foreground = UIUtil.getContextHelpForeground() })
        add(thresholdSlider)
        add(thresholdValueLabel)
        add(moduleCombo)
        add(formatCombo)
        updateThresholdLabel()
    }

    private fun updateThresholdLabel() {
        thresholdValueLabel.text = "${thresholdSlider.value}%+"
    }

    /** Normalized threshold, for comparison against a pair's similarity. */
    val threshold: Double get() = thresholdSlider.value / 100.0

    val hasActiveScopeFilter: Boolean
        get() = moduleCombo.selectedItem != ALL_MODULES || formatCombo.selectedItem != ALL_FORMATS

    /**
     * Repopulates the module and format choices from [clusters], preserving the current
     * selection when it still exists so re-clustering does not silently reset the filters.
     */
    fun refreshChoices(clusters: List<DrawableCluster>) {
        val modules = clusters
            .flatMap { cluster -> cluster.members.map { it.file.modulePath } }
            .distinct()
            .sorted()
        val formats = clusters
            .flatMap { cluster -> cluster.members.map { it.file.format } }
            .distinct()
            .sortedBy { it.name }
            .map { it.name }

        repopulate(moduleCombo, ALL_MODULES, modules)
        repopulate(formatCombo, ALL_FORMATS, formats)
    }

    private fun repopulate(combo: ComboBox<String>, allLabel: String, values: List<String>) {
        val previous = combo.selectedItem as? String
        val items = (listOf(allLabel) + values).toTypedArray()
        if (items.toList() == (0 until combo.itemCount).map { combo.getItemAt(it) }) return

        // Suppressed so repopulating does not fire a change and recurse into a rebuild.
        val listeners = combo.actionListeners
        listeners.forEach { combo.removeActionListener(it) }
        combo.model = DefaultComboBoxModel(items)
        combo.selectedItem = if (previous != null && previous in items) previous else allLabel
        listeners.forEach { combo.addActionListener(it) }
    }

    /** True when [cluster] survives the current module and format selections. */
    fun accepts(cluster: DrawableCluster): Boolean {
        val module = moduleCombo.selectedItem as? String ?: ALL_MODULES
        val format = formatCombo.selectedItem as? String ?: ALL_FORMATS

        // A group is shown when any of its copies matches, so a cross-module group stays
        // visible — and complete — when either of its modules is selected.
        val moduleOk = module == ALL_MODULES || cluster.members.any { it.file.modulePath == module }
        val formatOk = format == ALL_FORMATS || cluster.members.any { it.file.format == DrawableFormat.valueOf(format) }

        return moduleOk && formatOk
    }

    fun clearScopeFilters() {
        moduleCombo.selectedItem = ALL_MODULES
        formatCombo.selectedItem = ALL_FORMATS
    }
}
