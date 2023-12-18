import org.gradle.api.attributes.LibraryElements.CLASSES
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP

/**
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.java)
    alias(libs.plugins.tasktree)
}

val semanticVersion: SemanticVersion by extra

val xdkJavaToolsUtils by configurations.registering {
    description = "Consumer configuration of the XVM Java Tools Utils jar artifact: 'javatools_utils-$version.jar'"
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(CLASSES))
    }
}

dependencies {
    @Suppress("UnstableApiUsage")
    xdkJavaToolsUtils(libs.javatools.utils)
    // Normal use case: The javatools-utils is included in the Javatools uber-jar, so we need it only as compile only.
    // compileOnly(libs.javatools.utils)
    // Debugging use case: If we want IDE debugging, executing the Compiler or Runner manually, we need javatools-utils as an implementation dependency.
    implementation(libs.javatools.utils)
    testImplementation(libs.javatools.utils)
}

val jar by tasks.existing(Jar::class) {
    archiveBaseName = "javatools"

    // TODO: It may be fewer special cases if we just add to the source sets from these dependencies, but it's not
    //  apparent how to get that correct for includedBuilds.
    from(xdkJavaToolsUtils)
    from(file(xdkImplicitsPath)) // TODO Hack: this should be changed to use a consumable configuration, and/or moving javautils as a subproject, as it is never used standalone, just as part of a fat javatools jar.

    manifest {
        attributes(
            "Manifest-Version" to "1.0",
            "Xdk-Version" to semanticVersion.toString(),
            "Sealed" to "true",
            "Main-Class" to "org.xvm.tool.Launcher",
            "Name" to "/org/xvm/",
            "Specification-Title" to "xvm",
            "Specification-Version" to version,
            "Specification-Vendor" to "xtclang.org",
            "Implementation-Title" to "xvm-prototype",
            "Implementation-Version" to version,
            "Implementation-Vendor" to "xtclang.org"
        )
    }
    doLast {
        val path = archiveFile.map { it.asFile.absolutePath }
        logger.info("$prefix Finished building Java tools (fat jar including javatools-utils): '$path' as artifact.")
        printTaskOutputs()
    }
}

val assemble by tasks.existing {
    dependsOn(sanityCheckJar) // "assemble" already depends on jar by default in the Java build life cycle
}

val sanityCheckJar by tasks.registering {
    group = VERIFICATION_GROUP
    description = "If the properties are enabled, verify that the javatools.jar file is sane (contains expected packages and files), and optionally, that it has a certain number of entries."

    dependsOn(jar)

    val checkJar = getXdkPropertyBoolean("org.xtclang.javatools.verifyJar")
    onlyIf {
        checkJar
    }
    val expectedEntryCount = getXdkProperty("org.xtclang.javatools.verifyJar.expectedFileCount", "-1").toInt()
    inputs.file(jar.get().archiveFile)
    noOutputs()

    logger.info("$prefix Configuring sanityCheckJar task (enabled: $checkJar, expected entry count: $expectedEntryCount)")
    doLast {
        logger.info("$prefix Sanity checking integrity of generated jar file.")

        DebugBuild.verifyJarFileContents(
            project,
            listOfNotNull(
                "implicit.x", // verify the implicits are in the jar
                "org/xvm/tool/Compiler", // verify the javatools package is in there, including Compiler and Runner
                "org/xvm/tool/Runner",
                "org/xvm/util/Severity" // verify the
            ),
            expectedEntryCount) // Check for files in both javatools_utils and javatools + implicit.x

        logger.info("$prefix Sanity check of javatools.jar completed successfully.")
    }
}

// TODO: Move these to common-plugins, the XDK composite build does use them in some different places.
val xdkJavaToolsProvider by configurations.registering {
    description = "Provider configuration of the The XVM Java Tools jar artifact: 'javatools-$version.jar'"
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(jar)
    attributes {
        attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("javatools-fatjar"))
    }
}