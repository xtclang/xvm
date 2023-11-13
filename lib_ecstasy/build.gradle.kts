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
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("mackDir"))
    }
}

dependencies {
    @Suppress("UnstableApiUsage")
    xtcTurtle(libs.javatools.turtle) // TODO: Don't matter if we are a project or an artifact, which is great.
    xtcJavaTools(libs.javatools)
    implementation(libs.javatools.unicode)
}

// Reference to the unicode table builder. TODO: Should be references through configuration, but that's not a P1 yet.

val unicode = gradle.includedBuild("javatools_unicode")
val unicodeProjectDir = unicode.projectDir
var unicodeResources = File(unicodeProjectDir, "build/resources/unicode")

xtcCompile {
    renameOutput.put("mack.xtc", "javatools_turtle.xtc")
}

sourceSets.main {
    xtc {
        srcDir(xtcTurtle)
    }
    resources {
        // This is a bit backwards, but it basically says "if the resource directory of javatools_unicode is there,
        // use that as resource dir too", and not just the default in the source set (which is src/main/resources)
        // for the main source set, which we are in.
        if (unicodeResources.exists()) {
            logger.lifecycle("$prefix javatools_unicode has a resource directory under build, which will be included in the lib_ecstasy binary. ($unicodeResources)")
        } else {
            logger.lifecycle("$prefix No unicode resources are found.")
        }
        srcDir(unicodeResources)
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
val importUnicodeFiles by tasks.registering {
    group = BUILD_GROUP
    description = "Copy the various Unicode data files from :javatools_unicode to :lib_ecstasy project."
    dependsOn(unicode.task(":run"))
    doLast {
        // Hardcode the resource directory where the unicode ends up.
        val unicodeResources = "${unicode.projectDir}/build/resources/unicode"
        copy {

            from(file(unicodeResources))
            include("Char*.txt", "Char*.dat")
            into(project.layout.projectDirectory.dir("src/main/resources/ecstasy/text"))
        }
    }
}
