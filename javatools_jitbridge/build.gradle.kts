import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_JAVATOOLS_JAR

/*
 * Build file for the JavaTools "bridge" for JIT (aka "_native") module that is used to connect the
 * Java runtime to the Ecstasy type system.
 */

plugins {
    id("org.xtclang.build.java")
}

// Provider configuration to make this jar available to the XDK distribution
val xdkJavaToolsProvider by configurations.registering {
    description = "Provider configuration for the javatools-jitbridge jar"
// Note to ML: the build sill works without these
//    isCanBeResolved = false
//    isCanBeConsumed = true
    outgoing.artifact(tasks.jar)
    attributes {
// Note to ML: the build sill works without this
//        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(XDK_ARTIFACT_NAME_JAVATOOLS_JAR))
    }
}

dependencies {
    implementation(libs.javatools)
}
