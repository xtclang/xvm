import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.File

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.intellij.platform)
}

// Access version from xdkProperties (set by xdk.build.properties plugin)
val xdkVersion: String = project.version.toString()
val releaseChannel: String = xdkProperties.stringValue("xdk.intellij.release.channel", "alpha")

// Publishing is disabled by default. Enable with: ./gradlew publishPlugin -PenablePublish=true
val enablePublish = project.findProperty("enablePublish")?.toString()?.toBoolean() ?: false

// =============================================================================
// Local IntelliJ IDE Detection
// =============================================================================
// Using a local IDE avoids Gradle transform cache issues on macOS where
// Gatekeeper/codesigning can modify extracted files, corrupting the cache.
// Override with: -PintellijLocalPath=/path/to/IntelliJ

fun findLocalIntelliJ(): File? {
    // Allow explicit override via gradle property
    val explicitPath: String? = project.findProperty("intellijLocalPath")?.toString()
    if (explicitPath != null) {
        val explicit = File(explicitPath)
        if (explicit.exists()) return explicit
        logger.warn("Explicit intellijLocalPath '$explicitPath' does not exist, searching for installed IDE")
    }

    val osName: String = System.getProperty("os.name")?.lowercase() ?: ""
    val userHome: String = System.getProperty("user.home") ?: ""
    val candidates: List<String> =
        when {
            osName.contains("mac") ->
                listOf(
                    "/Applications/IntelliJ IDEA CE.app",
                    "/Applications/IntelliJ IDEA.app",
                    "$userHome/Applications/IntelliJ IDEA CE.app",
                    "$userHome/Applications/IntelliJ IDEA.app",
                )
            osName.contains("linux") -> {
                listOf(
                    "/opt/idea-IC",
                    "/opt/intellij-idea-community",
                    "/usr/share/intellij-idea-community",
                    "$userHome/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-0",
                    "$userHome/idea-IC",
                ) + (File("/opt").listFiles()?.filter { it.name.startsWith("idea-IC-") }?.map { it.path } ?: emptyList()) +
                    (File(userHome).listFiles()?.filter { it.name.startsWith("idea-IC-") }?.map { it.path } ?: emptyList())
            }
            osName.contains("windows") -> {
                val programFiles: String = System.getenv("ProgramFiles") ?: "C:\\Program Files"
                listOf(
                    "$programFiles\\JetBrains\\IntelliJ IDEA Community Edition 2025.1",
                    "$programFiles\\JetBrains\\IntelliJ IDEA Community Edition",
                    "$userHome\\AppData\\Local\\JetBrains\\Toolbox\\apps\\IDEA-C",
                )
            }
            else -> emptyList()
        }

    return candidates.map { File(it) }.firstOrNull { it.exists() }
}

val localIntelliJ: File? = findLocalIntelliJ()
// Force download with -PforceDownloadIde=true (useful when local IDE has incompatible bundled plugins)
val forceDownloadIde = project.findProperty("forceDownloadIde")?.toString()?.toBoolean() ?: false
val useLocalIde = localIntelliJ != null && !forceDownloadIde

if (useLocalIde) {
    logger.info("Using local IntelliJ IDE: ${localIntelliJ!!.absolutePath}")
} else if (forceDownloadIde && localIntelliJ != null) {
    logger.info("Skipping local IDE (forceDownloadIde=true), will download IntelliJ Community")
} else {
    logger.info("No local IntelliJ IDE found, will download (may cause cache issues on macOS)")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// =============================================================================
// Sync XtcProjectCreator from javatools (compiled for Java 21)
// =============================================================================
// XtcProjectCreator is a standalone class with no javatools dependencies.
// We sync it here and compile it for Java 21 (IntelliJ's runtime).

val syncedJavaSourceDir: Provider<Directory> = layout.buildDirectory.dir("generated/synced-java")

val syncXtcProjectCreator by tasks.registering(Copy::class) {
    description = "Sync XtcProjectCreator.java from javatools for Java 21 compilation"
    from(rootProject.file("../javatools/src/main/java/org/xvm/tool/XtcProjectCreator.java"))
    into(syncedJavaSourceDir.map { it.dir("org/xvm/tool") })
}

// Sync gradle-wrapper resources needed by XtcProjectCreator
// Source of truth is the repo root's gradle wrapper (same as what builds XVM)
val syncedResourcesDir: Provider<Directory> = layout.buildDirectory.dir("generated/synced-resources")
val compositeRoot: File = rootProject.projectDir.parentFile!! // /lang -> /

val syncGradleWrapperResources by tasks.registering(Copy::class) {
    description = "Sync gradle-wrapper resources from repo root (single source of truth)"
    from(File(compositeRoot, "gradlew"))
    from(File(compositeRoot, "gradlew.bat"))
    from(File(compositeRoot, "gradle/wrapper")) {
        into("gradle/wrapper")
    }
    into(syncedResourcesDir.map { it.dir("gradle-wrapper") })
}

sourceSets.main {
    java.srcDir(syncedJavaSourceDir)
    resources.srcDir(syncedResourcesDir)
}

val compileJava by tasks.existing {
    dependsOn(syncXtcProjectCreator)
}

// Ensure ktlint runs during normal development (not just 'check')
val ktlintCheck by tasks.existing

val compileKotlin by tasks.existing {
    dependsOn(syncXtcProjectCreator)
    dependsOn(ktlintCheck)
}

// Copy LSP version properties to plugin resources so the plugin can display version info
// The properties file comes from lsp-server's generateBuildInfo task
val copyLspVersionProperties by tasks.registering(Copy::class) {
    description = "Copy LSP version properties from lsp-server for display in IDE"
    // Use the configuration directly - Gradle will resolve dependencies automatically
    from(configurations.named("lspVersionProperties"))
    into(layout.buildDirectory.dir("generated/resources/lsp"))
}

sourceSets.main {
    resources.srcDir(copyLspVersionProperties.map { layout.buildDirectory.dir("generated/resources/lsp") })
}

val processResources by tasks.existing {
    dependsOn(syncGradleWrapperResources)
    dependsOn(copyLspVersionProperties)
}

// ktlint checks synced Java sources, so it must run after sync
tasks.matching { it.name.startsWith("runKtlint") }.configureEach {
    dependsOn(syncXtcProjectCreator)
}

// =============================================================================
// Consumer configurations for artifacts from sibling projects
// =============================================================================

// Configuration to consume TextMate grammar from dsl project
val textMateGrammar: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("textmate-grammar"))
    }
}

// =============================================================================
// Consumer configuration for LSP server fat JAR (out-of-process execution)
// =============================================================================
// The LSP server runs as a separate Java process because:
// 1. jtreesitter requires Java 23+ (FFM API), but IntelliJ uses JBR 21
// 2. Out-of-process allows using the XDK's Java 24 toolchain
// See doc/plans/PLAN_OUT_OF_PROCESS_LSP.md for architecture details.

val lspServerJar: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
    }
}

// Configuration to consume LSP version properties for display in IDE
val lspVersionProperties: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("lsp-version-properties"))
    }
}

dependencies {
    // LSP server fat JAR for out-of-process execution
    lspServerJar(project(path = ":lsp-server", configuration = "lspServerElements"))

    // LSP version properties for displaying version in IDE
    lspVersionProperties(project(path = ":lsp-server", configuration = "lspVersionProperties"))

    // CompileOnly dependency for version properties and compile-time type checking
    // (the actual server runs out-of-process via the fat JAR above)
    compileOnly(project(":lsp-server"))

    // JSON serialization for JSON-RPC protocol messages
    implementation(libs.kotlinx.serialization.json)

    intellijPlatform {
        // Use local IDE if available to avoid Gradle transform cache issues on macOS
        if (useLocalIde) {
            local(localIntelliJ!!.absolutePath)
        } else {
            intellijIdeaCommunity(
                libs.versions.intellij.ide
                    .get(),
            )
        }
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("org.jetbrains.plugins.textmate")
        plugin("com.redhat.devtools.lsp4ij", libs.versions.lsp4ij.get())
        // pluginVerifier() - only enable when publishing to verify compatibility
    }

    textMateGrammar(project(path = ":dsl", configuration = "textMateElements"))
}

// IntelliJ 2025.1 runs on JDK 21, so we must target JDK 21 (not the project's JDK 25)
val intellijJdkVersion: Int =
    libs.versions.intellij.jdk
        .get()
        .toInt()

// Derive sinceBuild from IDE version: "2025.1" -> "251" (last 2 digits of year + major version)
val intellijIdeVersion: String =
    libs.versions.intellij.ide
        .get()
val intellijSinceBuild: String =
    run {
        val parts = intellijIdeVersion.split(".")
        val year = parts[0].takeLast(2)
        val major = parts.getOrElse(1) { "0" }
        "$year$major"
    }

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(intellijJdkVersion))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(intellijJdkVersion))
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "org.xtclang.idea"
        name = "XTC Language Support"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = intellijSinceBuild
            untilBuild = provider { null } // No upper bound - compatible with future versions
        }

        changeNotes =
            """
            <h2>$xdkVersion</h2>
            <ul>
                <li>Initial alpha release</li>
                <li>New Project wizard for XTC projects</li>
                <li>Run configurations for XTC applications</li>
                <li>File type support for .x files</li>
                <li>LSP-based language features</li>
            </ul>
            """.trimIndent()
    }

    signing {
        certificateChain = providers.environmentVariable("JETBRAINS_CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("JETBRAINS_PRIVATE_KEY")
        password = providers.environmentVariable("JETBRAINS_PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("JETBRAINS_TOKEN")
        channels = listOf(releaseChannel)
    }
}

val publishCheck by tasks.registering {
    doFirst {
        if (!enablePublish) {
            throw GradleException(
                """
                |Publishing is disabled by default.
                |To publish the plugin, run:
                |  ./gradlew publishPlugin -PenablePublish=true
                |
                |Current version: $xdkVersion
                |Release channel: $releaseChannel
                |
                |Required environment variables:
                |  JETBRAINS_TOKEN - Your JetBrains Marketplace token
                |
                |Optional (for signed releases):
                |  JETBRAINS_CERTIFICATE_CHAIN - Base64-encoded certificate chain
                |  JETBRAINS_PRIVATE_KEY - Base64-encoded private key
                |  JETBRAINS_PRIVATE_KEY_PASSWORD - Private key password
                """.trimMargin(),
            )
        }
    }
}

val publishPlugin by tasks.existing {
    enabled = enablePublish
    dependsOn(publishCheck)
}

val buildSearchableOptions by tasks.existing {
    enabled = false // Speeds up build; enable for production
}

// =============================================================================
// Copy TextMate Grammar into plugin lib directory
// =============================================================================
// TextMate grammar provides syntax highlighting via IntelliJ's TextMate plugin.
// The grammar files must be on the filesystem (not inside JAR) for TextMateBundleProvider.

// Get typed reference to IntelliJ Platform tasks
val prepareSandbox by tasks.existing(Sync::class)

// Derive TextMate destination from prepareSandbox's output (works with any IDE version)
// prepareSandbox.destinationDir is the plugins/ directory, we need plugins/<plugin-name>/lib/textmate
val sandboxPluginTextMate: Provider<File> = prepareSandbox.map { it.destinationDir.resolve("intellij-plugin/lib/textmate") }

val copyTextMateToSandbox by tasks.registering(Sync::class) {
    group = "build"
    description = "Copy TextMate grammar into plugin sandbox lib directory"

    from(textMateGrammar)
    into(sandboxPluginTextMate)

    // Ensure DSL test compilation completes before copying (Gradle detected potential conflict with build/generated)
    mustRunAfter(project(":dsl").tasks.named("compileTestJava"))
}

// =============================================================================
// Copy LSP Server Fat JAR into plugin bin directory (NOT lib!)
// =============================================================================
// The LSP server runs as a SEPARATE Java process and MUST NOT be on the plugin
// classpath. If placed in lib/, IntelliJ loads its bundled lsp4j classes which
// conflict with LSP4IJ's own lsp4j, causing classloader errors.
//
// Location: plugins/intellij-plugin/bin/xtc-lsp-server.jar (off classpath)

val sandboxPluginBin: Provider<File> = prepareSandbox.map { it.destinationDir.resolve("intellij-plugin/bin") }

val copyLspServerToSandbox by tasks.registering(Copy::class) {
    group = "build"
    description = "Copy LSP server fat JAR into plugin sandbox bin directory (off classpath)"

    from(lspServerJar) {
        rename { "xtc-lsp-server.jar" }
    }
    into(sandboxPluginBin)

    // Ensure bin/ directory exists (prepareSandbox only creates lib/)
    // Capture the directory path at configuration time for cache compatibility
    val binDir = sandboxPluginBin
    doFirst {
        binDir.get().mkdirs()
    }
}

prepareSandbox.configure {
    finalizedBy(copyTextMateToSandbox)
    finalizedBy(copyLspServerToSandbox)
}

val prepareJarSearchableOptions by tasks.existing {
    // Explicit dependencies ensure copy tasks complete before scanning the sandbox lib directory.
    // These tasks share the sandbox lib output directory, so ordering must be explicit.
    dependsOn(copyTextMateToSandbox)
    dependsOn(copyLspServerToSandbox)
}

// =============================================================================
// Disable Ultimate-only plugins in the sandbox
// =============================================================================
// The local IDE detection (findLocalIntelliJ) may find Ultimate instead of Community.
// Ultimate bundles plugins that require com.intellij.modules.ultimate, which isn't
// available in the sandbox since we target Community. This causes errors like:
//   "Plugin 'Kubernetes' requires plugin with id=com.intellij.modules.ultimate"
//
// By disabling the Ultimate module marker, IntelliJ automatically disables all
// plugins that depend on it, giving us a clean Community-equivalent sandbox.

// Derive sandbox config dir from prepareSandbox (handles versioned sandbox directories like IU-2025.3.1.1)
// prepareSandbox.destinationDir is the plugins/ directory, config/ is a sibling
val sandboxConfigDir: Provider<File> = prepareSandbox.map { it.destinationDir.parentFile.resolve("config") }

// Plugins to disable in sandbox (Ultimate-only or problematic split-architecture plugins)
val disabledSandboxPlugins =
    listOf(
        "com.intellij.modules.ultimate",
        "com.intellij.kubernetes", // Split frontend/backend - partially loads even with ultimate disabled
        "com.intellij.clouds.kubernetes", // Another Kubernetes component
    )

val configureDisabledPlugins by tasks.registering {
    group = "intellij platform"
    description = "Disable Ultimate-only plugins in the sandbox IDE"

    // Must run after prepareSandbox creates the directory structure
    dependsOn(prepareSandbox)
    mustRunAfter(prepareSandbox)

    val configDir = sandboxConfigDir // Capture for configuration cache
    val pluginsList = disabledSandboxPlugins
    inputs.property("disabledPlugins", pluginsList)
    outputs.dir(configDir) // Output is the config directory

    doLast {
        val disabledPluginsFile = configDir.get().resolve("disabled_plugins.txt")
        disabledPluginsFile.parentFile.mkdirs()
        disabledPluginsFile.writeText(pluginsList.joinToString("\n") + "\n")
    }
}

// Ensure TextMate files and LSP server JAR are copied before IDE starts
// NOTE: finalizedBy doesn't guarantee completion, so we need explicit dependsOn
val runIde by tasks.existing {
    dependsOn(copyTextMateToSandbox)
    dependsOn(copyLspServerToSandbox)
    dependsOn(configureDisabledPlugins)
}

// No tests in this module - disable test task to avoid IntelliJ plugin test infrastructure overhead
val test by tasks.existing {
    enabled = false
}
