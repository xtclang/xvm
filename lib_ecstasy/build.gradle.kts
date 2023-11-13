import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP

/*
 * Build file for the Ecstasy core library of the XDK.
 *
 * This project builds the ecstasy.xtc anb mack.xtc core library files.
 */

plugins {
    id("org.xvm.build.version")
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
    implementation(libs.javatools.unicode)
}

// TODO: Hack; it's better to not directly refer to an included build instead of a configuration (resolvable), as with xtcTurtle.
val unicode = gradle.includedBuild("javatools_unicode")
val unicodeProjectDir = unicode.projectDir
var unicodeResources = File(unicodeProjectDir, "build/resources/unicode")

xtcCompile {
    renameOutput.put("mack.xtc", "javatools_turtle.xtc")
}

sourceSets.main {
    xtc {
        srcDir(xtcTurtle) // mack.x is in a different project, and does not build on its own, hence we add it to the lib_ecstasy source set instead.
    }
    resources {
        // NOTE: We want to avoid rebuilding the unicode files every run, but if they were part of the build, we could
        // just add them as resources here and skip the entire lazy importUnicodeFiles approach.
        // srcDir(unicodeResources)
    }
}

/**
 * This task can update the Unicode data files, if a Unicode release has occurred and provided
 * a new `ucd.all.flat.zip`; that is the only time that the Unicode data files have to be updated.
 *
 * This task is used to force rebuild and input unicode files into our build. This is not part of the
 * common build, but there is no reason to not make it that, now that we have caching parallel
 * Gradle/Maven build infrastructure.  TODO: make this a build task.
 */
val importUnicodeFiles by tasks.registering(Copy::class) {
    group = BUILD_GROUP
    description = "Copy the various Unicode data files from :javatools_unicode to :lib_ecstasy project."

    dependsOn(unicode.task(":run"))
    // TODO: ATM we hardcode the resource directory where the unicode ends up. This doesn't really fit with declarative Gradle/Maven semantics.
    //   So please don't copy this and start reusing it everywhere in your own code. I will get around to it.
    val outputDir = project.layout.projectDirectory.dir("src/main/resources/ecstasy/text")
    outputs.file(outputDir)

    // Copy specification:
    from(fileTree(unicodeResources))
    into(outputDir)
    include("**/Char*.txt")
    include("**/Char*.dat")
    includeEmptyDirs = false
    eachFile {
        relativePath = RelativePath(true, name)
        logger.lifecycle("$prefix Copying unicode file: ${file.absolutePath} to ${outputDir.asFile.absolutePath}.")
    }

    doLast {
        logger.lifecycle("$prefix Unicode files copied to ${outputDir.asFile}. Please verify your git diff, test and commit.")
        fileTree(outputDir).forEach {
            logger.lifecycle("$prefix     $it")
        }
    }
}
