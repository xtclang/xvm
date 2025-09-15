import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE
import XdkDistribution.Companion.JAVATOOLS_PREFIX_PATTERN
import org.gradle.api.DefaultTask
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
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
    id("org.xtclang.build.xdk.versioning")
    id("org.xtclang.build.git")
    id("org.xtclang.build.publish")  // Use the publication convention plugin
    alias(libs.plugins.xtc)
    alias(libs.plugins.sonatype.publish)
    application
    distribution
    signing
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
    @Suppress("UnstableApiUsage")
    xtcLauncherBinaries(project(path = ":javatools-launcher", configuration = "xtcLauncherBinaries"))
}

private val semanticVersion: SemanticVersion by extra

private val xdkDist = xdkBuildLogic.distro()

// Configuration cache compatibility: Extract just the version string to avoid holding project references
val artifactVersion = semanticVersion.artifactVersion


// Resolve XDK properties once at script level to avoid configuration cache issues
val enablePreview = getXdkPropertyBoolean("org.xtclang.java.enablePreview", false)
val enableNativeAccess = getXdkPropertyBoolean("org.xtclang.java.enableNativeAccess", false)

// Configure application plugin to create multiple scripts instead of default single script
application {
    applicationName = "xdk"
    mainClass.set("org.xvm.tool.Launcher") // Unified entry point for all tools
}

// Configure the application plugin to generate scripts using custom templates
tasks.startScripts {
    applicationName = "xec"  
    classpath = configurations.xdkJavaTools.get()
    // Configure default JVM options  
    defaultJvmOpts = buildList {
        add("-ea")
        if (enablePreview) {
            add("--enable-preview")
            logger.info("[xdk] You have enabled preview features for XTC launchers")
        }
        if (enableNativeAccess) {
            add("--enable-native-access=ALL-UNNAMED") 
            logger.info("[xdk] You have enabled native access for XTC launchers")
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

// Copy scripts to distribution location
val prepareDistributionScripts by tasks.registering(Copy::class) {
    dependsOn(modifyScripts)
    from(tasks.startScripts.map { it.outputDir!! }) {
        include("xec*", "xcc*", "xtc*")
    }
    into(layout.buildDirectory.dir("distribution-scripts"))
}



/**
 * Propagate the "version" part of the semanticVersion to all XTC compilers in all subprojects (the XDK modules
 * will get stamped with the Gradle project version, as defined in VERSION in the repo root).
 */

subprojects {
    tasks.withType<XtcCompileTask>().configureEach {
        /*
         * Add version stamp to XDK module from the XDK build global semantic version single source of truth.
         */
        assert(version == semanticVersion.artifactVersion)
        xtcVersion = semanticVersion.artifactVersion
    }
}

// Override the convention plugin's publishLocal task to do XDK-specific distribution publishing
val publishLocal by tasks.existing {
    // Clear default dependencies from convention plugin
    setDependsOn(emptyList<Task>())
    // Add XDK-specific dependency 
    dependsOn(tasks.distZip)
    
    // Extract the path during configuration to avoid capturing task reference
    val distZipPath = layout.buildDirectory.file("distributions/xdk-${artifactVersion}.zip")
    
    doLast {
        logger.lifecycle("[xdk] XDK distribution created: ${distZipPath.get().asFile}")
        logger.lifecycle("[xdk] To use locally, extract the distribution or run './gradlew installDist'")
    }
}

// Override the convention plugin's publication management tasks for XDK-specific behavior
val deleteLocalPublications by tasks.existing {
    doLast {
        logger.lifecycle("[xdk] XDK uses file-based distribution. To clean, use './gradlew clean' or delete build/distributions/")
    }
}

val listLocalPublications by tasks.existing {
    // Extract path during configuration to avoid script reference capture
    val distributionsDir = layout.buildDirectory.dir("distributions")
    
    doLast {
        val distDir = distributionsDir.get().asFile
        logger.lifecycle("[xdk] XDK distribution artifacts:")
        if (distDir.exists()) {
            distDir.listFiles()?.filter { it.name.startsWith("xdk-") }?.forEach {
                logger.lifecycle("[xdk]   ${it.absolutePath}")
            }
        } else {
            logger.lifecycle("[xdk]   No distribution artifacts found. Run './gradlew distZip' to create them.")
        }
    }
}

val listRemotePublications by tasks.existing {
    doLast {
        logger.lifecycle("[xdk] XDK uses file-based distribution, not remote Maven publication.")
        logger.lifecycle("[xdk] For Docker images, use './gradlew dockerBuild' tasks.")
    }
}

val deleteRemotePublications by tasks.existing {
    doLast {
        logger.lifecycle("[xdk] XDK uses file-based distribution, not remote Maven publication.")
        logger.lifecycle("[xdk] For Docker cleanup, use Docker commands directly.")
    }
}

// Extract publication values during configuration to avoid capturing project references
private val publicationGroupId = project.group.toString()
private val publicationArtifactId = project.name
private val publicationVersion = project.version.toString()

// Add XDK Maven publication to GitHub packages (like in master)
publishing {
    publications {
        val xdkArchive by registering(MavenPublication::class) {
            groupId = publicationGroupId
            artifactId = publicationArtifactId
            version = publicationVersion
            
            pom {
                name.set("xdk")
                description.set("XTC Language Software Development Kit (XDK) Distribution Archive")
                url.set("https://xtclang.org")
            }
            logger.info("[xdk] Publication '$name' configured for '$publicationGroupId:$publicationArtifactId:$publicationVersion'")
            artifact(tasks.distZip) {
                extension = "zip"
            }
        }
    }
}

// Now publishRemote will use the convention plugin behavior to publish xdkArchive to GitHub
// No need to override publishRemote - let it use the default behavior from convention plugin


// Signing removed since we're not using Maven publication for XDK
// signing {
//     mavenCentralSigning()
// }

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
                logger.info("[xdk] Resolving processResources output (this should be during the execution phase).")
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
            
            // Version file
            from(tasks.xtcVersionFile)
            
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
                logger.info("[xdk] Resolving processResources output (this should be during the execution phase).")
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
            
            // Version file
            from(tasks.xtcVersionFile)
            
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

// Ensure distribution tasks depend on script preparation
tasks.installDist {
    dependsOn(prepareDistributionScripts)
}

tasks.distTar {
    dependsOn(prepareDistributionScripts) 
}

tasks.distZip {
    dependsOn(prepareDistributionScripts)
}

// Let the Distribution plugin handle dependencies properly through the standard lifecycle
// Distribution tasks should automatically depend on processResources and other build outputs

val cleanXdk by tasks.registering(Delete::class) {
    subprojects.forEach {
        delete(it.layout.buildDirectory)
    }
    delete(compositeRootBuildDirectory)
}

val clean by tasks.existing {
    dependsOn(cleanXdk)
    doLast {
        logger.info("[xdk] WARNING: Note that running 'clean' is often unnecessary with a properly configured build cache.")
    }
}

/**
 * Set up signing tasks, only enabled if explicitly configured, or if not, only when we
 * are publishing a non-snapshot package.
 */
tasks.withType<Sign>().configureEach {
    val sign = getXdkPropertyBoolean("org.xtclang.signing.enabled", isRelease())
    // TODO: Before mavenCentral access tokens are sorted, signing should never be enabled:
    require(!sign) { "Signing is not enabled, and should not be enabled until we are sure default configs work." }
    logger.info("[xdk] Publication will ${if (sign) "" else "NOT "}be signed.")
    onlyIf {
        sign
    }
}

// Restore the proper distribution task dependencies using the existing utility
tasks.filter { XdkDistribution.isDistributionArchiveTask(it) }.forEach {
    it.dependsOn(tasks.named("processXtcResources"))
}

// Also ensure install tasks depend on processXtcResources (install tasks use the same content)
tasks.matching { it.group == "distribution" && it.name.contains("install") }.configureEach {
    dependsOn(tasks.named("processXtcResources"))
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


/**
 * Ensure that tags are correct. First we fetch remote tags, clobbering any locals ones,
 * the remote is always the single source of truth.
 *
 * For a snapshot release, we delete any existing tag for this version, and place a new
 * tag with hte same contents at the latest commit.
 *
 * For a normal release, we fail if there already is a tag for this version.
 */
// Configure the central git tagging task with XDK-specific values
tasks.ensureGitTags.configure {
    val snapshotOnlyValue = snapshotOnly()
    val currentVersionValue = semanticVersion.artifactVersion  // Use artifactVersion instead of toString()
    
    version.set(currentVersionValue)
    snapshotOnly.set(snapshotOnlyValue)
}

val ensureTags by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Ensure that the current commit is tagged with the current version."

    // Capture values during configuration phase to avoid runtime project access
    val snapshotOnlyValue = snapshotOnly()
    val currentVersionValue = semanticVersion.artifactVersion  // Extract just the version string
    val allowPublicationValue = allowPublication()  // Extract boolean value
    
    if (!allowPublicationValue) {
        logger.lifecycle("[xdk] Skipping publication task, snapshotOnly=${snapshotOnlyValue} for version: '$currentVersionValue")
    }
    onlyIf {
        allowPublicationValue
    }
    
    // Simply depend on the configured git tagging task
    dependsOn(tasks.ensureGitTags)
    
    doLast {
        logger.lifecycle("""
            [xdk] Git tagging completed via ensureGitTags task.
            [xdk]     version: $currentVersionValue
            [xdk]     snapshotOnly: $snapshotOnlyValue
        """.trimIndent())
    }
}

val withNativeLaunchersDistZip by tasks.existing {
    dependsOn(tasks.startScripts)
}

val withNativeLaunchersDistTar by tasks.existing {
    dependsOn(tasks.startScripts)
}
