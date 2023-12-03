/**
 * This is a featherweight precompiled script plugin that helps us set the semantic version
 * of all XDK components/subprojects. The plugin itself should not do any manipulation of
 * versions, since it can be applied from arbitrary thord party code.
 *
 * Any XTC project will have an extension with its resolved SemanticVersion
 */

import XdkBuildLogic.Companion.XDK_TASK_GROUP_VERSION
import org.gradle.api.Project

plugins {
    id("org.xtclang.build.debug")
}

internal val versions = xdkBuildLogic.xdkVersionHandler()
xdkBuildLogic.validateGradle()

val semanticVersion by extra {
    versions.assignXdkVersion(XdkVersionHandler.resolveCatalogSemanticVersion(project))
}

val bumpProjectVersion by tasks.registering {
    group = XDK_TASK_GROUP_VERSION
    description = "Increase the version of the current XDK build with one."
    doLast {
        versions.updateVersionCatalogFile(false)
    }
}

val bumpProjectVersionToSnapshot by tasks.registering {
    group = XDK_TASK_GROUP_VERSION
    description = "Increase the version of the current XDK build with one, and suffix the new version string with '-SNAPSHOT'."
    doLast {
        xdkBuildLogic.xdkVersionHandler().updateVersionCatalogFile(true)
    }
}
