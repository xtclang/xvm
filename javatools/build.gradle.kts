import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_JAVATOOLS_FATJAR
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP

/**
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.java)
    alias(libs.plugins.tasktree)
}

val semanticVersion: SemanticVersion by extra

// TODO: Move these to common-plugins, the XDK composite build does use them in some different places.
val xdkJavaToolsProvider by configurations.registering {
    description = "Provider configuration of the The XVM Java Tools jar artifact: 'javatools-$version.jar'"
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(tasks.jar)
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(XDK_ARTIFACT_NAME_JAVATOOLS_FATJAR))
    }
}

dependencies {
    implementation(libs.javatools.utils)
    implementation(libs.javatools.utils)
    implementation(libs.jline)
    testImplementation(libs.javatools.utils)
}

/**
 * TODO: Someone please determine if this is something we should fix or not:
 *
 * We add the implicits.x file to the resource set, to both make it a part of the javatools.jar + available.
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
            // May trigger a warning in your IDE, since we are referencing someone else's
            // (javatools_turtle) main resource path. IDEs like to have one.
            srcDir(file(xdkImplicitsFile.parent))
        }
    }
}

val copyDependencies by tasks.registering(Copy::class) {
    from(configurations.compileClasspath) {
        include("**/*.jar")
    }
    into(layout.buildDirectory.dir("javatools-dependencies"))
    doLast {
        outputs.files.asFileTree.forEach {
            logger.info("$prefix Resolved javatools dependency file: $it")
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

    /*
     * This "from" statement will copy everything in the dependencies section, and our resources
     * to the javatools-<version>.jar. In that respect it's a fat jar.
     *
     * The "copyDependencies" task will output a build directory containing all our dependencies,
     * which are currently just javatools-utils-<version>.jar and jline.
     *
     * Map semantics guarantee that we resolve the "from" input only during the execution phase.
     * We take the destination directory, known to be an output of copyDependencies, and a
     * single directory. This is implicit from the "into" configuration of that task.
     * Then we lazily (with "map") assume that every file in the destination tree is a jar/zip file
     * (we will break if it isn't) and unpack that into the javatools jar that is being built.
     *
     * TODO: an alternative solution would be to leave the run-time dependent libraries "as is" and
     *      use the "Class-Path" attribute of the manifest to point to them.
     */
    from(copyDependencies.map { fileTree(it.destinationDir).map { jarFile -> zipTree(jarFile) }})

    archiveBaseName = "javatools"

    manifest {
        attributes(
            "Manifest-Version" to "1.0",
            "Main-Class" to "org.xvm.tool.Launcher",
            "Name" to "/org/xvm/",
            "Specification-Title" to "xvm",
            "Specification-Version" to version,
            "Specification-Vendor" to "xtclang.org",
            "Implementation-Title" to "xvm-prototype",
            "Implementation-Version" to version,
            "Implementation-Vendor" to "xtclang.org",
            "Sealed" to "true"
        )
    }
}

val assemble by tasks.existing {
    dependsOn(sanityCheckJar) // "assemble" already depends on jar by default in the Java build life cycle
}

val sanityCheckJar by tasks.registering {
    group = VERIFICATION_GROUP
    description =
        "If the properties are enabled, verify that the javatools.jar file is sane " +
        "(contains expected packages and files), and optionally, that it has a certain number of entries."

    dependsOn(jar)

    val checkJar = getXdkPropertyBoolean("org.xtclang.javatools.sanityCheckJar")
    val expectedEntryCount = getXdkPropertyInt("org.xtclang.javatools.verifyJar.expectedFileCount", -1)
    inputs.properties("sanityCheckJarBoolean" to checkJar, "sanityCheckJarEntryCount" to expectedEntryCount)
    inputs.file(jar.map { it.archiveFile })

    logger.info("$prefix Configuring sanityCheckJar task (enabled: $checkJar, expected entry count: $expectedEntryCount)")

    onlyIf {
        checkJar
    }
    doLast {
        logger.info("$prefix Sanity checking integrity of generated jar file.")
        val size = DebugBuild.verifyJarFileContents(
            project,
            listOf(
                "implicit.x",                 // verify the implicits are in the jar
                "org/xvm/tool/Compiler",      // verify the javatools package inclusion
                "org/xvm/util/Severity",      // verify the javatools_utils package inclusion
                "org/jline/reader/LineReader" // verify the jline library inclusion
            ),
            expectedEntryCount
        )
        logger.lifecycle("$prefix Sanity check of javatools.jar completed successfully ($size elements found).")
    }
}

/*
 * We may want to provide a "thin" javatools.jar with javatools utils, jline and other dependencies
 * on the classpath in the manifest, and have them, together with licenses, in a sub-folder to
 * javatools in the XDK installation.
 *
 * TODO: This configuration exports the dependencies of the javatools.jar, so that we can consume
 *       and package them separately.
 */
/*
val xdkJavaToolsDependencyProvider by configurations.registering {
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(copyDependencies)
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("javatools-dependencies-dir"))
    }
}
*/
