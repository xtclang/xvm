import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.File

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.kotlin.jvm)
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
    val explicitPath = project.findProperty("intellijLocalPath")?.toString()
    if (explicitPath != null) {
        val explicit = File(explicitPath)
        if (explicit.exists()) return explicit
        logger.warn("Explicit intellijLocalPath '$explicitPath' does not exist, searching for installed IDE")
    }

    val osName = System.getProperty("os.name").lowercase()
    val candidates = when {
        osName.contains("mac") -> listOf(
            "/Applications/IntelliJ IDEA CE.app",
            "/Applications/IntelliJ IDEA.app",
            "${System.getProperty("user.home")}/Applications/IntelliJ IDEA CE.app",
            "${System.getProperty("user.home")}/Applications/IntelliJ IDEA.app"
        )
        osName.contains("linux") -> {
            val home = System.getProperty("user.home")
            listOf(
                "/opt/idea-IC",
                "/opt/intellij-idea-community",
                "/usr/share/intellij-idea-community",
                "$home/.local/share/JetBrains/Toolbox/apps/IDEA-C/ch-0",
                "$home/idea-IC"
            ) + (File("/opt").listFiles()?.filter { it.name.startsWith("idea-IC-") }?.map { it.path } ?: emptyList()) +
              (File(home).listFiles()?.filter { it.name.startsWith("idea-IC-") }?.map { it.path } ?: emptyList())
        }
        osName.contains("windows") -> {
            val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            listOf(
                "$programFiles\\JetBrains\\IntelliJ IDEA Community Edition 2025.1",
                "$programFiles\\JetBrains\\IntelliJ IDEA Community Edition",
                "${System.getProperty("user.home")}\\AppData\\Local\\JetBrains\\Toolbox\\apps\\IDEA-C"
            )
        }
        else -> emptyList()
    }

    return candidates.map { File(it) }.firstOrNull { it.exists() }
}

val localIntelliJ: File? = findLocalIntelliJ()
val useLocalIde = localIntelliJ != null

if (useLocalIde) {
    logger.lifecycle("Using local IntelliJ IDE: ${localIntelliJ!!.absolutePath}")
} else {
    logger.lifecycle("No local IntelliJ IDE found, will download (may cause cache issues on macOS)")
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

val syncedJavaSourceDir = layout.buildDirectory.dir("generated/synced-java")

val syncXtcProjectCreator by tasks.registering(Copy::class) {
    description = "Sync XtcProjectCreator.java from javatools for Java 21 compilation"
    from(rootProject.file("../javatools/src/main/java/org/xvm/tool/XtcProjectCreator.java"))
    into(syncedJavaSourceDir.map { it.dir("org/xvm/tool") })
}

// Sync gradle-wrapper resources needed by XtcProjectCreator
val syncedResourcesDir = layout.buildDirectory.dir("generated/synced-resources")

val syncGradleWrapperResources by tasks.registering(Copy::class) {
    description = "Sync gradle-wrapper resources from javatools"
    from(rootProject.file("../javatools/src/main/resources/gradle-wrapper"))
    into(syncedResourcesDir.map { it.dir("gradle-wrapper") })
}

sourceSets.main {
    java.srcDir(syncedJavaSourceDir)
    resources.srcDir(syncedResourcesDir)
}

val compileJava by tasks.existing {
    dependsOn(syncXtcProjectCreator)
}

val compileKotlin by tasks.existing {
    dependsOn(syncXtcProjectCreator)
}

val processResources by tasks.existing {
    dependsOn(syncGradleWrapperResources)
}

// =============================================================================
// Consumer configurations for artifacts from sibling projects
// =============================================================================

// Configuration to consume TextMate grammar from root project
val textMateGrammar by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("textmate-grammar"))
    }
}


dependencies {
    // LSP server for in-process execution (no subprocess needed)
    implementation(project(":lsp-server"))

    intellijPlatform {
        // Use local IDE if available to avoid Gradle transform cache issues on macOS
        if (useLocalIde) {
            local(localIntelliJ!!.absolutePath)
        } else {
            intellijIdeaCommunity(libs.versions.intellij.ide.get())
        }
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("org.jetbrains.plugins.textmate")
        plugin("com.redhat.devtools.lsp4ij", libs.versions.lsp4ij.get())
        // pluginVerifier() - only enable when publishing to verify compatibility
    }

    textMateGrammar(project(path = ":", configuration = "textMateElements"))
}

// IntelliJ 2025.1 runs on JDK 21, so we must target JDK 21 (not the project's JDK 25)
val intellijJdkVersion = libs.versions.intellij.jdk.get().toInt()

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
            sinceBuild = "251" // IntelliJ 2025.1+
            untilBuild = provider { null } // No upper bound - compatible with future versions
        }

        changeNotes = """
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
                """.trimMargin()
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
val sandboxPluginTextMate = prepareSandbox.map { it.destinationDir.resolve("lib/textmate") }

val copyTextMateToSandbox by tasks.registering(Sync::class) {
    group = "build"
    description = "Copy TextMate grammar into plugin sandbox lib directory"

    from(textMateGrammar)
    into(sandboxPluginTextMate)
}

prepareSandbox.configure {
    finalizedBy(copyTextMateToSandbox)
}

val prepareJarSearchableOptions by tasks.existing {
    mustRunAfter(copyTextMateToSandbox)
}

// Ensure TextMate files are copied before IDE starts
// NOTE: finalizedBy doesn't guarantee completion, so we need explicit dependsOn
val runIde by tasks.existing {
    dependsOn(copyTextMateToSandbox)
}

// No tests in this module - disable test task to avoid IntelliJ plugin test infrastructure overhead
val test by tasks.existing {
    enabled = false
}
