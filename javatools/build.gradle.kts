import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_JAVATOOLS_JAR
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import java.util.Properties

/**
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.java)
}

private val semanticVersion: SemanticVersion by extra

val processResources by tasks.existing {
    // Make the task depend on environment variables so Gradle knows when to re-run it
    inputs.property("GH_COMMIT", providers.environmentVariable("GH_COMMIT").orElse(""))
    inputs.property("GH_BRANCH", providers.environmentVariable("GH_BRANCH").orElse("master"))
    inputs.property("version", version)
    
    outputs.file(layout.buildDirectory.file("generated/resources/main/git.properties"))
    
    doFirst {
        logger.info(">>> GENERATING GIT PROPERTIES")
        val gitProtocol = GitHubProtocol(project)
        val props = gitProtocol.getGitInfo().toMutableMap()
        props["git.build.version"] = version.toString()
        logger.info("Calculated git properties: $props")
        val gitPropsFile = layout.buildDirectory.file("generated/resources/main/git.properties").get().asFile
        logger.info("Writing git properties file: ${gitPropsFile.absolutePath}")
        logger.debug("  Parent dir exists: ${gitPropsFile.parentFile.exists()}")
        gitPropsFile.parentFile.mkdirs()
        logger.debug("  Parent dir exists after mkdirs: ${gitPropsFile.parentFile.exists()}")
        gitPropsFile.writeText(props.map { "${it.key}=${it.value}" }.joinToString("\n"))
        logger.info("Git configuration properties:: ${gitPropsFile.readText()}")
    }
}

// TODO: Move these to common-plugins, the XDK composite build does use them in some different places.
val xdkJavaToolsProvider by configurations.registering {
    description = "Provider configuration of the The XVM Java Tools jar artifact: 'javatools-$version.jar'"
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(tasks.jar)
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(XDK_ARTIFACT_NAME_JAVATOOLS_JAR))
    }
}

val xdkEcstasyResourcesConsumer by configurations.registering {
    description = "Consumer configuration for ecstasy resources needed by javatools"
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("ecstasy-resources"))
    }
}

dependencies {
    implementation(libs.javatools.utils)
    implementation(libs.jline)
    testImplementation(libs.javatools.utils)
    // Note: We don't add this to sourceSets to avoid IntelliJ seeing cross-module paths
    xdkEcstasyResourcesConsumer(libs.xdk.ecstasy)
}

/**
 * INTELLIJ FIX: Copy resources at build time instead of referencing them directly.
 *
 * Instead of adding lib_ecstasy resources as a source directory (which confuses IntelliJ),
 * we copy them to our build directory and include that. This completely isolates
 * the resource dependency from IntelliJ's module system.
 */
val copyEcstasyResources by tasks.registering(Copy::class) {
    description = "Copy ecstasy resources to avoid IntelliJ cross-module path issues"
    from(xdkEcstasyResourcesConsumer)
    into(layout.buildDirectory.dir("generated/resources/main"))
    // Only copy when source changes or destination doesn't exist
    onlyIf { 
        !destinationDir.exists() || 
        source.files.any { it.lastModified() > destinationDir.lastModified() }
    }
}

val generateBuildInfo by tasks.registering {
    description = "Generate build-info.properties with version and git information"
    dependsOn(processResources)
    
    val versionPropsFile = compositeRootProjectDirectory.file("version.properties")
    val outputFile = layout.buildDirectory.file("resources/main/build-info.properties")
    val gitPropsFile = layout.buildDirectory.file("resources/main/git.properties")
    
    inputs.file(versionPropsFile)
    inputs.file(gitPropsFile).optional()
    outputs.file(outputFile)
    
    
    doLast {
        // Read version properties as base
        val buildInfo = Properties()
        versionPropsFile.asFile.inputStream().use { buildInfo.load(it) }
        
        // Add git information from git.properties
        if (gitPropsFile.get().asFile.exists()) {
            val gitProps = Properties()
            gitPropsFile.get().asFile.inputStream().use { gitProps.load(it) }
            
            gitProps.getProperty("git.commit.id")?.let { buildInfo.setProperty("git.commit", it) }
            
            val isDirty = gitProps.getProperty("git.dirty")?.toBoolean() ?: false
            buildInfo.setProperty("git.status", if (isDirty) "detached-head" else "clean")
        }
        
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            outputStream().use { buildInfo.store(it, "Build information generated at build time") }
        }
    }
}


sourceSets {
    main {
        resources {
            // Use build-time copied resources instead of direct reference
            srcDir(copyEcstasyResources.map { it.destinationDir })
        }
    }
}

tasks.processResources {
    dependsOn(copyEcstasyResources)
}

/**
 * Sync (not copy) all dependencies from the compile classpath. This is to prevent
 * older versions of dependencies with different file names (due to version changed),
 * still being in the build output, as we have not cleaned.
 */
val syncDependencies by tasks.registering(Sync::class) {
    val taskPrefix = "[${project.name}:syncDependencies]"
    from(configurations.compileClasspath) {
        include("**/*.jar")
    }
    into(layout.buildDirectory.dir("javatools-dependencies"))
    doLast {
        outputs.files.asFileTree.forEach {
            logger.info("$taskPrefix Resolved javatools dependency file: $it")
        }
    }
}

val jar by tasks.existing(Jar::class) {
    dependsOn(processResources, generateBuildInfo)
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
    from(syncDependencies.map { fileTree(it.destinationDir).map { jarFile ->
        logger.info("$prefix Resolving dependency: $jarFile for $version")
        zipTree(jarFile)
    }})

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
            "Sealed" to "true",
            "Xdk-Version" to semanticVersion.toString(),
        )
    }
}

val versionOutputTest by tasks.registering(Test::class) {
    description = "Run tests that verify version output contains git and API information"
    group = VERIFICATION_GROUP
    
    dependsOn(jar, tasks.test)
    
    // Only run the version-related tests
    include("**/BuildInfoTest.class", "**/LauncherVersionTest.class")
    
    doFirst {
        logger.lifecycle("$prefix Verifying version output contains expected git and API information")
    }
}

val assemble by tasks.existing {
    dependsOn(sanityCheckJar) // "assemble" already depends on jar by default in the Java build life cycle
}

val sanityCheckJar by tasks.registering {
    group = VERIFICATION_GROUP
    description = """
            If the properties are enabled, verify that the javatools.jar file is sane (contains expected packages and files), 
            and optionally, that it has a certain number of entries.
        """.trimIndent()

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
                "implicit.x",                       // verify the implicits are in the jar
                "org/xvm/tool/Compiler.class",      // verify the javatools package inclusion
                "org/xvm/util/Severity.class",      // verify the javatools_utils package inclusion
                "org/jline/reader/LineReader.class" // verify the jline library inclusion
            ),
            expectedEntryCount
        )
        logger.lifecycle("$prefix Sanity check of javatools.jar completed successfully ($size elements found).")
    }
}
