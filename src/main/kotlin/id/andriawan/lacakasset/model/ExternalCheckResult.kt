package id.andriawan.lacakasset.model

/**
 * Outcome of checking a file that is not part of the project against the project's drawables.
 *
 * The candidate is never copied into the project and nothing is written to disk.
 */
sealed interface ExternalCheckResult {

    /**
     * [cluster] holds the candidate together with its matches, with the candidate as the
     * canonical member. It is null when nothing matched, which is reported differently from
     * an error: the check worked, the answer was "no".
     */
    data class Completed(val cluster: DrawableCluster?, val candidateName: String) : ExternalCheckResult

    data class Error(val message: String) : ExternalCheckResult
}
