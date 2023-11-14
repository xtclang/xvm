/*
 * Build file for the Unicode tools portion of the XDK.
 *
 * Technically, this only needs to be built and run when new versions of the Unicode standard are
 * released, and when that occurs, the code in Char.x also has to be updated (to match the .dat file
 * data) using the values in the *.txt files that are output by running this.
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

/**
 * Download the ucd zip file from the unicode site, if it does not exist.
 */
val downloadUcdFlatZip by tasks.registering(Download::class) {
    val url = "https://unicode.org/Public/UCD/latest/ucdxml/ucd.all.flat.zip"
    src(url)
    overwrite(false)
    onlyIfModified(true)
    quiet(false)
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

    dependsOn(jar)
    dependsOn(downloadUcdFlatZip)

    val buildDir = project.layout.buildDirectory
    outputs.dir(buildDir.dir("resources/unicode/"))
    outputs.dir(buildDir.dir("resources/unicode/tables"))

    alwaysRerunTask()

    doLast {
        val unicodeJar = jar.get().archiveFile
        val localUcdZip = downloadUcdFlatZip.get().outputs.files.singleFile
        logger.lifecycle("Downloaded unicode file: ${localUcdZip.absolutePath}, and the ucd zip: ${localUcdZip.absolutePath}")
        javaexec {
            classpath(configurations.runtimeClasspath)
            classpath(unicodeJar)
            args(localUcdZip.absolutePath)
            mainClass.set("org.xvm.tool.BuildUnicodeTables")
        }
    }
}

/*
val xtcUnicodeProvider by configurations.registering {
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(run) {
        type = ArtifactTypeDefinition.DIRECTORY_TYPE
    }
    // TODO: Can likely remove these.
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("unicodeDir"))
    }
}
*/
