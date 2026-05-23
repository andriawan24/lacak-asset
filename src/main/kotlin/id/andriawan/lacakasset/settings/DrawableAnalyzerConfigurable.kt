package id.andriawan.lacakasset.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import javax.swing.JTextField

class DrawableAnalyzerConfigurable(project: Project) : BoundConfigurable("Lacak Asset") {

    private val settings = DrawableAnalyzerSettings.getInstance(project)
    private var excludedDirectoriesField: JTextField? = null

    override fun createPanel() = panel {
        group("Scanning") {
            row("Similarity threshold (%):") {
                spinner(50..100, 5)
                    .bindIntValue(settings.state::similarityThreshold)
                    .comment("Minimum similarity percentage to report as a match. Default: 90.")
            }

            row {
                checkBox("Show refresh reminder after drawable changes")
                    .bindSelected(settings.state::showOutdatedBanner)
            }
        }

        group("File Types") {
            row {
                checkBox("PNG (.png)")
                    .bindSelected(settings.state::includePng)
            }
            row {
                checkBox("JPEG (.jpg, .jpeg)")
                    .bindSelected(settings.state::includeJpg)
            }
            row {
                checkBox("WebP (.webp)")
                    .bindSelected(settings.state::includeWebp)
            }
            row {
                checkBox("SVG (.svg)")
                    .bindSelected(settings.state::includeSvg)
            }
            row {
                checkBox("Android Vector Drawable (.xml)")
                    .bindSelected(settings.state::includeAndroidVector)
            }
        }

        group("Exclusions") {
            row("Excluded directories:") {
                textField()
                    .align(AlignX.FILL)
                    .applyToComponent {
                        text = settings.state.excludedDirectories ?: ""
                        excludedDirectoriesField = this
                    }
                    .comment("Comma-separated directory names to skip, for example build, generated, sampledata.")
            }
        }
    }

    override fun apply() {
        super.apply()
        settings.state.excludedDirectories = excludedDirectoriesField?.text?.trim().orEmpty()
    }

    override fun reset() {
        super.reset()
        excludedDirectoriesField?.text = settings.state.excludedDirectories ?: ""
    }

    override fun isModified(): Boolean {
        return super.isModified() ||
                excludedDirectoriesField?.text?.trim().orEmpty() != (settings.state.excludedDirectories ?: "")
    }
}
