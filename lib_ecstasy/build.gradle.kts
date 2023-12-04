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

/**
 * This task can update the Unicode data files, if a Unicode release has occurred and provided
 * a new `ucd.all.flat.zip`; that is the only time that the Unicode data files have to be updated.
 *
 * This task is used to force rebuild and input unicode files into our build. This is not part of the
 * common build, but there is no reason to not make it that, now that we have caching parallel
 * Gradle/Maven build infrastructure.
 */

// TODO: We do NOT want the unicode consumer as a project dependency. We must resolve it in-task somehow.
/*
val importUnicodeFiles by tasks.registering {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Copy the various Unicode data files from :javatools_unicode to :lib_ecstasy project."

    logger.warn("$prefix '$name' is currently broken. There is a reported issue, and we will get to this very soon.")

    val update = System.getenv("XDK_UPDATE_UNICODE") == "true"
    onlyIf {
        update
    }

    //alwaysRerunTask()
    dependsOn(gradle.includedBuild("javatools_unicode").task(":run"))
    //dependsOn(xtcUnicodeConsumer)
    //inputs.files(xtcUnicodeConsumer)

    val libEcstasy = project(XdkVersionHandler.semanticVersionFor(libs.xdk.ecstasy).artifactId)
    val outputDir = File(libEcstasy.projectDir, "src/main/resources/ecstasy/text2")
    outputs.dir(outputDir)

    doLast {
        logger.lifecycle("$prefix Trying to write unicode tables to ${outputDir.absolutePath}...")
        logger.lifecycle("$prefix Provider files: ${xtcUnicodeConsumer.get().resolve()}")
        fileTree(xtcUnicodeConsumer).forEach {
            println(" TODO: WORK : $it")
        }

        copy {
            from(fileTree(xtcUnicodeConsumer)) {
                into(outputDir)
            }
            includeEmptyDirs = false
            eachFile {
                relativePath = RelativePath(true, name)
                logger.lifecycle("$prefix Copying unicode file: ${file.absolutePath} to ${outputDir.absolutePath}.")
            }
        }
        logger.lifecycle("$prefix Unicode files copied to ${outputDir.absolutePath}. Please verify your git diff, test and commit.")
        fileTree(outputDir).forEach {
            logger.lifecycle("$prefix     Destination: $it")
        }
    }
}
*/
