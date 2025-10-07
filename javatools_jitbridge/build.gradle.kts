/*
 * Build file for the JavaTools "bridge" for JIT (aka "_native") module that is used to connect the
 * Java runtime to the Ecstasy type system.
 */

plugins {
    id("org.xtclang.build.xdk.versioning")
    id("org.xtclang.build.java")
}

// Special configuration for javatools-jitbridge - this is a binary blob that gets
// loaded by the runtime via disk probing, not through normal JAR classpath resolution.
// It should be in the distribution but not participate in normal JAR/dependency resolution.
//
// Explicit configuration for providing jitbridge JAR as binary blob to XDK distribution.
// This JAR is NOT for classpath use - it's loaded by runtime via disk probing.
// The XDK build consumes this via libs.javatools.jitbridge dependency with matching attributes.
// See: xdk/build.gradle.kts:29-37 (consumer) and gradle/libs.versions.toml:73 (version catalog)
val xdkJavaToolsJitBridgeProvider by configurations.registering {
    description = "Provides javatools-jitbridge JAR as native binary blob for XDK distribution (NOT classpath)"
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(tasks.jar)
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("jit-bridge-binary"))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("jit-bridge-binary"))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("native-runtime-blob"))
    }
}

// Note: javatools-jitbridge.jar is built as regular JAR but treated as binary blob by runtime

dependencies {
    implementation(libs.javatools)
    implementation(libs.javatools.utils)  // Needed for org.xvm.util.Handy.require
}
