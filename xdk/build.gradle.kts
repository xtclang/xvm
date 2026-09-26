import XdkDistribution.Companion.JAVATOOLS_MODULE_PATTERNS
import XdkDistribution.Companion.XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.DefaultTask
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.xtclang.plugin.XtcPlugin
import org.xtclang.plugin.XtcTestExtension
import org.xtclang.plugin.tasks.XtcCompileTask
import java.io.File
import javax.inject.Inject

/**
 * XDK root project, collecting the lib_* xdk builds as includes, not includedBuilds ATM,
 * and producing one outgoing artifact with provided layout for the XDK being shipped.
 */

plugins {
    alias(libs.plugins.xdk.build.properties)  // Apply first to set group/version
    alias(libs.plugins.xdk.build.java)  // Provides java plugin + test framework
    alias(libs.plugins.xtc)  // Apply after properties are set
    alias(libs.plugins.xdk.build.publishing)
    application
    distribution
}

val xtcLauncherBinaries = configurations.register("xtcLauncherBinaries") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val xdkJavaToolsJitBridge = configurations.register("xdkJavaToolsJitBridge") {
    description = "Consumes javatools-jitbridge JAR as native binary blob for distribution (NOT classpath)"
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named("jit-bridge-binary"))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("jit-bridge-binary"))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("native-runtime-blob"))
    }
}

val testEcstasyModule = configurations.register("testEcstasyModule") {
    description = "Resolves the compiled ecstasy module that TypeConstantL1SpecializationTest loads"
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("xtc"))
    }
}

/**
 * Local configuration to provide an xdk-distribution, which contains versioned zip and tar.gz XDKs.
 */
val xdkProvider = configurations.register("xdkProvider") {
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

subprojects {
    plugins.withType<XtcPlugin> {
        extensions.configure<XtcTestExtension> {
            // TODO:
            // We redirect XTC test stdout here because testXtc is a custom launcher task, not a Gradle Test task,
            // and the current ATTACHED execution path used to mirror the xunit runner's "Started/Finished" stream
            // directly to the console. That creates much noisier builds than normal Java/JUnit test execution.
            //
            // This is only a partial ergonomics fix. The long-term goal should be to make XTC test execution feel
            // equivalent to Gradle's built-in Test task model instead of relying on raw process stream redirection.
            // Relevant Gradle/Java equivalents we should aim to support conceptually are:
            // - Test.failFast
            // - testLogging.events(...)
            // - testLogging.showStandardStreams
            // - testLogging.exceptionFormat
            // - testLogging.showCauses / showExceptions / showStackTraces
            // - maxParallelForks / forkEvery
            // - reports (HTML / XML)
            // - onOutput { ... } style output handling
            //
            // Ideally the plugin should capture xunit output structurally, show concise progress/failure summaries,
            // preserve stderr visibility, and only surface detailed stdout when explicitly requested or on failure.
            stdoutPath(layout.buildDirectory.file("logs/testXtc-stdout.log"))
        }
    }
}

dependencies {
    xdkJavaTools(libs.javatools)
    // Test dependencies for integration tests
    testImplementation(libs.javatools)
    testImplementation(libs.javatools.utils)
    testEcstasyModule(libs.xdk.ecstasy)
    xdkJavaToolsJitBridge(libs.javatools.jitbridge)
    xtcModule(libs.bundles.xdk.libraries)
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
    applicationName = "xtc"
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
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scriptsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun modifyScripts() {
        fileSystem.sync {
            from(scriptsDir) {
                include("xtc", "xtc.bat")
            }
            into(outputDir)
        }
        XdkDistribution.modifyLauncherScripts(
            outputDir = outputDir.get().asFile,
            artifactVersion = artifactVersionProperty.get(),
            javaToolsFiles = javaToolsFiles.files
        )
    }
}

val modifyScripts = tasks.register<ModifyScriptsTask>("modifyScripts") {
    artifactVersionProperty.set(artifactVersion)
    javaToolsFiles.from(configurations.getByName("xdkJavaTools"))
    scriptsDir.set(layout.dir(tasks.startScripts.map { it.outputDir!! }))
    outputDir.set(layout.buildDirectory.dir("modified-scripts"))
}

val prepareDistributionScripts = tasks.register<Copy>("prepareDistributionScripts") {
    from(modifyScripts.flatMap { it.outputDir })
    into(layout.buildDirectory.dir("distribution-scripts"))
}

/**
 * Bundle every XDK module (including the system modules mack and _native) into a single
 * self-contained xdk.xtc file. Opt-in: not wired into the default build/distribution lifecycle.
 * The resulting file can serve as the entire XDK module path, e.g.:
 *
 *     java -jar javatools.jar run -L xdk.xtc app.xtc
 */
val bundleXdk = tasks.register<JavaExec>("bundleXdk") {
    group = "distribution"
    description = "Bundle all XDK modules into a single self-contained xdk.xtc"
    dependsOn(tasks.installDist)

    val installDir = layout.buildDirectory.dir("install/xdk")
    val outputFile = layout.buildDirectory.file("bundle/xdk.xtc")

    inputs.dir(installDir.map { it.dir("lib") })
    inputs.file(installDir.map { it.file("javatools/javatools_turtle.xtc") })
    inputs.file(installDir.map { it.file("javatools/javatools_bridge.xtc") })
    outputs.file(outputFile)

    classpath(configurations.xdkJavaTools)
    mainClass = "org.xvm.tool.Launcher"
    argumentProviders.add {
        val install = installDir.get().asFile
        listOf(
            "bundle",
            "--include-system",
            "--main", "ecstasy.xtclang.org",
            "-L", File(install, "lib").absolutePath,
            "-L", File(install, "javatools/javatools_turtle.xtc").absolutePath,
            "-L", File(install, "javatools/javatools_bridge.xtc").absolutePath,
            "-o", outputFile.get().asFile.absolutePath,
        )
    }
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

    // Aggregate subproject lifecycle tasks into xdk lifecycle tasks.
    // Normally, running `./gradlew check` would execute `check` for all projects in the build.
    // However, the root aggregator plugin explicitly wires `:check` to depend on `:xdk:check`,
    // which bypasses the natural Gradle behavior of running tasks for all subprojects.
    // This explicit aggregation ensures subproject lifecycle tasks are included.
    pluginManager.withPlugin("base") {
        listOf("check", "build", "assemble", "clean").forEach { taskName ->
            rootProject.tasks.named(taskName) { dependsOn(tasks.named(taskName)) }
        }
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
        JavaLibrary(
            javadocJar = JavadocJar.None(),
            sourcesJar = SourcesJar.None()
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

            // The application plugin copies the raw startScripts output into bin/ ahead of our specs, and EXCLUDE
            // keeps the first copy, so drop it: bin/ must only contain the rewritten launchers from modifyScripts.
            val rawStartScriptsDir = tasks.startScripts.map { it.outputDir!! }
            exclude { it.file.parentFile == rawStartScriptsDir.get() }

            // Core XDK content
            val xdkTemplate = tasks.processResources.map {
                File(it.outputs.files.singleFile, "xdk")
            }
            from(xdkTemplate) {
                exclude("**/bin/**")  // Exclude bin directory to avoid conflicts with generated scripts
                includeEmptyDirs = false
            }

            // XTC modules. The build keeps the compiler's output names; the two runtime system modules
            // get their distribution names here, which the launchers expect next to javatools.jar.
            from(configurations.xtcModule) {
                into("lib")
                exclude(JAVATOOLS_MODULE_PATTERNS)
            }
            from(configurations.xtcModule) {
                into("javatools")
                include(JAVATOOLS_MODULE_PATTERNS)
                rename("^mack\\.xtc$", "javatools_turtle.xtc")
                rename("^_native\\.xtc$", "javatools_bridge.xtc")
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
            from(prepareDistributionScripts.flatMap { it.destinationDirectory }) {
                include("xcc")
                include("xcc.bat")
                include("xec")
                include("xec.bat")
                include("xtc")
                include("xtc.bat")
                into("bin")
                filePermissions { unix("rwxr-xr-x") }
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

/**
 * Verifies the launchers actually shipped in the XDK archive: every one must be present, executable, and the version
 * rewritten by modifyScripts (javatools/javatools.jar on the classpath plus the javatools module paths), never the raw
 * application plugin script, whose lib/javatools-<version>.jar classpath does not exist in the distribution.
 */
abstract class VerifyDistributionLaunchersTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val distributionArchive: RegularFileProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @TaskAction
    fun verify() {
        val expected = listOf("xtc", "xcc", "xec").flatMap { listOf(it, "$it.bat") }.toSet()
        val shipped = buildMap {
            archives.zipTree(distributionArchive).matching { include("*/bin/*") }.visit {
                if (!isDirectory) {
                    put(name, file.readText() to permissions.user.execute)
                }
            }
        }
        val problems = buildList {
            (expected - shipped.keys).forEach { add("$it: missing from bin/") }
            shipped.filterKeys { it in expected }.forEach { (name, launcher) ->
                val (content, executable) = launcher
                if (!content.contains("javatools.jar") || !content.contains("javatools_turtle.xtc")) {
                    add("$name: not the launcher rewritten by modifyScripts")
                }
                if (!executable) {
                    add("$name: not executable")
                }
            }
        }
        check(problems.isEmpty()) {
            "Broken launchers in ${distributionArchive.get().asFile.name}:\n  ${problems.joinToString("\n  ")}"
        }
    }
}

val verifyDistributionLaunchers = tasks.register<VerifyDistributionLaunchersTask>("verifyDistributionLaunchers") {
    description = "Verify the XDK archive ships the rewritten, executable launcher scripts."
    group = VERIFICATION_GROUP
    distributionArchive.set(tasks.distZip.flatMap { it.archiveFile })
}

tasks.check {
    dependsOn(verifyDistributionLaunchers)
}

// Let the Distribution plugin handle dependencies properly through the standard lifecycle
// Distribution tasks should automatically depend on processResources and other build outputs

val cleanXdk = tasks.register<Delete>("cleanXdk") {
    subprojects.forEach {
        delete(it.layout.buildDirectory)
    }
    // Delete build directory at composite root
    delete(File(XdkPropertiesService.compositeRootDirectory(projectDir), "build"))
}

val clean = tasks.named("clean") {
    dependsOn(cleanXdk)
    doLast {
        logger.info("[xdk] WARNING: Note that running 'clean' is often unnecessary with a properly configured build cache.")
    }
}

// Restore the proper distribution task dependencies using the existing utility
tasks.withType<AbstractArchiveTask>().matching { XdkDistribution.isDistributionArchiveTask(it) }.configureEach {
    dependsOn(tasks.named<Copy>("processXtcResources"))
}

// Also ensure install tasks depend on processXtcResources (install tasks use the same content)
tasks.matching { it.group == "distribution" && it.name.contains("install") }.configureEach {
    dependsOn(tasks.named<Copy>("processXtcResources"))
}

tasks.withType<Tar>().configureEach {
    compression = Compression.GZIP
    archiveExtension = "tar.gz"
}

// TypeConstantL1SpecializationTest loads the compiled ecstasy module from the test classpath.
// Consuming the lib-ecstasy artifact carries the compile dependency and tracks the module as a test input.
tasks.processTestResources {
    from(testEcstasyModule) {
        include("ecstasy.xtc")
    }
}

// Configure test task to run integration tests after XDK is fully built
tasks.test {
    // Tests require the XDK to be fully installed with all XTC libraries
    dependsOn(tasks.installDist)

    // Set working directory for tests
    workingDir = projectDir
}
