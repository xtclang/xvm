/*
 * Build file for the Unicode tools portion of the XDK.
 *
 * Technically, this only needs to be built and run when new versions of the Unicode standard are
 * released, and when that occurs, the code in Char.x also has to be updated (to match the .dat file
 * data) using the values in the *.txt files that are output by running this.
 */

import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.xdk.build.java)
    alias(libs.plugins.download)
}

// Access xdkProperties extension (provided by Java convention plugin)

dependencies {
    implementation(libs.bundles.unicode)
    implementation(libs.javatools.utils)
}

val unicodeUcdUrl = "https://unicode.org/Public/UCD/latest/ucdxml/ucd.all.flat.zip"
val processedResourcesDir = tasks.processResources.get().outputs.files.singleFile

/**
 * Type safe "jar" task accessor.
 */
val jar by tasks.existing(Jar::class)

/**
 * Download the ucd zip file from the unicode site, if it does not exist.
 */
val downloadUcdFlatZip by tasks.registering(Download::class) {
    val rebuildUnicode = xdkProperties.booleanValue("org.xtclang.unicode.rebuild", false)
    onlyIf { rebuildUnicode }

    src(unicodeUcdUrl)
    overwrite(false)
    onlyIfModified(true)
    quiet(false)
    dest(layout.buildDirectory.dir("ucd"))
}

/**
 * Abstract task for building unicode tables with proper configuration cache support.
 */
abstract class RebuildUnicodeTablesTask : DefaultTask() {
    @get:InputFile
    abstract val unicodeJar: RegularFileProperty

    @get:InputFile
    abstract val ucdZipFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Classpath
    abstract val taskClasspath: ConfigurableFileCollection

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun rebuildTables() {
        logger.lifecycle("[javatools_unicode] Rebuilding unicode tables...")
        logger.lifecycle("[javatools_unicode] Downloaded unicode file: ${ucdZipFile.get().asFile.absolutePath}")

        execOperations.javaexec {
            mainClass.set("org.xvm.tool.BuildUnicodeTables")
            classpath = taskClasspath
            args = listOf(
                ucdZipFile.get().asFile.absolutePath,
                outputDir.get().dir("ecstasy/text").asFile.absolutePath
            )
        }
    }
}

/**
 * Build unicode tables, and put them under the build directory.
 *
 * For a normal run, the unicode resources are already copied to the build directory
 * by the processResources task, which as part of any default Java Plugin build lifecycle,
 * will copy the src/<sourceSet>/resources directory to build/resources/<sourceSet>
 * In that case, when resolveUnicodeTables is set to false, the only thing this task does
 * is add the processResources outputs as its own outputs. If it's true, we will overwrite
 * those resources to the build folder, and optionally, copy them to replace the source
 * folder resources.
 *
 * We never execute this task explicitly, but we do declare a consumable coniguration that
 * contains the output of this task, forcing it to run (and maybe rebuild unicode files) if
 * anyone wants to resolve the config. The lib_ecstasy project adds this configuration to
 * its incoming resources, which means that lib_ecstasy will include them in the ecstasy.xtc
 * module. All we need to do is add the configuration as a resource for lib_ecstasy.
 */
val rebuildUnicodeTables by tasks.registering(RebuildUnicodeTablesTask::class) {
    group = BUILD_GROUP
    description = "If the unicode files should be regenerated, generate them from the build tool, and place them under the build resources."

    val rebuildUnicode = xdkProperties.booleanValue("org.xtclang.unicode.rebuild", false)

    dependsOn(jar)
    outputDir.set(layout.dir(provider { processedResourcesDir }))

    onlyIf { rebuildUnicode }

    if (rebuildUnicode) {
        dependsOn(downloadUcdFlatZip)
        unicodeJar.set(jar.flatMap { it.archiveFile })
        ucdZipFile.set(layout.buildDirectory.file("ucd/ucd.all.flat.zip"))
        taskClasspath.from(configurations.runtimeClasspath, jar)
    }
}
