/**
 * Git convention plugin that provides git tagging functionality.
 *
 * This plugin only provides git tagging - git info resolution is handled
 * by individual projects using the Palantir gradle-git-version plugin directly.
 */

// Add our custom git tagging task
val ensureGitTags by tasks.registering(GitTaggingTask::class) {
    group = "git"
    description = "Ensure current commit is tagged with the current version"
    version.set(providers.provider { project.version.toString() })
}
