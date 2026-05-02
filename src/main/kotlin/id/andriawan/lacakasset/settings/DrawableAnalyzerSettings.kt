package id.andriawan.lacakasset.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import id.andriawan.lacakasset.model.DrawableFormat

@Service(Service.Level.PROJECT)
@State(
    name = "LacakAssetSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class DrawableAnalyzerSettings : SimplePersistentStateComponent<DrawableAnalyzerSettings.State>(State()) {

    class State : BaseState() {
        var similarityThreshold by property(90)
        var excludedDirectories by string("")
        var includePng by property(true)
        var includeJpg by property(true)
        var includeWebp by property(true)
        var includeSvg by property(true)
        var includeAndroidVector by property(true)
        var showOutdatedBanner by property(true)

        fun enabledFormats(): Set<DrawableFormat> = buildSet {
            if (includePng) add(DrawableFormat.PNG)
            if (includeJpg) add(DrawableFormat.JPG)
            if (includeWebp) add(DrawableFormat.WEBP)
            if (includeSvg) add(DrawableFormat.SVG)
            if (includeAndroidVector) add(DrawableFormat.ANDROID_VECTOR)
        }
    }

    companion object {
        fun getInstance(project: Project): DrawableAnalyzerSettings {
            return project.getService(DrawableAnalyzerSettings::class.java)
        }
    }
}
