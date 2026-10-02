import XdkDistribution.Companion.XDK_ARTIFACT_NAME_MACK_DIR
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.xtclang.plugin.tasks.XtcCompileTask

/*
 * Build file for the Ecstasy core library of the XDK.
 *
 * This project builds the ecstasy.xtc anb mack.xtc core library files.
 */

plugins {
    alias(libs.plugins.xtc)
}

val xdkTurtleConsumer = configurations.register("xdkTurtleConsumer") {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(XDK_ARTIFACT_NAME_MACK_DIR))
    }
}

val xdkUnicodeConsumer = configurations.register("xdkUnicodeConsumer") {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("unicodeDir"))
    }
}

val xdkEcstasyResourcesProvider = configurations.register("xdkEcstasyResourcesProvider") {
    description = "Provider configuration for ecstasy resources (implicit.x and unicode data)"
    isCanBeResolved = false
    isCanBeConsumed = true
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("ecstasy-resources"))
    }
}

artifacts {
    add(xdkEcstasyResourcesProvider.name, layout.projectDirectory.dir("src/main/resources"))
}

dependencies {
    xdkJavaTools(libs.javatools)
    @Suppress("UnstableApiUsage")
    xdkTurtleConsumer(libs.javatools.turtle) // A dependency declaration like this works equally well if we are working with an included build/project or with an artifact. This is exactly what we want.
}

tasks.named<XtcCompileTask>("compileXtc") {
    // Older builds renamed mack.xtc to javatools_turtle.xtc here; the distribution does that now. Delete such a
    // leftover so the output directory, and every module path built from it, holds a single copy of the module.
    val leftover = outputDirectory.map { it.file("javatools_turtle.xtc") }
    doFirst { leftover.get().asFile.delete() }
}

/**
 * Set up source sets. The XTC main source set needs the turtle module as part of the compile, i.e. "mack.x", as it
 * cannot build standalone, for bootstrapping reasons. It would really just be simpler to move mack.x to live beside
 * ecstasy.x, but right now we want to transition to the Gradle build logic without changing semantics form the old
 * world. This shows the flexibility of being Source Set aware, through.
 */
sourceSets {
    main {
        xtc {
            // mack.x is in a different project, and does not build on its own, hence we add it to the lib_ecstasy source set instead.
            srcDir(xdkTurtleConsumer)
        }
    }
}
