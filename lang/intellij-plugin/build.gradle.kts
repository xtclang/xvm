import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

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
val enablePublish = providers.gradleProperty("enablePublish").map { it.toBoolean() }.getOrElse(false)

// =============================================================================
// IntelliJ IDE Resolution
// =============================================================================
// By default, IntelliJ Community is downloaded and cached in $GRADLE_USER_HOME
// (the Gradle dependency cache). The cached IDE is keyed by version, so changing
// the version in libs.versions.toml automatically downloads the new version on
// the next build.
//
// To use a locally installed IDE instead (faster startup, no download):
//   -PuseLocalIde=true          Auto-detect a local IntelliJ installation
//   -PintellijLocalPath=/path   Use a specific local IDE path

fun findLocalIntelliJ(): File? {
    val explicitPath = providers.gradleProperty("intellijLocalPath").orNull
    if (explicitPath != null) {
        val explicit = File(explicitPath)
        if (explicit.exists()) return explicit
        logger.warn("Explicit intellijLocalPath '$explicitPath' does not exist, searching for installed IDE")
    }

    val osName = System.getProperty("os.name", "").lowercase()
    val userHome = System.getProperty("user.home", "")
    val candidates =
        when {
            "mac" in osName ->
                listOf(
                    "/Applications/IntelliJ IDEA CE.app",
                    "/Applications/IntelliJ IDEA.app",
                    "$userHome/Applications/IntelliJ IDEA CE.app",
                    "$userHome/Applications/IntelliJ IDEA.app",
                )
            "linux" in osName ->
                buildList {
                    add("/opt/idea-IC")
                    add("/opt/intellij-idea-community")
                    add("/usr/share/intellij-idea-community")
                    add("$userHome/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-0")
                    add("$userHome/idea-IC")
                    File("/opt").listFiles()?.forEach { if (it.name.startsWith("idea-IC-")) add(it.path) }
                    File(userHome).listFiles()?.forEach { if (it.name.startsWith("idea-IC-")) add(it.path) }
                }
            "windows" in osName -> {
                val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
                listOf(
                    "$programFiles\\JetBrains\\IntelliJ IDEA Community Edition 2025.1",
                    "$programFiles\\JetBrains\\IntelliJ IDEA Community Edition",
                    "$userHome\\AppData\\Local\\JetBrains\\Toolbox\\apps\\IDEA-C",
                )
            }
            else -> emptyList()
        }

    return candidates.map(::File).firstOrNull(File::exists)
}

// Default: download IntelliJ Community (cached in $GRADLE_USER_HOME by version).
// Opt-in to local IDE: -PuseLocalIde=true or -PintellijLocalPath=/path
val useLocalIde = providers.gradleProperty("useLocalIde").map { it.toBoolean() }.getOrElse(false)
val hasExplicitLocalPath = providers.gradleProperty("intellijLocalPath").isPresent
val localIntelliJ: File? = if (useLocalIde || hasExplicitLocalPath) findLocalIntelliJ() else null

val gradleUserHome = gradle.gradleUserHomeDir
val ideVersion =
    libs.versions.intellij.ide
        .get()

// The IntelliJ Platform Gradle Plugin downloads the IDE distribution into the Gradle module cache:
//   $GRADLE_USER_HOME/caches/modules-2/files-2.1/idea/ideaIC/<version>/
// Bundled plugin metadata is stored locally in: lang/.intellijPlatform/localPlatformArtifacts/
//
// To purge all cached IntelliJ distributions and force a fresh re-download:
//   rm -rf "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/idea"
//   rm -rf lang/.intellijPlatform/localPlatformArtifacts
// Then run any task that requires the IDE (e.g. ./gradlew :lang:intellij-plugin:runIde).

val ideCacheDir =
    File(gradleUserHome, "caches/modules-2/files-2.1/idea/ideaIC/$ideVersion")

when {
    localIntelliJ != null -> {
        logger.warn("[ide] WARNING: Using local IntelliJ IDE: ${localIntelliJ.absolutePath}")
        logger.warn("[ide]   Local IDE mode is brittle - version mismatches between the local")
        logger.warn("[ide]   IDE and the plugin's target SDK can cause subtle runtime errors")
        logger.warn("[ide]   or missing API exceptions.")
        logger.warn("[ide]   Prefer the default sandboxed download for reliable development.")
    }
    else -> {
        if (ideCacheDir.exists()) {
            val sizeBytes =
                ideCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val sizeMb = sizeBytes / (1024 * 1024)
            logger.lifecycle("[ide] IntelliJ Community $ideVersion (cached, ~$sizeMb MB)")
            logger.lifecycle("[ide]   Location: $ideCacheDir")
        } else {
            logger.lifecycle("[ide] IntelliJ Community $ideVersion not cached - will download (~1.5 GB)")
            logger.lifecycle("[ide]   Destination: $ideCacheDir")
            logger.lifecycle("[ide]   First-time download may take several minutes.")
            logger.lifecycle("[ide]   To use a local IDE instead: -PuseLocalIde=true")
        }
    }
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
    // Test dependencies
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly("junit:junit:4.13.2") // Required by IntelliJ Platform test harness
    testImplementation(libs.assertj)

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
        // Default: download IntelliJ Community (cached in $GRADLE_USER_HOME by version)
        // Use -PuseLocalIde=true or -PintellijLocalPath=/path for local IDE
        if (localIntelliJ != null) {
            local(localIntelliJ.absolutePath)
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

// Searchable options allow IntelliJ's Settings search (Cmd+Shift+A / Ctrl+Shift+A) to index
// this plugin's settings pages and configuration UI text. When disabled, users cannot find
// plugin settings via the search bar — they must navigate to them manually. Enabling it adds
// ~30-60s to the build because it launches a headless IDE to index all settings pages.
// Enable with: -Plsp.buildSearchableOptions=true
val buildSearchableOptionsEnabled =
    providers
        .gradleProperty("lsp.buildSearchableOptions")
        .map { it.toBoolean() }
        .getOrElse(false)

val searchableOptionsStatus =
    if (buildSearchableOptionsEnabled) "enabled" else "disabled (use -Plsp.buildSearchableOptions=true to enable)"
logger.lifecycle("[ide] Searchable options: $searchableOptionsStatus")

val buildSearchableOptions by tasks.existing {
    enabled = buildSearchableOptionsEnabled
    inputs.property("buildSearchableOptionsEnabled", buildSearchableOptionsEnabled)
}

// =============================================================================
// Plugin Distribution ZIP
// =============================================================================
// The buildPlugin task creates a distributable ZIP archive that can be installed
// in any IntelliJ IDE. Log the output path and installation instructions.

val buildPlugin by tasks.existing {
    val distDir = layout.buildDirectory.dir("distributions")
    val rootDir = project.rootDir
    doLastTask {
        val dir = distDir.get().asFile
        val zipFiles = dir.listFiles { f -> f.extension == "zip" }.orEmpty()
        if (zipFiles.isNotEmpty()) {
            val zip = zipFiles.first()
            val relPath = zip.relativeTo(rootDir)
            logger.lifecycle("[plugin] Distribution ZIP: $relPath (${zip.length().humanSize()})")
            logger.lifecycle("[plugin] Absolute path:    ${zip.absolutePath}")
            logger.lifecycle("[plugin]")
            logger.lifecycle("[plugin] To install in IntelliJ IDEA:")
            logger.lifecycle("[plugin]   1. Open Settings -> Plugins -> gear icon -> Install Plugin from Disk...")
            logger.lifecycle("[plugin]   2. Select: ${zip.absolutePath}")
            logger.lifecycle("[plugin]   3. Restart the IDE")
        }
    }
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

    val rootDir = project.rootDir // local capture for CC safety
    doLastTask { logCopiedFiles("textmate", destinationDir, rootDir) }
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

    // Capture at configuration time for CC safety
    val binDir = sandboxPluginBin
    val rootDir = project.rootDir
    doFirstTask {
        binDir.get().mkdirs()
    }
    doLastTask {
        logCopiedFiles("lsp", binDir.get(), rootDir, "Adapter: tree-sitter (out-of-process, off classpath)")
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
        logger.lifecycle("[sandbox] Disabled ${pluginsList.size} Ultimate-only plugins in sandbox config")
    }
}

// Enable INFO-level logging for the XTC plugin in the sandbox IDE.
// IntelliJ defaults to WARN, which hides useful JRE resolution and LSP lifecycle messages.
val configureSandboxLogging by tasks.registering {
    group = "intellij platform"
    description = "Configure INFO-level logging for XTC plugin in the sandbox IDE"

    dependsOn(prepareSandbox)
    mustRunAfter(prepareSandbox)

    val configDir = sandboxConfigDir
    outputs.dir(configDir)

    doLast {
        val optionsDir = configDir.get().resolve("options")
        optionsDir.mkdirs()
        val logCategoriesFile = optionsDir.resolve("log-categories.xml")
        logCategoriesFile.writeText(
            """
            |<application>
            |  <component name="Logs.Categories"><![CDATA[{"org.xtclang":"INFO"}]]></component>
            |</application>
            """.trimMargin() + "\n",
        )
        logger.lifecycle("[sandbox] Configured INFO-level logging for org.xtclang")
    }
}

// Ensure the XTC Gradle plugin and XDK are published to mavenLocal before the sandbox IDE starts.
// The sandbox IDE resolves the plugin from mavenLocal (not from the composite includeBuild),
// so publishToMavenLocal must complete first. gradle.parent reaches the root build that has
// the "xdk" and "plugin" included builds.
val parentPublishLocal =
    gradle.parent?.let { parent ->
        listOf(
            parent.includedBuild("xdk").task(":publishToMavenLocal"),
            parent.includedBuild("plugin").task(":publishToMavenLocal"),
        )
    } ?: emptyList()

// Ensure TextMate files, LSP server JAR, and mavenLocal artifacts are ready before IDE starts
// NOTE: finalizedBy doesn't guarantee completion, so we need explicit dependsOn
val runIde by tasks.existing {
    parentPublishLocal.forEach { dependsOn(it) }
    dependsOn(
        copyTextMateToSandbox,
        copyLspServerToSandbox,
        configureDisabledPlugins,
        configureSandboxLogging,
    )

    // Log sandbox location, mavenLocal status, version info, and idea.log path
    val sandboxDir = sandboxConfigDir.map { it.parentFile }
    val mavenLocalRoot =
        providers
            .systemProperty("maven.repo.local")
            .orElse(providers.systemProperty("user.home").map { "$it/.m2/repository" })
    val m2Repo = mavenLocalRoot.map { File(it, "org/xtclang") }
    val lspLogFile = providers.systemProperty("user.home").map { File(it, ".xtc/logs/lsp-server.log") }
    val capturedIdeVersion = ideVersion
    val capturedLsp4ijVersion = libs.versions.lsp4ij.get()
    val capturedSinceBuild = intellijSinceBuild
    val capturedIdeCacheDir = ideCacheDir
    val capturedGradleUserHome = gradleUserHome
    val capturedGradleVersion = gradle.gradleVersion
    val capturedPluginVersion = project.version.toString()
    doFirstTask {
        val sandbox = sandboxDir.get()
        val ideaLog = sandbox.resolve("log/idea.log")
        val pluginsDir = sandbox.resolve("plugins")
        val systemDir = sandbox.resolve("system")

        // Version matrix - all pinned in gradle/libs.versions.toml
        logger.lifecycle("[runIde] ─── Version Matrix (gradle/libs.versions.toml) ───")
        logger.lifecycle("[runIde]   IntelliJ CE:  $capturedIdeVersion (sinceBuild=$capturedSinceBuild)")
        logger.lifecycle("[runIde]   LSP4IJ:       $capturedLsp4ijVersion")
        logger.lifecycle("[runIde]   XTC plugin:   $capturedPluginVersion")

        // IDE cache layers
        logger.lifecycle("[runIde] ─── IDE Cache Layers ───")
        logger.lifecycle("[runIde]   Download:  ${capturedIdeCacheDir.absolutePath}")
        if (capturedIdeCacheDir.exists()) {
            val sizeMb = capturedIdeCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024)
            logger.lifecycle("[runIde]              (cached, ~$sizeMb MB - survives clean)")
        } else {
            logger.lifecycle("[runIde]              (not cached - will download on demand)")
        }
        logger.lifecycle("[runIde]   Extracted: $capturedGradleUserHome/caches/$capturedGradleVersion/transforms/...")
        logger.lifecycle("[runIde]              (Gradle artifact transform - survives clean)")

        // Sandbox status
        logger.lifecycle("[runIde] ─── Sandbox ───")
        logger.lifecycle("[runIde]   Path:      ${sandbox.absolutePath}")
        val sandboxIsReused = systemDir.exists() && systemDir.listFiles().orEmpty().isNotEmpty()
        if (sandboxIsReused) {
            logger.lifecycle("[runIde]   Status:    reused (existing sandbox with IDE caches/indices)")
        } else {
            logger.lifecycle("[runIde]   Status:    fresh (new sandbox - first-run indexing will be slower)")
        }
        logger.lifecycle("[runIde]   Plugins:   ${pluginsDir.listFiles()?.map { it.name } ?: emptyList()}")
        logger.lifecycle("[runIde]   IDE log:   ${ideaLog.absolutePath}")
        logger.lifecycle("[runIde]              tail -f ${ideaLog.absolutePath}")

        // mavenLocal artifacts
        val xtcArtifacts = m2Repo.get()
        logger.lifecycle("[runIde] ─── mavenLocal XTC Artifacts ───")
        logger.lifecycle("[runIde]   ${xtcArtifacts.absolutePath}")
        if (xtcArtifacts.exists()) {
            xtcArtifacts.listFiles()?.sorted()?.forEach { artifact ->
                val versions = artifact.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                logger.lifecycle("[runIde]   ${artifact.name}: ${versions.joinToString(", ")}")
            }
        }

        // Recovery instructions
        logger.lifecycle("[runIde] ─── Reset Commands ───")
        logger.lifecycle("[runIde]   Nuke sandbox (keeps IDE download):  ./gradlew :lang:intellij-plugin:clean")
        logger.lifecycle("[runIde]   Nuke everything (re-downloads IDE): rm -rf ${capturedIdeCacheDir.absolutePath}")
        logger.lifecycle("[runIde]              then: rm -rf lang/.intellijPlatform/localPlatformArtifacts")

        // Tail LSP server log file to Gradle console in real time.
        // The LSP server writes to this file via logback's FILE appender.
        // We skip any pre-existing content and only show this session's output.
        // Safe with multiple LSP server processes: logback opens in append mode, and
        // POSIX guarantees atomic appends for small writes (log lines are well under
        // the ~4KB threshold). LSP4IJ may briefly spawn duplicate servers on startup
        // but kills extras within milliseconds, so interleaving is negligible.
        val logFile = lspLogFile.get()
        val startSize = if (logFile.exists()) logFile.length() else 0L
        val taskLogger = logger
        thread(isDaemon = true, name = "lsp-log-tailer") {
            runCatching {
                while (!logFile.exists() && !Thread.currentThread().isInterrupted) Thread.sleep(500)
                val raf = RandomAccessFile(logFile, "r")
                raf.seek(startSize)
                while (!Thread.currentThread().isInterrupted) {
                    val line = raf.readLine()
                    if (line != null) {
                        taskLogger.lifecycle("[lsp-server] $line")
                    } else {
                        Thread.sleep(200)
                    }
                }
                raf.close()
            }
        }
        logger.lifecycle("[runIde] LSP log:  ${logFile.absolutePath} (tailing to console)")
    }

    // Stop the tailer thread when the IDE exits (prevents it lingering in the Gradle daemon)
    doLastTask {
        Thread
            .getAllStackTraces()
            .keys
            .firstOrNull { it.name == "lsp-log-tailer" }
            ?.interrupt()
    }
}

// =============================================================================
// Clean sandbox when running 'clean'
// =============================================================================
// The IntelliJ Platform Gradle Plugin stores the sandbox under build/idea-sandbox/.
// By default, Gradle's clean task only deletes build/classes, build/libs, etc.
// We extend clean to also delete the sandbox so that version or dependency changes
// are picked up on the next run. The downloaded IDE distribution itself is cached
// in $GRADLE_USER_HOME and is NOT affected by clean (only re-downloaded when the
// version in libs.versions.toml changes, managed by Gradle's dependency resolution).

val clean by tasks.existing(Delete::class) {
    delete(layout.buildDirectory.dir("idea-sandbox"))
}

// The IntelliJ Platform Gradle Plugin sets -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader
// on the test JVM so that IntelliJ's plugin classloading works during tests. This causes a harmless JVM warning:
//   [warning][cds] Archived non-system classes are disabled because the java.system.class.loader property is specified
// CDS (Class Data Sharing) is a JVM startup optimization; IntelliJ's custom classloader is incompatible with it
// for non-system classes, so the JVM falls back to normal class loading. This is standard for all IntelliJ plugin
// test suites and is unrelated to the LSP server JAR (which runs in a separate JVM process at runtime).
val test by tasks.existing(Test::class) {
    useJUnitPlatform()
    jvmArgs("-Xlog:cds=off")
    testLogging {
        events("passed", "skipped", "failed")
    }
}
