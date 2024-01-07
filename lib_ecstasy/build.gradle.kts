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
    id("org.xtclang.build.version")
    alias(libs.plugins.xtc)
}

val xdkTurtle by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("mack-dir"))
    }
}

dependencies {
    // TODO: Find out why xdkJavaTools is not an unstable API, while xdkTurtle and xdkUnicode are.
    xdkJavaTools(libs.javatools)
    @Suppress("UnstableApiUsage")
    xdkTurtle(libs.javatools.turtle) // A dependency declaration like this works equally well if we are working with an included build/project or with an artifact. This is exactly what we want.
}

val compileXtc by tasks.existing(XtcCompileTask::class) {
    outputFilename("mack.xtc" to "javatools_turtle.xtc")
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
            srcDir(xdkTurtle)
        }
        resources {
            // Skip the local unicode files if we are in "rebuild unicode" mode.
            if (xdkBuild.rebuildUnicode()) {
                exclude("**/ecstasy/text**")
            }
        }
    }
}

// We need extra resources.
val processResources by tasks.existing(ProcessResources::class) {
    val rebuildUnicode = xdkBuild.rebuildUnicode()

// We can't use onlyIf here, since we need processResources to copy the src/main/resources files to the build, from where
// they are picked up by the compileXtc tasks. CompileXtc respects Gradle semantics and allows for a processResources
// (default is a plain lifecycle intra-project copy) to modify the resources, if needed.
    if (rebuildUnicode) {
        val javaToolsUnicode = gradle.includedBuild("javatools_unicode")
        val rebuildUnicodeOutput = File(
            javaToolsUnicode.projectDir,
            "build/resources/"
        ) // TODO: Use configs for dependencies instead, it's less hard coded.
        dependsOn(javaToolsUnicode.task(":rebuildUnicodeTables"))
        from(rebuildUnicodeOutput)
        doLast {
            printTaskInputs()
            printTaskOutputs()
            printTaskDependencies()
// TODO: Add another task that overwrites the source code with the results? Or do we want to do that manually?
            logger.warn("$prefix *** Rebuilt the unicode tables. New tables are the '$name' outputs.")
            logger.warn("$prefix *** Please copy the files manually to the lib-ecstasy src/main/resources directory and commit, if you want to update the pre-built unicode tables in source control.")
        }
        return@existing
    }

    doLast {
        logger.lifecycle("$prefix Using pre-built unicode tables from lib-ecstasy src/main/resources directory.")
    }
}
