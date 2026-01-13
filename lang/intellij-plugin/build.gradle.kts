import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

// Access version from xdkProperties (set by xdk.build.properties plugin)
val xdkVersion: String = project.version.toString()
val releaseChannel: String = xdkProperties.stringValue("xdk.intellij.release.channel", "alpha")

// Publishing is disabled by default. Enable with: ./gradlew publishPlugin -PenablePublish=true
val enablePublish = project.findProperty("enablePublish")?.toString()?.toBoolean() ?: false

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

sourceSets.main {
    java.srcDir(syncedJavaSourceDir)
}

tasks.named("compileJava") {
    dependsOn(syncXtcProjectCreator)
}

tasks.named("compileKotlin") {
    dependsOn(syncXtcProjectCreator)
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
    implementation(kotlin("stdlib"))

    // LSP server for in-process execution (no subprocess needed)
    implementation(project(":lsp-server"))

    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.gradle")
        // TextMate plugin for syntax highlighting (bundled in IntelliJ)
        bundledPlugin("org.jetbrains.plugins.textmate")
        // LSP4IJ provides LSP support for Community Edition
        plugin("com.redhat.devtools.lsp4ij:0.11.0")
        pluginVerifier()
    }

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // JUnit 4 needed by IntelliJ test framework at runtime
    testRuntimeOnly("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")

    // Depend on sibling project artifacts via configurations
    textMateGrammar(project(path = ":", configuration = "textMateElements"))
}

// IntelliJ 2025.1 runs on JDK 21, so we must target JDK 21 (not the project's JDK 24)
val intellijJdkVersion = 21

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

val sandboxPluginTextMate = layout.buildDirectory.dir("idea-sandbox/IC-2025.1/plugins/${project.name}/lib/textmate")

val copyTextMateToSandbox by tasks.registering(Sync::class) {
    group = "build"
    description = "Copy TextMate grammar into plugin sandbox lib directory"

    from(textMateGrammar)
    into(sandboxPluginTextMate)
}

// Get typed reference to IntelliJ Platform tasks
val prepareSandbox by tasks.existing(Sync::class) {
    finalizedBy(copyTextMateToSandbox)
}

val prepareJarSearchableOptions by tasks.existing {
    mustRunAfter(copyTextMateToSandbox)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
