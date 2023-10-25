import org.gradle.api.attributes.LibraryElements.CLASSES
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP

/**
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    id("org.xvm.build.java")
    alias(libs.plugins.tasktree)
}

// TODO: Move these to buildSrc, the XDK composite build does use them in some different places.
val xtcJavaToolsProvider by configurations.registering {
    description = "Provider configuration of the The XVM Java Tools jar artifact: 'javatools-$version.jar'"
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(tasks.jar)
    attributes {
        attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}

val xtcJavaToolsUtils by configurations.registering {
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
    xtcJavaToolsUtils(libs.javatools.utils)
    compileOnly(libs.javatools.utils) // We include the javautils utils in the Javatools uber-jar, so we need it only as compile only.
    testImplementation(libs.javatools.utils)
}

val jar by tasks.existing(Jar::class) {
    archiveBaseName = "javatools"

    // TODO: It may be fewer special cases if we just add to the source sets from these dependencies, but it's not
    //  apparent how to get that correct for includedBuilds.
    from(xtcJavaToolsUtils)
    from(file(xdkImplicitsPath)) // TODO Hack: this should be changed to use a consumable configuration, and/or moving javautils as a subproject, as it is never used standalone, just as part of a fat javatools jar.

    manifest {
        attributes["Manifest-Version"] = "1.0"
        attributes["Sealed"] = "true"
        attributes["Main-Class"] = "org.xvm.tool.Launcher"
        attributes["Name"] = "/org/xvm/"
        attributes["Specification-Title"] = "xvm"
        attributes["Specification-Version"] = version
        attributes["Specification-Vendor"] = "xtclang.org"
        attributes["Implementation-Title"] = "xvm-prototype"
        attributes["Implementation-Version"] = version
        attributes["Implementation-Vendor"] = "xtclang.org"
    }
}

val assemble by tasks.existing {
    // assemble already depends on jar by default in the Java build life cycle
    dependsOn(sanityCheckJar)
}

val sanityCheckJar by tasks.registering {
    group = VERIFICATION_GROUP
    description = "If the properties are enabled, verify that the javatools.jar file is sane (contains expected packages and files), and optionally, that it has a certain number of entries."

    dependsOn(jar)

    val checkJar = getXdkPropertyBoolean("org.xvm.javatools.verifyJar", false)
    onlyIf {
        checkJar
    }
    val expectedEntryCount = getXdkProperty("org.xvm.javatools.verifyJar.expectedFileCount", "-1").toInt()

    logger.lifecycle("$prefix Configuring sanityCheckJar task (enabled: $checkJar, expected entry count: $expectedEntryCount)")
    doLast {
        logger.info("$prefix Sanity checking integrity of generated jar file.")
        verifyJarFileContents(
            project,
            listOfNotNull(
                "implicit.x",
                "org/xvm/tool/Compiler",
                "org/xvm/util/Severity"),
            expectedEntryCount) // Check for files in both javatools_utils and javatools + implicit.x
        logger.lifecycle("$prefix Sanity check of javatools.jar completed successfully.")
    }
}
