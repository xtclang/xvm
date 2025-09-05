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

// Resolve XDK properties once at script level to avoid configuration cache issues
val enablePreview = getXdkPropertyBoolean("org.xtclang.java.enablePreview", false)
val enableNativeAccess = getXdkPropertyBoolean("org.xtclang.java.enableNativeAccess", false)

// Capture configuration values at configuration time for configuration cache compatibility
val javaToolsJars = configurations.xdkJavaTools.get().files

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
    val enablePreview = getXdkPropertyBoolean("org.xtclang.java.enablePreview", false)
    val enableNativeAccess = getXdkPropertyBoolean("org.xtclang.java.enableNativeAccess", false)
    defaultJvmOpts = buildList {
        add("-ea")
        //add("-DXDK_HOME=\$APP_HOME")
        if (enablePreview) {
            add("--enable-preview")
            logger.info("[xdk] You have enabled preview features for XTC launchers")
        }
        if (enableNativeAccess) {
            add("--enable-native-access=ALL-UNNAMED")
            logger.info("[xdk] You have enabled native access for XTC launchers")
        }
    }
    
    // Clean script modification using doLast pattern
    doLast {
        // Read existing working content and apply minimal changes
        val javaToolsJars = configurations.xdkJavaTools.get().files
        
        listOf(
            unixScript to false,    // Unix shell script
            windowsScript to true   // Windows batch file
        ).forEach { (script, isWindows) ->
            if (script.exists()) {
                var content = script.readText()
                
                // Replace jar paths with version-stripped equivalents
                javaToolsJars.forEach { jar ->
                    val originalName = jar.name
                    val strippedName = stripVersionFromJarName(originalName)
                    content = content.replace("/lib/$originalName", "/javatools/$strippedName")
                        .replace("\\lib\\$originalName", "\\javatools\\$strippedName")
                }
                
                // Add XDK_HOME delegation and XTC module paths using templates
                val platformKey = if (isWindows) "windows" else "unix"
                val templates = XdkDistribution.SCRIPT_TEMPLATES[platformKey]!!
                
                if (isWindows) {
                    // Insert XDK_HOME delegation for Windows - place it right after APP_HOME calculation
                    val insertPoint = content.indexOf(":execute")
                    if (insertPoint >= 0) {
                        val lineEnd = content.indexOf('\n', insertPoint)
                        val endOfLine = if (lineEnd >= 0) lineEnd + 1 else content.length
                        val before = content.substring(0, endOfLine)
                        val after = content.substring(endOfLine)
                        content = before + templates["xdk_home_delegation"] + after
                    }
                    
                    // Add XTC module paths to Windows script
                    content = content.replace("org.xvm.tool.Launcher", templates["launcher_args"]!!)
                } else {
                    // Insert XDK_HOME delegation for Unix - place it right after APP_HOME calculation
                    val insertPoint = content.indexOf("APP_HOME=\$( cd -P")
                    if (insertPoint >= 0) {
                        val lineEnd = content.indexOf('\n', insertPoint)
                        val endOfLine = if (lineEnd >= 0) lineEnd + 1 else content.length
                        val before = content.substring(0, endOfLine)
                        val after = content.substring(endOfLine)
                        content = before + templates["xdk_home_delegation"] + after
                    }
                    
                    // Add XTC module paths to Unix script  
                    content = content.replace("org.xvm.tool.Launcher", templates["launcher_args"]!!)
                }
                
                script.writeText(content)
            }
        }
        
        // Create additional scripts for xcc and xtc
        listOf("xcc", "xtc").forEach { toolName ->
            val unixOriginal = File(outputDir, "xec")
            val windowsOriginal = File(outputDir, "xec.bat")
            
            if (unixOriginal.exists()) {
                val newScript = File(outputDir, toolName)
                newScript.writeText(unixOriginal.readText().replace("Launcher \\${'$'}APP_BASE_NAME", "Launcher $toolName"))
                newScript.setExecutable(true)
            }
            
            if (windowsOriginal.exists()) {
                File(outputDir, "$toolName.bat").writeText(
                    windowsOriginal.readText().replace("Launcher %APP_BASE_NAME%", "Launcher $toolName"))
            }
        }
    }
    // Declare inputs and outputs for incremental build support
    inputs.property("enablePreview", enablePreview)
    inputs.property("enableNativeAccess", enableNativeAccess)
    outputs.files(listOf("xcc", "xec", "xtc").flatMap { script ->
        listOf(File(outputDir, script), File(outputDir, "$script.bat"))
    })
    
    // Create additional scripts for xcc and xtc with simple name replacement
    doLast {
        listOf("xcc", "xtc").forEach { toolName ->
            val unixScript = File(outputDir, "xec")
            val windowsScript = File(outputDir, "xec.bat")
            
            if (unixScript.exists()) {
                val newScript = File(outputDir, toolName)
                newScript.writeText(
                    unixScript.readText().replace("\$APP_BASE_NAME", toolName)
                )
                newScript.setExecutable(true)
            }
            
            if (windowsScript.exists()) {
                File(outputDir, "$toolName.bat").writeText(
                    windowsScript.readText().replace("%APP_BASE_NAME%", toolName)
                )
            }
        }
    }
}

// Copy scripts to distribution location
val prepareDistributionScripts by tasks.registering(Copy::class) {
    dependsOn(tasks.startScripts)
    from(tasks.startScripts.get().outputDir!!) {
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
                include("xtc")
                include("xtc.bat")
                into("bin")
            }
            
            // Exclude unwanted files and prevent auto-inclusion of script task outputs
            distributionExcludes.forEach { exclude(it) }
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

val withNativeLaunchersDistZip by tasks.existing {
    dependsOn(tasks.startScripts)
}

val withNativeLaunchersDistTar by tasks.existing {
    dependsOn(tasks.startScripts)
}
