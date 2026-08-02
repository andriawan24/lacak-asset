package id.andriawan.lacakasset.service

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.util.Processor
import id.andriawan.lacakasset.model.HashedDrawable

/**
 * Counts, approximately, how much a drawable is used.
 *
 * This informs which copy of a duplicate is worth keeping. It counts the project files
 * mentioning the resource name — a proxy, not an exact usage count, because a resource is
 * referenced through several syntaxes (`R.drawable.x`, `@drawable/x`, `Res.drawable.x`) and
 * some references are not statically visible at all.
 *
 * A proxy is adequate here: the count only ranks members of one cluster against each other,
 * and it is never used to decide whether a file is safe to delete. That decision belongs to
 * Safe Delete, which does its own real search.
 *
 * Counting is a project-wide search, so it runs only for the cluster the user selected
 * rather than for every cluster in a scan.
 */
class DrawableReferenceCounter(private val project: Project) {

    private val log = Logger.getInstance(DrawableReferenceCounter::class.java)

    suspend fun countFor(members: List<HashedDrawable>): Map<String, Int> {
        // The word index is unavailable while indexing, and the ranking is only advisory.
        if (DumbService.getInstance(project).isDumb) return emptyMap()

        return try {
            readAction {
                val helper = PsiSearchHelper.getInstance(project)
                val scope = GlobalSearchScope.projectScope(project)

                members.associate { member ->
                    member.file.path to countFilesMentioning(helper, scope, member.file.resourceName)
                }
            }
        } catch (e: Exception) {
            log.warn("Reference counting failed; falling back to the remaining ranking rules", e)
            emptyMap()
        }
    }

    private fun countFilesMentioning(
        helper: PsiSearchHelper,
        scope: GlobalSearchScope,
        resourceName: String
    ): Int {
        if (resourceName.isBlank()) return 0

        var count = 0
        helper.processAllFilesWithWord(
            resourceName,
            scope,
            Processor {
                count++
                true
            },
            true
        )
        return count
    }
}
