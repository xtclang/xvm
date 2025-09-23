/**
 * Git convention plugin that provides git information resolution for builds.
 *
 * Registers a `resolveGitInfo` task for git metadata (branch, commit, dirty status).
 * Task implementations are in separate files to keep this plugin clean.
 */

// Create the standard git info resolution task that all projects can use
val resolveGitInfo by tasks.registering(ResolveGitInfoTask::class) {
    group = "build"
    description = "Resolve git information (branch, commit, dirty status) in configuration cache compatible way"

    branchEnv.set(providers.environmentVariable("GH_BRANCH").orElse(""))
    commitEnv.set(providers.environmentVariable("GH_COMMIT").orElse(""))
    version.set(provider { project.version.toString() })
    ciFlag.set(providers.environmentVariable("CI").orElse(""))
    outputFile.set(layout.buildDirectory.file("git-info.properties"))

    // Git state is checked dynamically by running git commands, no file inputs needed
}

// Git tagging task that XDK needs
val ensureGitTags by tasks.registering(GitTaggingTask::class) {
    group = "git"
    description = "Ensure current commit is tagged with the current version"
    version.set(providers.provider { project.version.toString() })
}
