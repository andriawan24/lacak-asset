package id.andriawan.lacakasset.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "LacakAssetSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class DrawableAnalyzerSettings : SimplePersistentStateComponent<DrawableAnalyzerSettings.State>(State()) {

    class State : BaseState() {
        var similarityThreshold by property(90)
        var excludedDirectories by string("")
        var includeXmlDrawables by property(true)
        var showOutdatedBanner by property(true)
    }

    companion object {
        fun getInstance(project: Project): DrawableAnalyzerSettings {
            return project.getService(DrawableAnalyzerSettings::class.java)
        }
    }
}
