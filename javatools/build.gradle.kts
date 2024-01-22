import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_JAVATOOLS_FATJAR
import org.gradle.api.attributes.LibraryElements.CLASSES
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
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

private val checkJarProperty = "org.xtclang.javatools.sanityCheckJar"

val semanticVersion: SemanticVersion by extra

val xdkJavaToolsUtils by configurations.registering {
    description = "Consumer configuration of the classes for the XVM Java Tools Utils project (version $version)"
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

/**
 * TODO: Someone please determine if this is something we should fix or not:
 *
 * We add the implicits.x file to the resource set, to both make it part of the javatools.jar + available.
 * IntelliJ may warn that we have a "duplicate resource folder" if this is executed by a Run/Debug configuration.
 * This is because lib_ecstasy is the place where these resources are originally placed. I'm not sure if
 * they need to be there to be compiled into the ecstasy.xtc binary, in order for the launchers to work, or
 * if they only need to be in the javatools.jar. If the latter is the case, we should move them. If the
 * former is the case, they should be resolved from the the built ecstasy.xtc module on the module path
 * anyway.
 *
 * We also need to refer to the "mack" module during runtime in the debugger. I suppose we can't access that
 * due to a similar reason as above - IntelliJ needs a reference to it to be able to invoke it in
 * the debug session, and it resides in a different module too (javatools_turtle). Seems weird that it
 * doesn't get that from the ecstasy.xtc file that actually IS on the module path in the debug run.
 */
sourceSets {
    main {
        resources {
            srcDir(file(xdkImplicitsFile.parent)) // May trigger a warning in your IDE, since we are referencing someone else's (javatools_turtle) main resource path. IDEs like to have one.
        }
    }
}

val jar by tasks.existing(Jar::class) {
    inputs.property("manifestSemanticVersion") {
        semanticVersion.toString()
    }
    inputs.property("manifestVersion") {
        version
    }
    archiveBaseName = "javatools"

    // TODO: It may be fewer special cases if we just add to the source sets from these dependencies, but it's not
    //  apparent how to get that correct for includedBuilds.
    from(xdkJavaToolsUtils)

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
}

val assemble by tasks.existing {
    if (getXdkPropertyAndTaskInputBool(checkJarProperty)) {
        dependsOn(sanityCheckJar) // "assemble" already depends on jar by default in the Java build life cycle
    }
}

val sanityCheckJar by tasks.registering {
    group = VERIFICATION_GROUP
    description =
        "If the properties are enabled, verify that the javatools.jar file is sane (has all expected packages and files), and optionally, that it has a certain number of entries."

    // These getters register the property provider for the keys used as input to this task. Hopefully
    // that is as lazy and can be, and will also at the same time detect, e.g. that a varibale based
    // on a system property has changed values, without anyone touching the actual build.
    val checkJar = getXdkPropertyAndTaskInputBool(checkJarProperty)
    val expectedEntryCount = getXdkPropertyAndTaskInputInt("org.xtclang.javatools.verifyJar.expectedFileCount", -1)

    logger.info("$prefix Configuring sanityCheckJar task (enabled: $checkJar, expected entry count: $expectedEntryCount)")

    dependsOn(jar)
    inputs.files(jar)

    onlyIf {
        checkJar
    }

    doLast {
        logger.lifecycle("$prefix Sanity checking integrity of generated jar file...")
        // Check for contents from both javatools_utils and javatools + implicit.x
        DebugBuild.verifyJarFileContents(
            project,
            listOfNotNull(
                "implicit.x", // verify the implicits are in the jar
                "org/xvm/tool/Compiler", // verify the javatools package is in there, including Compiler and Runner
                "org/xvm/tool/Runner",
                "org/xvm/util/Severity" // verify the
            ),
            expectedEntryCount
        )

        logger.lifecycle("$prefix Sanity check of javatools.jar completed successfully.")
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
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(XDK_ARTIFACT_NAME_JAVATOOLS_FATJAR))
    }
}
