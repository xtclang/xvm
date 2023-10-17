/*
 * Build file for the Ecstasy core library of the XDK.
 *
 * This project builds the ecstasy.xtc anb mack.xtc core library files.
 */

plugins {
    id("org.xvm.build.version")
    alias(libs.plugins.xtc)}

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

xtcCompile {
    renameOutput.put("mack.xtc", "javatools_turtle.xtc")
}

sourceSets.main {
    xtc {
        srcDir(xtcTurtle)
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
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Copy the various Unicode data files from :javatools_unicode to :lib_ecstasy project."
    dependsOn(gradle.includedBuild("javatools_unicode").task(":run"))
    val unicodeResources = "${gradle.includedBuild("javatools_unicode").projectDir}/build/resources/unicode"
    copy {
        from(file(unicodeResources))
        include("Char*.txt", "Char*.dat")
        into(project.layout.projectDirectory.dir("src/main/resources/ecstasy/text"))
    }
}
