import XdkBuildLogic.XDK_ARTIFACT_NAME_JAVATOOLS_JAR
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.gradle.api.tasks.PathSensitivity
import java.util.Properties

/**
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.java)
    alias(libs.plugins.palantir.git.version)
}

// semanticVersion is now a Project extension property

// Using Palantir git plugin - no custom git tasks needed

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

// Path to your static base properties
val versionPropsFile = compositeRootProjectDirectory.file("version.properties")

abstract class GenerateBuildInfo : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baseProps: RegularFileProperty

    @get:Input
    abstract val gitCommit: Property<String>

    @get:Input
    abstract val gitStatus: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val commit = gitCommit.get()
        val status = gitStatus.get()

        logger.lifecycle("[javatools] Generating build-info.properties with git commit: $commit ($status)")

        val buildInfo = Properties().apply {
            baseProps.get().asFile.inputStream().use { load(it) }
            setProperty("git.commit", commit)
            setProperty("git.status", status)
        }

        val content = buildString {
            appendLine("#Build information generated at build time")
            buildInfo.entries
                .map { it.key.toString() to it.value.toString() }
                .sortedBy { it.first }
                .forEach { (k, v) -> appendLine("$k=$v") }
        }

        // Write to standard Gradle generated resources directory
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(content)

        logger.lifecycle("[javatools] Build info written to: ${out.absolutePath}")
    }
}

val generateBuildInfo by tasks.registering(GenerateBuildInfo::class) {
    description = "Generate build-info.properties deterministically (config-cache safe)"
    baseProps.set(versionPropsFile)

    // Use Providers to defer resolution to task execution time, avoiding CC issues with Groovy closure
    val versionDetailsProvider = providers.provider {
        @Suppress("UNCHECKED_CAST")
        (project.extensions.extraProperties["versionDetails"] as groovy.lang.Closure<com.palantir.gradle.gitversion.VersionDetails>).call()
    }

    gitCommit.set(versionDetailsProvider.map { it.gitHashFull })

    gitStatus.set(versionDetailsProvider.map { it.branchName ?: "detached-head" })

    // Put it under build/, so Gradle owns it
    outputFile.set(layout.buildDirectory.file("generated/resources/main/build-info.properties"))
}

sourceSets {
    main {
        resources {
            // Use build-time copied resources instead of direct reference
            srcDir(copyEcstasyResources.map { it.destinationDir })
            // Include generated build info so IntelliJ can find it
            srcDir(generateBuildInfo.map { it.outputFile.get().asFile.parentFile })
        }
    }
}

tasks.processResources {
    dependsOn(copyEcstasyResources)
}

// Make sure generateBuildInfo runs for any compilation task that might need it
tasks.compileJava {
    dependsOn(generateBuildInfo)
}

// Also make it run for tests and other lifecycle tasks
tasks.compileTestJava {
    dependsOn(generateBuildInfo)
}

// Use the copied ecstasy resources as before
tasks.processResources {
    dependsOn(copyEcstasyResources, generateBuildInfo)
    // Include the generated build-info.properties
    from(generateBuildInfo.map { it.outputFile }) {
        into("") // at root of resources in the jar
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// Make 'jar' clean: no I/O or repo reads inside actions
val jar by tasks.existing(Jar::class) {
    dependsOn(tasks.processResources)

    // Declare inputs that affect jar content
    inputs.files(configurations.compileClasspath)
    inputs.files(xdkEcstasyResourcesConsumer)
    inputs.property("manifestSemanticVersion", semanticVersion)
    inputs.property("manifestVersion", version.toString())

    // Build your fat-jar content lazily
    from(configurations.compileClasspath.map { cfg ->
        cfg.filter { it.name.endsWith(".jar") }.map(::zipTree)
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
            "Xdk-Version" to semanticVersion,
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
        logger.info("[javatools] Verifying version output contains expected git and API information")
    }
}
