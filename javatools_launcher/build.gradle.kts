/*
 * Build file for the javatools_launcher project.
 *
 * The launcher programs need to be built on different hardware/OS combinations, so this project is
 * not currently automated. Currently, the pre-built executables for different platforms are placed
 * in the reources of this module.
 *
 * TODO: We could add a mechanism to native compile this, but it would have to cross compile
 *    from all potential build platforms to native launchers for all supported platforms. The
 *    Gradle native plugins for c/cpp takes us almost all the way there, but misses some of
 *    the cross compilation abilities.
 */

plugins {
    base
}

val launcherExecutableDir = layout.projectDirectory.dir("src/main/resources/exe")

val processLauncherResources by tasks.registering(Copy::class) {
    from(files(launcherExecutableDir))
    // TODO: This may cause a warning in IDEA with non-delegated compilation, but it is safe.
    //   "Cannot resolve resource filtering of . IDEA may fail to build project. Consider using delegated build (enabled by default)."
    exclude("**/README.md")
    eachFile {
        relativePath = RelativePath(true, name)
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.file("bin"))
}

val assemble by tasks.existing {
    dependsOn(processLauncherResources)
    doLast {
        logger.info("$prefix Finished assembling launcher resources into Gradle build.")
    }
}

val xtcLauncherBinaries by configurations.registering {
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(processLauncherResources)
}
