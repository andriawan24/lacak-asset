package id.andriawan.lacakasset.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class DrawableAnalyzerConfigurable(project: Project) : BoundConfigurable("Lacak Asset") {

    private val settings = DrawableAnalyzerSettings.getInstance(project)

    override fun createPanel() = panel {
        group("Scanning") {
            row("Similarity threshold (%):") {
                // Matches the tool window slider's range: comparison retains nothing below 70%.
                spinner(70..100, 5)
                    .bindIntValue(settings.state::similarityThreshold)
                    .comment("Starting position of the tool window's threshold slider. Default: 90.")
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
                    .bindText(
                        { settings.state.excludedDirectories ?: "" },
                        { settings.state.excludedDirectories = it.trim() }
                    )
                    .comment("Comma-separated directory names to skip, for example build, generated, sampledata.")
            }
        }
    }
}
