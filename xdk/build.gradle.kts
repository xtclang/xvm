import XdkDistribution.Companion.JAVATOOLS_PREFIX_PATTERN
import XdkDistribution.Companion.XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE
import org.gradle.api.DefaultTask
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.xtclang.plugin.tasks.XtcCompileTask
import java.io.File

/**
 * XDK root project, collecting the lib_* xdk builds as includes, not includedBuilds ATM,
 * and producing one outgoing artifact with provided layout for the XDK being shipped.
 */

plugins {
    alias(libs.plugins.xdk.build.properties)  // Apply first to set group/version
    alias(libs.plugins.xtc)  // Apply after properties are set
    alias(libs.plugins.xdk.build.publishing)
    application
    distribution
}

val xtcLauncherBinaries by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val xdkJavaToolsJitBridge by configurations.registering {
    description = "Consumes javatools-jitbridge JAR as native binary blob for distribution (NOT classpath)"
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named("jit-bridge-binary"))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("jit-bridge-binary"))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("native-runtime-blob"))
    }
}

/**
 * Local configuration to provide an xdk-distribution, which contains versioned zip and tar.gz XDKs.
 */
val xdkProvider by configurations.registering {
    isCanBeConsumed = true
    isCanBeResolved = false
    // TODO these are added twice to the archive configuration. We probably don't want that.
    outgoing.artifact(tasks.distZip) {
        extension = "zip"
    }
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE))
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    xdkJavaTools(libs.javatools)
    xdkJavaToolsJitBridge(libs.javatools.jitbridge)
    xtcModule(libs.xdk.ecstasy)
    xtcModule(libs.xdk.aggregate)
    xtcModule(libs.xdk.cli)
    xtcModule(libs.xdk.collections)
    xtcModule(libs.xdk.convert)
    xtcModule(libs.xdk.crypto)
    xtcModule(libs.xdk.json)
    xtcModule(libs.xdk.jsondb)
    xtcModule(libs.xdk.net)
    xtcModule(libs.xdk.oodb)
    xtcModule(libs.xdk.sec)
    xtcModule(libs.xdk.web)
    xtcModule(libs.xdk.webauth)
    xtcModule(libs.xdk.webcli)
    xtcModule(libs.xdk.xenia)
    xtcModule(libs.xdk.xml)
    xtcModule(libs.javatools.bridge)
    xtcLauncherBinaries(project(path = ":javatools-launcher", configuration = "xtcLauncherBinaries"))
}

// Create XDK distribution configuration
private val xdkDist = XdkDistribution.create(project)

// Configuration cache compatibility: Extract just the version string to avoid holding project references
val artifactVersion = version.toString()


// Resolve XDK properties at configuration time (acceptable - static launcher configuration)
val enablePreview = xdkProperties.booleanValue("org.xtclang.java.enablePreview", false)
val enableNativeAccess = xdkProperties.booleanValue("org.xtclang.java.enableNativeAccess", false)

// Configure application plugin to create multiple scripts instead of default single script
application {
    applicationName = "xdk"
    mainClass.set("org.xvm.tool.Launcher") // Unified entry point for all tools
}

// Configure the application plugin to generate scripts using custom templates
// TODO: This should also use the java convention default jvm args.
tasks.startScripts {
    applicationName = "xec"
    classpath = configurations.xdkJavaTools.get()
    // Configure default JVM options
    defaultJvmOpts = buildList {
        add("-ea")
        if (enablePreview) {
            add("--enable-preview")
        }
        if (enableNativeAccess) {
            add("--enable-native-access=ALL-UNNAMED")
        }
    }
}

// Configuration-cache-compatible script modification task using proper task type
abstract class ModifyScriptsTask : DefaultTask() {
    @get:Input
    abstract val artifactVersionProperty: Property<String>

    @get:InputFiles
    abstract val javaToolsFiles: ConfigurableFileCollection

    @get:InputDirectory
    abstract val scriptsDir: DirectoryProperty

    @get:OutputFiles
    abstract val modifiedScripts: ConfigurableFileCollection

    @TaskAction
    fun modifyScripts() {
        XdkDistribution.modifyLauncherScripts(
            outputDir = scriptsDir.get().asFile,
            artifactVersion = artifactVersionProperty.get(),
            javaToolsFiles = javaToolsFiles.files
        )
    }
}

val modifyScripts by tasks.registering(ModifyScriptsTask::class) {
    dependsOn(tasks.startScripts)

    artifactVersionProperty.set(artifactVersion)
    javaToolsFiles.from(configurations.getByName("xdkJavaTools"))
    scriptsDir.set(layout.dir(tasks.startScripts.map { it.outputDir!! }))
    modifiedScripts.from(tasks.startScripts.map { task ->
        listOf("xcc", "xec", "xtc").flatMap { script ->
            listOf(File(task.outputDir!!, script), File(task.outputDir!!, "$script.bat"))
        }
    })
}

val prepareDistributionScripts by tasks.registering(Copy::class) {
    dependsOn(modifyScripts)
    from(tasks.startScripts.map { it.outputDir!! }) {
        include("xec*", "xcc*", "xtc*")
    }
    into(layout.buildDirectory.dir("distribution-scripts"))
}

/**
 * Propagate group and version to all subprojects (the XDK modules will get stamped with the Gradle project
 * version, as defined in VERSION in the repo root).
 */

subprojects {
    group = rootProject.group
    version = rootProject.version

    tasks.withType<XtcCompileTask>().configureEach {
        /*
         * Add version stamp to XDK module from the XDK build global version single source of truth.
         */
        xtcVersion = version.toString()
    }
}


// Configure project-specific publishing metadata
xdkPublishing {
    pomName.set("xdk")
    pomDescription.set("XTC Language Software Development Kit (XDK) Distribution Archive")
}

// Configure publication type as JavaLibrary to handle custom ZIP artifact
mavenPublishing {
    configure(
        com.vanniktech.maven.publish.JavaLibrary(
            javadocJar = com.vanniktech.maven.publish.JavadocJar.None(),
            sourcesJar = false
        )
    )

    // Add the ZIP distribution as the main artifact
    afterEvaluate {
        publishing.publications.named<MavenPublication>("maven") {
            // Remove default JAR artifact and replace with ZIP
            artifacts.clear()
            artifact(tasks.distZip) {
                extension = "zip"
            }
        }

        // Disable Gradle Module Metadata generation - the XDK is a simple ZIP distribution
        // that doesn't need variant-aware dependency resolution
        tasks.withType<GenerateModuleMetadata>().configureEach {
            enabled = false
        }
    }

    pom {
        packaging = "zip"  // Specify ZIP packaging
    }
}


 /**
 * Common exclude patterns for unwanted files in distributions
 */
private val distributionExcludes = listOf(
    "**/scripts/**",    // Exclude any script build directories
    "**/cfg_*.sh",
    "**/cfg_*.cmd",
    "**/bin/README.md"
)

// Capture distributionExcludes for configuration cache compatibility
val capturedDistributionExcludes = distributionExcludes

/*
 * Distribution archives contain internal directory names like "xdk0.4.4SNAPSHOT" rather than "xdk-0.4.4-SNAPSHOT".
 * This is intentional and follows Gradle's standard behavior - the Distribution plugin sanitizes version strings
 * to remove special characters (hyphens, dots) for filesystem compatibility. This naming convention is used
 * by many Gradle-built projects and should not be changed as it ensures compatibility across different
 * operating systems and deployment tools.
 */
distributions {
    main {
        // Configure as "xdk" distribution with launcher scripts
        distributionBaseName = xdkDist.distributionName  // "xdk"
        version = xdkDist.distributionVersion

        contents {
            // Handle potential script duplicates
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE

            // Core XDK content
            val xdkTemplate = tasks.processResources.map {
                File(it.outputs.files.singleFile, "xdk")
            }
            from(xdkTemplate) {
                exclude("**/bin/**")  // Exclude bin directory to avoid conflicts with generated scripts
                includeEmptyDirs = false
            }

            // XTC modules
            from(configurations.xtcModule) {
                into("lib")
                exclude(JAVATOOLS_PREFIX_PATTERN) // *.xtc, but not javatools_*.xtc
            }
            from(configurations.xtcModule) {
                into("javatools")
                include(JAVATOOLS_PREFIX_PATTERN) // only javatools_*.xtc
            }

            // Java tools (strip version from jar names)
            from(configurations.xdkJavaTools) {
                // Configuration cache: Use static transformer to avoid script object references
                rename(XdkDistribution.createRenameTransformer(artifactVersion))
                into("javatools")
            }

            // Include javatools-jitbridge binary blob (separate from normal javatools classpath)
            from(xdkJavaToolsJitBridge) {
                // Configuration cache: Use static transformer to avoid script object references
                rename(XdkDistribution.createRenameTransformer(artifactVersion))
                into("javatools")
            }

            // Include launcher scripts directly in bin/
            from(prepareDistributionScripts.map { it.destinationDir }) {
                include("xcc")
                include("xcc.bat")
                include("xec")
                include("xec.bat")
                include("xtc")
                include("xtc.bat")
                into("bin")
            }

            // Exclude unwanted files and prevent auto-inclusion of script task outputs
            capturedDistributionExcludes.forEach { exclude(it) }
        }
    }
    val withNativeLaunchers by registering {
        distributionBaseName = xdkDist.distributionName
        version = xdkDist.distributionVersion
        distributionClassifier = "native-${xdkDist.osClassifier()}"

        contents {
            // Handle potential script duplicates
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE

            // Core XDK content (same as main distribution)
            val xdkTemplate = tasks.processResources.map {
                File(it.outputs.files.singleFile, "xdk")
            }
            from(xdkTemplate) {
                exclude("**/bin/**")  // Exclude bin directory to avoid conflicts with generated scripts
                includeEmptyDirs = false
            }

            // XTC modules
            from(configurations.xtcModule) {
                into("lib")
                exclude(JAVATOOLS_PREFIX_PATTERN) // *.xtc, but not javatools_*.xtc
            }
            from(configurations.xtcModule) {
                into("javatools")
                include(JAVATOOLS_PREFIX_PATTERN) // only javatools_*.xtc
            }

            // Java tools (strip version from jar names)
            from(configurations.xdkJavaTools) {
                // Configuration cache: Use static transformer to avoid script object references
                rename(XdkDistribution.createRenameTransformer(artifactVersion))
                into("javatools")
            }

            // Include javatools-jitbridge binary blob (separate from normal javatools classpath)
            from(xdkJavaToolsJitBridge) {
                // Configuration cache: Use static transformer to avoid script object references
                rename(XdkDistribution.createRenameTransformer(artifactVersion))
                into("javatools")
            }

            // Install platform-specific binary launchers that work on the host system
            XdkDistribution.binaryLauncherNames.forEach {
                val launcher = xdkDist.launcherFileName()
                from(xtcLauncherBinaries) {
                    include(launcher)
                    rename(launcher, it)
                    into("bin")
                }
            }

            // Exclude unwanted files and prevent auto-inclusion of script task outputs
            capturedDistributionExcludes.forEach { exclude(it) }
        }
    }
}

// Ensure distribution tasks depend on script preparation AND javatools artifacts
tasks.installDist {
    dependsOn(prepareDistributionScripts)
    // Force dependency on javatools artifacts which triggers git info resolution
    dependsOn(configurations.xdkJavaTools)
}

tasks.distTar {
    dependsOn(prepareDistributionScripts)
    dependsOn(configurations.xdkJavaTools)
}

tasks.distZip {
    dependsOn(prepareDistributionScripts)
    dependsOn(configurations.xdkJavaTools)
}

// Let the Distribution plugin handle dependencies properly through the standard lifecycle
// Distribution tasks should automatically depend on processResources and other build outputs

val cleanXdk by tasks.registering(Delete::class) {
    subprojects.forEach {
        delete(it.layout.buildDirectory)
    }
    // Delete build directory at composite root
    delete(File(XdkPropertiesService.compositeRootDirectory(projectDir), "build"))
}

val clean by tasks.existing {
    dependsOn(cleanXdk)
    doLast {
        logger.info("[xdk] WARNING: Note that running 'clean' is often unnecessary with a properly configured build cache.")
    }
}

// Restore the proper distribution task dependencies using the existing utility
tasks.filter { XdkDistribution.isDistributionArchiveTask(it) }.forEach {
    it.dependsOn(tasks.named<Copy>("processXtcResources"))
}

// Also ensure install tasks depend on processXtcResources (install tasks use the same content)
tasks.matching { it.group == "distribution" && it.name.contains("install") }.configureEach {
    dependsOn(tasks.named<Copy>("processXtcResources"))
}

tasks.withType<Tar>().configureEach {
    compression = Compression.GZIP
    archiveExtension = "tar.gz"
}

val test by tasks.existing {
    doLast {
        TODO("Implement response to the check lifecycle, probably some kind of aggregate XUnit.")
    }
}

val withNativeLaunchersDistZip by tasks.existing {
    dependsOn(tasks.startScripts)
}

val withNativeLaunchersDistTar by tasks.existing {
    dependsOn(tasks.startScripts)
}
