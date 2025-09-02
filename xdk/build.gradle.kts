import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE
import XdkDistribution.Companion.JAVATOOLS_PREFIX_PATTERN
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import org.xtclang.plugin.tasks.XtcCompileTask
import java.io.File

/**
 * XDK root project, collecting the lib_* xdk builds as includes, not includedBuilds ATM,
 * and producing one outgoing artifact with provided layout for the XDK being shipped.
 */

plugins {
    id("org.xtclang.build.xdk.versioning")
    alias(libs.plugins.xdk.build.publish)
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

/**
 * Strip version suffix from jar names, matching the rename logic used in distribution
 */
private fun stripVersionFromJarName(jarName: String): String {
    return jarName.replace(Regex("(.*)\\-${Regex.escape(semanticVersion.artifactVersion)}\\.jar"), "$1.jar")
}

// Configure application plugin to create multiple scripts instead of default single script
application {
    applicationName = "xdk"
    mainClass.set("org.xvm.tool.Compiler") // Will be overridden by individual scripts
}

// Disable default single script generation
tasks.startScripts {
    enabled = false
}

/**
 * Helper function to create XDK launcher script tasks with consistent configuration
 */
fun createLauncherScriptTask(scriptName: String, mainClassName: String) = tasks.registering(CreateStartScripts::class) {
    applicationName = scriptName
    mainClass.set(mainClassName)
    outputDir = layout.buildDirectory.dir("scripts").get().asFile
    classpath = configurations.xdkJavaTools.get()
    // Configure default JVM options
    val enablePreview = getXdkPropertyBoolean("org.xtclang.java.enablePreview", false)
    val enableNativeAccess = getXdkPropertyBoolean("org.xtclang.java.enableNativeAccess", false)
    defaultJvmOpts = buildList {
        add("-ea")
        add("-DXDK_HOME=\$APP_HOME")
        if (enablePreview) {
            add("--enable-preview")
            logger.warn("[xdk] You have enabled preview features for XTC launchers")
        }
        if (enableNativeAccess) {
            add("--enable-native-access=ALL-UNNAMED")
            logger.warn("[xdk] You have enabled native access for XTC launchers")
        }
    }
    logger.info("[xdk] Default JVM args for $scriptName: $defaultJvmOpts")

    // Declare outputs explicitly  
    outputs.files(File(outputDir, scriptName), File(outputDir, "$scriptName.bat"))
    
    doLast {
        // Process both Unix shell script and Windows batch file
        // Unix script: Works on Linux, macOS, and all other Unix/POSIX systems 
        // Windows script: Works on Windows only
        listOf(
            File(outputDir, scriptName) to false,     // Unix shell script (works everywhere except Windows)
            File(outputDir, "$scriptName.bat") to true   // Windows batch file (Windows only)
        ).forEach { (scriptFile, isWindows) ->
            if (scriptFile.exists()) {
                var content = scriptFile.readText()
                
                // Replace each jar in the classpath with its version-stripped equivalent
                configurations.xdkJavaTools.get().forEach { jar ->
                    val originalName = jar.name
                    val strippedName = stripVersionFromJarName(originalName)
                    // Replace lib/ paths with javatools/ paths and strip version using proper cross-platform function
                    content = XdkDistribution.replaceJarPaths(content, originalName, strippedName)
                }
                
                // Add XTC module paths using utility from XdkDistribution  
                content = XdkDistribution.injectXtcModulePaths(content, mainClassName, isWindows)
                
                // Add XDK_HOME delegation logic
                content = XdkDistribution.injectXdkHomeDelegation(content, isWindows)
                
                // Fix path resolution to use APP_HOME consistently after delegation
                content = XdkDistribution.fixPathResolution(content, isWindows)
                
                scriptFile.writeText(content)
            }
        }
    }
}

/**
 * Create individual script tasks for xcc and xec with different main classes
 */
val createXccScript by createLauncherScriptTask("xcc", "org.xvm.tool.Compiler")
val createXecScript by createLauncherScriptTask("xec", "org.xvm.tool.Runner")

// Create scripts directly in distribution-ready location
val prepareDistributionScripts by tasks.registering(Copy::class) {
    dependsOn(createXccScript, createXecScript)
    from(createXccScript.map { it.outputDir!! }) {
        include("xcc*")
    }
    from(createXecScript.map { it.outputDir!! }) {
        include("xec*")
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

publishing {
    // TODO: Set up a hook to the maven central/osasrh/sonatype repository here, once the keys have been recovered.
    publications {
        val xdkArchive by registering(MavenPublication::class) {
            with(project) {
                groupId = group.toString()
                artifactId = project.name
                version = version.toString()
            }
            pom {
                name = "xdk"
                description = "XTC Language Software Development Kit (XDK) Distribution Archive"
                url = "https://xtclang.org"
            }
            logger.info("[xdk] Publication '$name' configured for '$groupId:$artifactId:$version'")
            artifact(tasks.distZip) {
                extension = "zip"
            }
        }
    }
}

signing {
    mavenCentralSigning()
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
                eachFile {
                    includeEmptyDirs = false
                }
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
                rename { originalName -> stripVersionFromJarName(originalName) }
                into("javatools")
            }
            
            // Include javatools-jitbridge binary blob (separate from normal javatools classpath)
            from(xdkJavaToolsJitBridge) {
                rename { originalName -> stripVersionFromJarName(originalName) }
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
                into("bin")
            }
            
            // Exclude unwanted files and prevent auto-inclusion of script task outputs
            distributionExcludes.forEach { exclude(it) }
        }
    }
    val withLaunchers by registering {
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
                eachFile {
                    includeEmptyDirs = false
                }
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
                rename { originalName -> stripVersionFromJarName(originalName) }
                into("javatools")
            }
            
            // Include javatools-jitbridge binary blob (separate from normal javatools classpath)
            from(xdkJavaToolsJitBridge) {
                rename { originalName -> stripVersionFromJarName(originalName) }
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
            distributionExcludes.forEach { exclude(it) }
        }
    }
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
val ensureTags by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Ensure that the current commit is tagged with the current version."

    // Capture values during configuration phase to avoid runtime project access
    val snapshotOnly = snapshotOnly()
    val currentVersion = semanticVersion
    val gitHubProtocol = xdkBuildLogic.gitHubProtocol()
    if (!allowPublication()) {
        logger.lifecycle("[xdk] Skipping publication task, snapshotOnly=${snapshotOnly} for version: '$currentVersion")
    }
    onlyIf {
        allowPublication()
    }
    doLast {
        logger.lifecycle("""
            [xdk] Ensuring that the current commit is tagged with version.
            [xdk]     version: $currentVersion
            [xdk]     snapshotOnly: $snapshotOnly
        """.trimIndent())
        val tag = gitHubProtocol.ensureTags(snapshotOnly)
        if (GitHubProtocol.tagCreated(tag)) {
            logger.lifecycle("[xdk] Created or updated tag '$tag' for version: '$currentVersion'")
        }
    }
}

val withLaunchersDistZip by tasks.existing {
    dependsOn(createXccScript, createXecScript)
}

val withLaunchersDistTar by tasks.existing {
    dependsOn(createXccScript, createXecScript)
}
