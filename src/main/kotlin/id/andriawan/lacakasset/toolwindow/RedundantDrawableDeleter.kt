package id.andriawan.lacakasset.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiManager
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import id.andriawan.lacakasset.model.DrawableCluster
import id.andriawan.lacakasset.model.HashedDrawable

/**
 * Deletes a redundant copy by handing it to the IDE's Safe Delete refactoring.
 *
 * Usage search, the conflict preview, and undo all come from the platform. Writing our own
 * would mean reimplementing reference resolution for `R.drawable`, `@drawable/`, and Compose
 * `Res.drawable` while still being blind to string-based lookups — so the platform keeps that
 * responsibility, and this class only decides what may be offered for deletion.
 */
class RedundantDrawableDeleter(private val project: Project) {

    /**
     * Confirms if needed and then invokes Safe Delete.
     *
     * Refuses the canonical member and any external candidate: a cluster must keep one copy,
     * and a file outside the project is never ours to remove.
     */
    fun delete(cluster: DrawableCluster, member: HashedDrawable, onDeleted: () -> Unit) {
        if (member.file.path == cluster.canonical.file.path) return
        if (cluster.isExternal && member.file.path == cluster.canonical.file.path) return

        if (cluster.isMixedFormat && !confirmFormatChange(cluster, member)) return

        val psiFile = PsiManager.getInstance(project).findFile(member.file.virtualFile)
        if (psiFile == null) {
            Messages.showErrorDialog(
                project,
                "${member.file.fileName} could not be resolved in this project, so it was not deleted.",
                "Delete Drawable"
            )
            return
        }

        // Safe Delete runs its own confirmation and usage preview; the callback fires after it
        // completes, whether or not the user went through with it, so the caller re-checks the
        // file rather than assuming it is gone.
        SafeDeleteHandler.invoke(project, arrayOf(psiFile), null, true) { onDeleted() }
    }

    /**
     * Deleting across formats swaps a raster for a vector or the reverse, which changes how the
     * asset renders and tints rather than merely removing a copy — so it is named explicitly.
     */
    private fun confirmFormatChange(cluster: DrawableCluster, member: HashedDrawable): Boolean {
        val removed = describeFormat(member)
        val kept = describeFormat(cluster.canonical)
        if (removed == kept) return true

        val answer = Messages.showYesNoDialog(
            project,
            "You are about to delete ${member.file.fileName}, a $removed, and keep " +
                    "${cluster.canonical.file.fileName}, a $kept.\n\n" +
                    "These formats are not interchangeable: rendering, tinting, and the API levels " +
                    "they support can differ. Check the remaining copy before continuing.",
            "Delete Across Formats?",
            "Delete Anyway",
            "Cancel",
            Messages.getWarningIcon()
        )
        return answer == Messages.YES
    }

    private fun describeFormat(member: HashedDrawable): String = when (member.file.format) {
        id.andriawan.lacakasset.model.DrawableFormat.PNG -> "PNG"
        id.andriawan.lacakasset.model.DrawableFormat.JPG -> "JPEG"
        id.andriawan.lacakasset.model.DrawableFormat.WEBP -> "WebP"
        id.andriawan.lacakasset.model.DrawableFormat.SVG -> "SVG"
        id.andriawan.lacakasset.model.DrawableFormat.ANDROID_VECTOR -> "Android vector drawable"
    }
}
