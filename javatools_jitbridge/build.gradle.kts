import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_JAVATOOLS_JAR

/*
 * Build file for the JavaTools "bridge" for JIT (aka "_native") module that is used to connect the
 * Java runtime to the Ecstasy type system.
 */

plugins {
    id("org.xtclang.build.java")
}

// Special configuration for javatools-jitbridge - this is a binary blob that gets
// loaded by the runtime via disk probing, not through normal JAR classpath resolution.
// It should be in the distribution but not participate in normal JAR/dependency resolution.
val xdkJavaToolsJitBridgeProvider by configurations.registering {
    description = "Provider configuration specifically for the javatools-jitbridge binary blob"
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(tasks.jar)
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("native-binary"))
        // Tag as native binary blob, not a JAR library
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("native-binary"))
    }
}

// Note: javatools-jitbridge.jar is built as regular JAR but treated as binary blob by runtime

dependencies {
    implementation(libs.javatools)
}
