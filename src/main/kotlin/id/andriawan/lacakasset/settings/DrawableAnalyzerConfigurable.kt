package id.andriawan.lacakasset.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class DrawableAnalyzerConfigurable(project: Project) : BoundConfigurable("Lacak Asset") {

    private val settings = DrawableAnalyzerSettings.getInstance(project)

    override fun createPanel() = panel {
        group("Scanning") {
            row("Similarity threshold (%):") {
                spinner(50..100, 5)
                    .bindIntValue(settings.state::similarityThreshold)
                    .comment("Minimum similarity percentage to flag as duplicate (default: 90)")
            }

            row {
                checkBox("Include XML vector drawables")
                    .bindSelected(settings.state::includeXmlDrawables)
            }

            row {
                checkBox("Show outdated banner on file changes")
                    .bindSelected(settings.state::showOutdatedBanner)
            }
        }

        group("Exclusions") {
            row("Excluded directories:") {
                textField()
                    .align(AlignX.FILL)
                    .comment("Comma-separated directory names to exclude from scanning")
                    .applyToComponent {
                        text = settings.state.excludedDirectories ?: ""
                    }
            }
        }
    }

    override fun apply() {
        super.apply()
    }
}
