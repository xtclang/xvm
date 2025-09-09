import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_JAVATOOLS_JAR
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import java.util.Properties

/**
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.java)
    id("org.xtclang.build.git")
}

private val semanticVersion: SemanticVersion by extra

// Use the standard git convention plugin instead of custom logic
tasks.processResources {
    dependsOn(tasks.resolveGitInfo)
}

// Separate task to copy git info to legacy location
val copyGitInfoForBuildInfo by tasks.registering(Copy::class) {
    description = "Copy git info to legacy location for BuildInfo compatibility"
    dependsOn(tasks.resolveGitInfo)
    
    from(tasks.resolveGitInfo.flatMap { it.outputFile })
    into(layout.buildDirectory.dir("resources/main"))
    rename { "git.properties" }
}

tasks.processResources {
    dependsOn(copyGitInfoForBuildInfo)
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
    // Remove onlyIf condition - let Gradle handle incremental builds with proper inputs/outputs
}

val generateBuildInfo by tasks.registering {
    description = "Generate build-info.properties with version and git information"
    dependsOn(tasks.resolveGitInfo)
    
    val versionPropsFile = compositeRootProjectDirectory.file("version.properties")
    val outputFile = layout.buildDirectory.file("resources/main/build-info.properties")
    val gitInfoProvider = tasks.resolveGitInfo.flatMap { it.outputFile }
    val intellijOutputFile = project.file("out/production/resources/build-info.properties")
    
    inputs.file(versionPropsFile)
    inputs.file(gitInfoProvider)
    outputs.file(outputFile)
    // Conditionally add IntelliJ output if directory exists
    if (intellijOutputFile.parentFile.exists()) {
        outputs.file(intellijOutputFile)
    }
    
    doLast {
        // Read version properties as base
        val buildInfo = Properties()
        versionPropsFile.asFile.inputStream().use { buildInfo.load(it) }
        
        // Add git information directly from git info file
        val gitInfoFile = gitInfoProvider.get().asFile
        if (gitInfoFile.exists()) {
            val gitProps = Properties()
            gitInfoFile.inputStream().use { gitProps.load(it) }
            
            gitProps.getProperty("git.commit.id")?.let { buildInfo.setProperty("git.commit", it) }
            
            val isDirty = gitProps.getProperty("git.dirty")?.toBoolean() ?: false
            buildInfo.setProperty("git.status", if (isDirty) "detached-head" else "clean")
        }
        
        // Write to Gradle build directory
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            outputStream().use { buildInfo.store(it, "Build information generated at build time") }
        }
        
        // Also write to IntelliJ output directory if it exists
        if (intellijOutputFile.parentFile.exists()) {
            intellijOutputFile.apply {
                parentFile.mkdirs()
                outputStream().use { buildInfo.store(it, "Build information generated at build time") }
            }
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

// Ensure test compilation has access to generated build info
tasks.compileTestJava {
    dependsOn(generateBuildInfo)
}

val jar by tasks.existing(Jar::class) {
    dependsOn(tasks.processResources, generateBuildInfo)
    
    // CRITICAL: Explicitly declare that this task reads from compileClasspath
    // This forces Gradle to wait for javatools_utils:jar and other dependency tasks to complete
    inputs.files(configurations.compileClasspath)
    // Also declare dependency on ecstasy resources (like implicit.x) that get copied
    inputs.files(xdkEcstasyResourcesConsumer)
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
     * Include project's own classes and resources (default Jar behavior)
     * PLUS extract dependency JARs into the fat jar.
     */
    // With proper inputs declaration above, this should now work correctly with configuration cache
    from(configurations.compileClasspath.map { config ->
        config.filter { it.name.endsWith(".jar") }.map { jarFile -> 
            zipTree(jarFile) 
        }
    })

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
            // Enable native access for JLine and other dependencies that load native libraries
            "Add-Opens" to "java.base/java.lang=ALL-UNNAMED",
            "Enable-Native-Access" to "ALL-UNNAMED",
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
        logger.lifecycle("[javatools] Verifying version output contains expected git and API information")
    }
}

// "assemble" already depends on jar by default in the Java build life cycle
