import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_MACK_DIR
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE

plugins {
    id("org.xtclang.build.xdk.versioning")
    alias(libs.plugins.xtc)
}

val xdkTurtleProvider by configurations.registering {
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(tasks.processResources)
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(XDK_ARTIFACT_NAME_MACK_DIR))
    }
}

val compileXtc by tasks.existing {
    enabled = false
}
