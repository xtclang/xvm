/*
 * Build file for the Unicode tools portion of the XDK.
 *
 * Technically, this only needs to be built and run when new versions of the Unicode standard are
 * released, and when that occurs, the code in Char.x also has to be updated (to match the .dat file
 * data) using the values in the *.txt files that are outputted by running this.
 */

import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.plugins.ApplicationPlugin.APPLICATION_GROUP

plugins {
    id("org.xvm.build.java")
    alias(libs.plugins.download)
}

dependencies {
    implementation(libs.bundles.unicode)
    implementation(libs.javatools.utils)
}

internal val ucdZip = "http://unicode.org/Public/UCD/latest/ucdxml/ucd.all.flat.zip"

/**
 * Download the ucd zip file from the unicode site, if it does not exist.
 */
val downloadUcdFlatZip by tasks.registering(Download::class) {
    overwrite(false)
    src(ucdZip)
    dest(project.mkdir(project.layout.buildDirectory.dir("ucd")))
}

/**
 * Build the javatools-unicode jar
 *    existing -> getting (lazy -> eager)
 *    registering -> creating (lazy -> eager)
 */
val jar by tasks.existing(Jar::class)

val run by tasks.registering {
    group = APPLICATION_GROUP
    description = "Run the BuildUnicodeTables tool, after downloading the latest available data. This rebuilds our Unicode tables."
    dependsOn(tasks.assemble, download)
    outputs.dir(project.layout.buildDirectory.dir("resources/main/unicode"))
    doLast {
        val unicodeJar = jar.get().archiveFile
        val localUcdZip = downloadUcdFlatZip.get().outputs.files.singleFile
        logger.lifecycle("Downloaded unicode file: ${localUcdZip.absolutePath}")
        javaexec {
            classpath(configurations.runtimeClasspath, unicodeJar)
            args(localUcdZip.absolutePath)
            mainClass.set("org.xvm.tool.BuildUnicodeTables")
        }
    }
}

