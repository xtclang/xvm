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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
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

/**
 * Download the ucd zip file from the unicode site, if it does not exist.
 */
val downloadUcdFlatZip = tasks.register<Download>("downloadUcdFlatZip") {
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
    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

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
            executable = javaLauncher.get().executablePath.asFile.absolutePath
            mainClass.set("org.xvm.tool.BuildUnicodeTables")
            classpath = taskClasspath
            args = listOf(
                ucdZipFile.get().asFile.absolutePath,
                outputDir.get().dir("ecstasy/text").asFile.absolutePath
            )
        }

        val generatedDatDir = outputDir.get().dir("ecstasy/text").asFile
        logger.lifecycle("""

            ================================================================================
            [javatools_unicode] Unicode .dat files generated successfully!
            [javatools_unicode] Location: ${generatedDatDir.absolutePath}

            [javatools_unicode] Next steps:
            [javatools_unicode] 1. Review the generated .dat and .txt files
            [javatools_unicode] 2. Copy them to: lib_ecstasy/src/main/resources/ecstasy/text/
            [javatools_unicode] 3. Update Char.x to match the new .dat file data
            [javatools_unicode] 4. Commit the updated files to the repository
            ================================================================================

        """.trimIndent())
    }
}

/**
 * Explicit maintenance task. Generated tables have their own output directory and
 * are reviewed before being copied into lib_ecstasy's checked-in resources.
 * Normal builds continue to use those checked-in tables.
 */
val rebuildUnicodeTables = tasks.register<RebuildUnicodeTablesTask>("rebuildUnicodeTables") {
    group = BUILD_GROUP
    description = "Regenerate Unicode tables for review without overwriting processed resources."

    val rebuildUnicode = xdkProperties.booleanValue("org.xtclang.unicode.rebuild", false)
    onlyIf { rebuildUnicode }

    dependsOn(downloadUcdFlatZip)
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    outputDir.set(layout.buildDirectory.dir("generated/unicode"))
    ucdZipFile.set(layout.buildDirectory.file("ucd/ucd.all.flat.zip"))
    taskClasspath.from(sourceSets.main.map { it.runtimeClasspath })
}
