import XdkDistribution.Companion.XDK_ARTIFACT_NAME_MACK_DIR
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.xtc)
}

val processXtcResources by tasks.existing(Copy::class)

val xdkTurtleProvider by configurations.registering {
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(tasks.processResources) {
        // Also depend on processXtcResources since the XTC plugin adds its output
        // to the source set output, which consumers may access
        builtBy(processXtcResources)
    }
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(XDK_ARTIFACT_NAME_MACK_DIR))
    }
}

val compileXtc by tasks.existing {
    enabled = false
}
