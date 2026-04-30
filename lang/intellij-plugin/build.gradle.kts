import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.lang.kotlin.jvm)
    alias(libs.plugins.lang.ktlint)
    alias(libs.plugins.lang.intellij.platform)
}

// Access version from xdkProperties (set by xdk.build.properties plugin)
val xdkVersion: String = project.version.toString()
val releaseChannel: String = xdkProperties.stringValue("xdk.intellij.release.channel", "alpha")

// Publishing is disabled by default. Enable with: ./gradlew publishPlugin -PenablePublish=true
val enablePublish = providers.gradleProperty("enablePublish").map { it.toBoolean() }.getOrElse(false)
val enablePublishProvider = providers.gradleProperty("enablePublish").map { it.toBoolean() }.orElse(false)
val xdkVersionProvider = providers.provider { project.version.toString() }
val releaseChannelProvider = xdkProperties.string("xdk.intellij.release.channel", "alpha")
// NOTE: For CI, this token is also stored as the xtclang GitHub organization Actions secret
// named JETBRAINS_TOKEN. xdkProperties resolves that env var via the local key "jetbrains.token".
val jetbrainsTokenProvider = xdkProperties.string("jetbrains.token", "")
val jetbrainsPublishSuffixOverrideProvider = xdkProperties.string("jetbrains.publish.suffix", "")
val utcPublishTimestamp: String =
    DateTimeFormatter
        .ofPattern("yyyyMMddHHmmss")
        .withZone(ZoneOffset.UTC)
        .format(Instant.now())
val jetbrainsPublishVersionProvider =
    providers.provider {
        val baseVersion = xdkVersionProvider.get()
        val publishEnabled = enablePublishProvider.get()
        if (!publishEnabled || !baseVersion.endsWith("-SNAPSHOT")) {
            return@provider baseVersion
        }
        val suffix = jetbrainsPublishSuffixOverrideProvider.get().ifBlank { utcPublishTimestamp }
        "$baseVersion.$suffix"
    }
val usesGeneratedJetBrainsPublishSuffix: Boolean =
    enablePublish && xdkVersion.endsWith("-SNAPSHOT") && jetbrainsPublishSuffixOverrideProvider.get().isBlank()

// Log level: -Plog=DEBUG or XTC_LOG_LEVEL=DEBUG (default: INFO)
// Propagated to the IDE JVM as a system property and environment variable, so the
// IntelliJ plugin passes it to the out-of-process LSP/DAP server child processes.
// Resolved via xdkProperties to read from composite root gradle.properties.
val logLevel: String =
    xdkProperties
        .stringValue(
            "log",
            System.getenv("XTC_LOG_LEVEL")?.uppercase() ?: "INFO",
        ).uppercase()

// Run the branch's full editor path by default: LSP semantic tokens on.
// Override with -Pxtc.intellij.semanticTokens=false when debugging TextMate-only behavior.
val ideLspSemanticTokens: String = xdkProperties.stringValue("xtc.intellij.semanticTokens", "true")

abstract class PublishCheckTask : DefaultTask() {
    @get:Input
    abstract val publishEnabled: Property<Boolean>

    @get:Input
    abstract val xdkVersion: Property<String>

    @get:Input
    abstract val releaseChannel: Property<String>

    @get:Input
    abstract val jetbrainsTokenPresent: Property<Boolean>

    @get:Input
    abstract val publishVersion: Property<String>

    @TaskAction
    fun validate() {
        if (publishEnabled.get()) {
            if (!jetbrainsTokenPresent.get()) {
                throw GradleException(
                    """
                    |JetBrains Marketplace token missing.
                    |
                    |Set one of:
                    |  jetbrains.token=perm:...
                    |  JETBRAINS_TOKEN=perm:...
                    |
                    |Base version: ${xdkVersion.get()}
                    |Publish version: ${publishVersion.get()}
                    |Release channel: ${releaseChannel.get()}
                    """.trimMargin(),
                )
            }
            return
        }

        throw GradleException(
            """
            |Publishing is disabled by default.
            |To publish the plugin, run:
            |  ./gradlew publishPlugin -PenablePublish=true
            |
            |Base version: ${xdkVersion.get()}
            |Publish version: ${publishVersion.get()}
            |Release channel: ${releaseChannel.get()}
            |
            |Required credential sources:
            |  jetbrains.token=perm:...    (recommended in ~/.gradle/gradle.properties)
            |  JETBRAINS_TOKEN=perm:...    (CI / environment variable)
            |
            |Optional (for signed releases):
            |  JETBRAINS_CERTIFICATE_CHAIN - Base64-encoded certificate chain
            |  JETBRAINS_PRIVATE_KEY - Base64-encoded private key
            |  JETBRAINS_PRIVATE_KEY_PASSWORD - Private key password
            """.trimMargin(),
        )
    }
}

abstract class SummarizeSearchableOptionsTask : DefaultTask() {
    @get:InputFile
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun summarize() {
        val manifestFile = manifestFile.get().asFile
        val outputDir = manifestFile.parentFile
        if (!manifestFile.isFile) {
            throw GradleException(
                """
                |Searchable options output not found at:
                |  ${manifestFile.absolutePath}
                |
                |Run one of:
                |  ./gradlew -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.buildSearchableOptions=true :lang:intellij-plugin:buildPlugin
                |  ./gradlew -PincludeBuildLang=true -PincludeBuildAttachLang=true -Plsp.buildSearchableOptions=true :lang:intellij-plugin:buildSearchableOptions
                """.trimMargin(),
            )
        }

        @Suppress("UNCHECKED_CAST")
        val manifest = JsonSlurper().parse(manifestFile) as Map<String, List<Map<String, Any>>>
        val bundleNames = manifest.keys.sorted()
        val xtcBundles =
            bundleNames.filter { name ->
                name.contains("xtc", ignoreCase = true) || name.contains("ecstasy", ignoreCase = true)
            }
        val bundleFiles =
            manifest.entries
                .flatMap { (_, records) -> records }
                .mapNotNull { record ->
                    val file = record["file"]?.toString() ?: return@mapNotNull null
                    val size = (record["size"] as? Number)?.toLong() ?: 0L
                    file to size
                }.sortedByDescending { (_, size) -> size }

        logger.lifecycle(
            """
            |[searchable-options] Output directory: ${outputDir.absolutePath}
            |[searchable-options] Bundle manifest: ${manifestFile.absolutePath}
            |[searchable-options] Bundles indexed: ${bundleNames.size}
            |[searchable-options] XTC/Ecstasy bundles: ${if (xtcBundles.isEmpty()) "none detected" else xtcBundles.joinToString()}
            |[searchable-options] Largest bundles:
            |${bundleFiles.take(10).joinToString("\n") { (file, size) -> "  - $file (${size.humanSize()})" }}
            |
            |[searchable-options] Sample bundle names:
            |${bundleNames.take(20).joinToString("\n") { "  - $it" }}
            """.trimMargin(),
        )
    }
}

// =============================================================================
// IntelliJ IDE Resolution
// =============================================================================
// IntelliJ Community is downloaded and cached under lang/.intellijPlatform/ides/
// by the IntelliJ Platform Gradle Plugin. The cached IDE is keyed by version,
// so changing the version in libs.versions.toml automatically downloads the
// new version on the next build.
//
// To force a fresh re-download, delete the cache directory:
//   rm -rf lang/.intellijPlatform/ides
// Then run any task that requires the IDE (e.g. ./gradlew :lang:intellij-plugin:runIde).

val ideVersion =
    libs.versions.lang.intellij.ide
        .get()

logger.info("[ide] IntelliJ IDEA $ideVersion (managed by IntelliJ Platform Gradle Plugin)")
logger.info("[ide]   First-time download may take several minutes if not already cached.")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// =============================================================================
// Sync XtcProjectCreator from javatools (compiled for Java 25)
// =============================================================================
// XtcProjectCreator is a standalone class with no javatools dependencies.
// We sync it here and compile it for Java 25 (IntelliJ's JBR runtime).

val syncedJavaSourceDir: Provider<Directory> = layout.buildDirectory.dir("generated/synced-java")

val syncXtcProjectCreator by tasks.registering(Copy::class) {
    description = "Sync XtcProjectCreator.java from javatools for Java 25 compilation"
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
    // TODO: Re-enable org/xtclang/idea/dap/** once DAP is implemented and user-visible.
    // Exclude it from the published plugin for now so Plugin Verifier does not report
    // experimental LSP4IJ DAP APIs that users cannot currently access.
    kotlin.exclude("org/xtclang/idea/dap/**")
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
// The LSP server runs as a separate Java process for classloader isolation
// (avoids lsp4j version conflicts with LSP4IJ) and crash/memory isolation.
// See doc/plans/PLAN_IDE_INTEGRATION.md for architecture details.

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
    testRuntimeOnly(libs.lang.intellij.junit4.compat)
    testImplementation(libs.assertj)

    // LSP server fat JAR for out-of-process execution
    lspServerJar(project(path = ":lsp-server", configuration = "lspServerElements"))

    // LSP version properties for displaying version in IDE
    lspVersionProperties(project(path = ":lsp-server", configuration = "lspVersionProperties"))

    // CompileOnly dependency for version properties and compile-time type checking
    // (the actual server runs out-of-process via the fat JAR above)
    compileOnly(project(":lsp-server"))

    intellijPlatform {
        intellijIdea(
            libs.versions.lang.intellij.ide
                .get(),
        )
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("org.jetbrains.plugins.textmate")
        plugin(
            "com.redhat.devtools.lsp4ij",
            libs.versions.lang.intellij.lsp4ij
                .get(),
        )
        pluginVerifier()
    }

    textMateGrammar(project(path = ":dsl", configuration = "textMateElements"))
}

// Use the same JDK version as the rest of the XDK (from version.properties).
// IntelliJ 2026.1+ ships JBR 25, which matches the project's minimum JDK floor;
// the binary verifier (verifyPlugin) catches any future since-build/JBR mismatch.
val jdkVersion = xdkProperties.int("org.xtclang.java.jdk")

// Derive sinceBuild from IDE version: "2026.1" -> "261" (last 2 digits of year + major version)
val intellijIdeVersion: String =
    libs.versions.lang.intellij.ide
        .get()
val intellijSinceBuild: String =
    run {
        val parts = intellijIdeVersion.split(".")
        val year = parts[0].takeLast(2)
        val major = parts.getOrElse(1) { "0" }
        "$year$major"
    }

// Kotlin auto-inherits this toolchain (no explicit kotlin.jvmToolchain needed).
java {
    toolchain {
        languageVersion.set(jdkVersion.map { JavaLanguageVersion.of(it) })
    }
}

intellijPlatform {
    caching {
        ides {
            enabled = true
            path = rootProject.layout.projectDirectory.dir(".intellijPlatform/ides")
            name = { requested -> "${requested.type}-${requested.version}" }
        }
    }

    pluginConfiguration {
        id = "org.xtclang.idea"
        // The language is officially "Ecstasy"; "XTC" is the file extension
        // and informal/legacy short-form. Both names appear in the published
        // name and description so JetBrains Marketplace search returns the
        // plugin for either query.
        name = "Ecstasy Language Support"
        version = jetbrainsPublishVersionProvider.get()

        ideaVersion {
            sinceBuild = intellijSinceBuild
            untilBuild = provider { null } // No upper bound - compatible with future versions
        }

        changeNotes =
            """
            <h2>$xdkVersion</h2>
            <ul>
                <li>Initial alpha release</li>
                <li>New Project wizard for Ecstasy (XTC) projects</li>
                <li>Run configurations for Ecstasy (XTC) applications</li>
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
        token = jetbrainsTokenProvider
        channels = listOf(releaseChannel)
    }

    // Plugin Verifier statically analyses the built plugin against real IDE
    // distributions to catch binary-incompat regressions (removed APIs, missing
    // classes, signature changes) before users hit them. `recommended()` verifies
    // against every IDE build in the plugin's declared compatibility range
    // (since-build "261" through any future until-build), so newly-released
    // 2026.1.x patches and 2026.2 EAPs are checked as soon as JetBrains
    // publishes them. failureLevel is explicit so the build fails on real
    // breaks but not on every deprecated/experimental API touch.
    pluginVerification {
        ides {
            recommended()
        }
        failureLevel =
            listOf(
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
                VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
            )
    }
}

val publishCheck by tasks.registering(PublishCheckTask::class) {
    publishEnabled.set(enablePublishProvider)
    xdkVersion.set(xdkVersionProvider)
    releaseChannel.set(releaseChannelProvider)
    jetbrainsTokenPresent.set(jetbrainsTokenProvider.map { it.isNotBlank() })
    publishVersion.set(jetbrainsPublishVersionProvider)
}

val summarizeSearchableOptions by tasks.registering(SummarizeSearchableOptionsTask::class) {
    group = "verification"
    description = "Summarize the generated searchable options manifest for the IntelliJ plugin build."
    mustRunAfter(buildSearchableOptions)
    manifestFile.set(layout.buildDirectory.file("tmp/buildSearchableOptions/content.json"))
}

val verifyPlugin by tasks.existing

val publishPlugin by tasks.existing {
    enabled = enablePublish
    dependsOn(publishCheck)
    // Enforce binary-compat verification before publishing, regardless of who
    // invokes publishPlugin (CI, local, snapshot pipeline). Catches removed/
    // changed APIs and missing dependencies that would otherwise reach users.
    dependsOn(verifyPlugin)
    if (usesGeneratedJetBrainsPublishSuffix) {
        notCompatibleWithConfigurationCache("JetBrains snapshot publish version uses a generated UTC timestamp suffix for uniqueness.")
    }
}

// Searchable options allow IntelliJ's Settings search (Cmd+Shift+A / Ctrl+Shift+A) to index
// this plugin's settings pages and configuration UI text. When disabled, users cannot find
// plugin settings via the search bar — they must navigate to them manually. Enabling it adds
// ~30-60s to the build because it launches a headless IDE to index all settings pages.
// Enable with: -Plsp.buildSearchableOptions=true
val buildSearchableOptionsEnabled = xdkProperties.booleanValue("lsp.buildSearchableOptions", false)

val searchableOptionsStatus =
    if (buildSearchableOptionsEnabled) "enabled" else "disabled (use -Plsp.buildSearchableOptions=true to enable)"
logger.info("[ide] Searchable options: $searchableOptionsStatus")

val buildSearchableOptions by tasks.existing {
    enabled = buildSearchableOptionsEnabled
    inputs.property("buildSearchableOptionsEnabled", buildSearchableOptionsEnabled)
    // IntelliJ headless tasks use custom classloading that triggers harmless CDS/class-sharing
    // warnings from the JVM. Suppress them consistently so normal builds stay readable.
    (this as JavaExec).jvmArgs("-Xlog:cds=off")
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
            logger.info(
                """
                |[plugin] Distribution ZIP: $relPath (${zip.length().humanSize()})
                |[plugin] Absolute path:    ${zip.absolutePath}
                |[plugin]
                |[plugin] To install in IntelliJ IDEA:
                |[plugin]   1. Open Settings -> Plugins -> gear icon -> Install Plugin from Disk...
                |[plugin]   2. Select: ${zip.absolutePath}
                |[plugin]   3. Restart the IDE
                """.trimMargin(),
            )
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
// Disable problematic plugins in the sandbox
// =============================================================================
// Some bundled plugins have split frontend/backend architectures that fail to
// load cleanly in the sandbox. Disable them to avoid spurious errors.

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
        logger.info("[sandbox] Disabled ${pluginsList.size} Ultimate-only plugins in sandbox config")
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
        logger.info("[sandbox] Configured INFO-level logging for org.xtclang")
    }
}

val configureSandboxAppearance by tasks.registering {
    group = "intellij platform"
    description = "Remove broken user-derived color-scheme overrides from the sandbox IDE config"

    dependsOn(prepareSandbox)
    mustRunAfter(prepareSandbox)

    val configDir = sandboxConfigDir
    outputs.dir(configDir)

    doLast {
        val colorsSchemeFile = configDir.get().resolve("options/colors.scheme.xml")
        if (!colorsSchemeFile.isFile) {
            return@doLast
        }

        val text = colorsSchemeFile.readText()
        if ("_@user_" !in text) {
            logger.info("[sandbox] Preserving sandbox color scheme override: ${colorsSchemeFile.absolutePath}")
            return@doLast
        }

        colorsSchemeFile.delete()
        logger.info("[sandbox] Removed user-derived sandbox color scheme override: ${colorsSchemeFile.absolutePath}")
    }
}

// Ensure the Ecstasy Gradle plugin and XDK are published to mavenLocal before the sandbox IDE starts.
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

val runIdeCapturedIdeVersion = ideVersion
val runIdeCapturedSinceBuild = intellijSinceBuild
val runIdeCapturedLsp4ijVersion =
    libs.versions.lang.intellij.lsp4ij
        .get()
val runIdeCapturedPluginVersion = project.version.toString()
val runIdeCapturedSemanticTokens = ideLspSemanticTokens

val runIdeInfo by tasks.registering(RunIdeEnvironmentReportTask::class) {
    dependsOn(
        copyTextMateToSandbox,
        copyLspServerToSandbox,
        configureDisabledPlugins,
        configureSandboxLogging,
        configureSandboxAppearance,
    )
    ideVersion.set(runIdeCapturedIdeVersion)
    sinceBuild.set(runIdeCapturedSinceBuild)
    lsp4ijVersion.set(runIdeCapturedLsp4ijVersion)
    pluginVersion.set(runIdeCapturedPluginVersion)
    semanticTokensEnabled.set(runIdeCapturedSemanticTokens)
    sandboxDir.set(layout.dir(sandboxConfigDir.map { it.parentFile }))
    val mavenLocalRoot =
        providers
            .systemProperty("maven.repo.local")
            .orElse(providers.systemProperty("user.home").map { "$it/.m2/repository" })
    this.mavenLocalRoot.set(layout.dir(mavenLocalRoot.map(::File)))
    pluginNames.set(
        sandboxConfigDir.map { configDir ->
            configDir.parentFile
                .resolve("plugins")
                .listFiles()
                ?.map { it.name }
                ?.sorted() ?: emptyList()
        },
    )
    lspLogFile.set(layout.file(providers.systemProperty("user.home").map { File(it, ".xtc/logs/lsp-server.log") }))
}

val startLspLogTail by tasks.registering(StartLogTailTask::class) {
    logFile.set(layout.file(providers.systemProperty("user.home").map { File(it, ".xtc/logs/lsp-server.log") }))
    threadName.set("lsp-log-tailer")
    linePrefix.set("[lsp-server] ")
}

val stopLspLogTail by tasks.registering(StopLogTailTask::class) {
    threadName.set("lsp-log-tailer")
}

// Ensure TextMate files, LSP server JAR, and mavenLocal artifacts are ready before IDE starts
// NOTE: finalizedBy doesn't guarantee completion, so we need explicit dependsOn
val runIde by tasks.existing {
    parentPublishLocal.forEach { dependsOn(it) }
    dependsOn(
        copyTextMateToSandbox,
        copyLspServerToSandbox,
        configureDisabledPlugins,
        configureSandboxLogging,
        configureSandboxAppearance,
        runIdeInfo,
        startLspLogTail,
    )

    // Pass log level to the IDE JVM so the IntelliJ plugin can forward it to the
    // out-of-process LSP server. Set both system property and env var for robustness.
    (this as JavaExec).apply {
        systemProperty("xtc.logLevel", logLevel)
        environment("XTC_LOG_LEVEL", logLevel)
        systemProperty("xtc.lsp.semanticTokens", ideLspSemanticTokens)
        environment("XTC_LSP_SEMANTIC_TOKENS", ideLspSemanticTokens)
        // IntelliJ's classloader setup disables JVM CDS optimizations and otherwise prints
        // harmless class-sharing warnings. Suppress those so runIde output stays focused.
        jvmArgs("-Xlog:cds=off")
        // Sandbox plugin auto-reload is convenient during plugin development, but in this
        // project it can leave IntelliJ holding a stale or partially reloaded plugin JAR
        // while Gradle is still rebuilding and copying artifacts into the sandbox.
        // Disable it for deterministic startup and classloading.
        systemProperty("idea.auto.reload.plugins", "false")
    }

    finalizedBy(stopLspLogTail)
}

// =============================================================================
// Clean sandbox when running 'clean'
// =============================================================================
// The IntelliJ Platform Gradle Plugin stores the sandbox under lang/.intellijPlatform/sandbox/.
// By default, Gradle's clean task only deletes build/classes, build/libs, etc.
// We extend clean to also delete the actual sandbox used by runIde so that version
// or dependency changes are picked up on the next run. The downloaded IDE distribution
// itself is cached separately and is NOT affected by clean.

val clean by tasks.existing(Delete::class) {
    delete(layout.projectDirectory.dir("../.intellijPlatform/sandbox/intellij-plugin"))
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
        events("failed")
    }
}
