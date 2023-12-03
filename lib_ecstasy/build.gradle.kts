import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE

/*
 * Build file for the Ecstasy core library of the XDK.
 *
 * This project builds the ecstasy.xtc anb mack.xtc core library files.
 */

plugins {
    id("org.xtclang.build.version")
    alias(libs.plugins.xtc)
}

val xtcTurtle by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("mackDir"))
    }
}

dependencies {
    @Suppress("UnstableApiUsage")
    xtcTurtle(libs.javatools.turtle) // TODO: Don't matter if we are a project or an artifact, which is great.
    xtcJavaTools(libs.javatools)
}

xtcCompile {
    renameOutput.put("mack.xtc", "javatools_turtle.xtc")
}

sourceSets.main {
    xtc {
        srcDir(xtcTurtle) // mack.x is in a different project, and does not build on its own, hence we add it to the lib_ecstasy source set instead.
    }
}
