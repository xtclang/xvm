/**
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    id("org.xvm.build.java")
    alias(libs.plugins.tasktree)
}

val xtcJavaToolsProvider by configurations.registering {
    description = "Provider configuration of the The XVM Java Tools jar artifact: 'javatools-$version.jar'"
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(tasks.jar)
}

val xtcJavaToolsUtils by configurations.registering {
    description = "Consumer configuration of the XVM Java Tools Utils jar artifact: 'javatools_utils-$version.jar'"
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
    }
}

dependencies {
    @Suppress("UnstableApiUsage")
    xtcJavaToolsUtils(libs.javatools.utils)
    compileOnly(libs.javatools.utils) // We include the javautils utils in the Javatools uber-jar, so we need it only as compile only.
    testImplementation(libs.javatools.utils)
}

val jarTask = tasks.named<Jar>("jar") {
    // TODO Hack: this should be changed to use a consumable configuration, and/or moving javautils as a subproject, as it is never used standalone, just as part of a fat javatools jar.
    val implicitsPath = file("$compositeRootProjectDirectory/lib_ecstasy/src/main/resources/implicit.x").absolutePath

    archiveBaseName = "javatools"

    from(xtcJavaToolsUtils)
    from(implicitsPath)

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

val buildTask = tasks.named("build") {
    dependsOn(jarTask)
    finalizedBy("sanityCheckJar")
}

val sanityCheckJar by tasks.registering {
    val expectedEntryCount = 1256
    dependsOn(buildTask)
    doLast {
        logger.info("$prefix Sanity checking integrity of generated jar file.")
        verifyJarFileContents(
            project,
            listOfNotNull(
                "implicit.x",
                "org/xvm/tool/Compiler",
                "org/xvm/util/Severity"),
            expectedEntryCount) // Check for files in both javatools_utils and javatools + implicit.x
    }
}
