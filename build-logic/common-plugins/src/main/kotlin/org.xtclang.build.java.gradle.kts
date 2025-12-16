import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.process.CommandLineArgumentProvider
import java.nio.charset.StandardCharsets.UTF_8

plugins {
    id("org.xtclang.build.xdk.properties")
    java
}

private class DefaultJvmArgsProvider(
    private val args: Provider<List<String>>
) : CommandLineArgumentProvider {
    @get:Input
    val snapshot: List<String> get() = args.get()
    override fun asArguments(): Iterable<String> = snapshot
}

/**
 * Lint modes for Java compilation:
 * - "all": Enable all lint warnings (-Xlint:all) - most extensive
 * - "default": Show warnings javac hints about by default (-Xlint:unchecked)
 * - "none": Disable all lint warnings (-Xlint:none)
 */
private class JavaCompilerArgsProvider(
    private val lintModeProv: Provider<String>,
    private val enablePreviewProv: Provider<Boolean>,
    private val maxErrorsProv: Provider<Int>,
    private val maxWarningsProv: Provider<Int>,
    private val warningsAsErrorsProv: Provider<Boolean>,
) : CommandLineArgumentProvider {

    @get:Input
    val lintModeSnapshot: String get() = lintModeProv.get()

    @get:Input
    val previewSnapshot: Boolean get() = enablePreviewProv.get()

    @get:Input
    val maxErrorsSnapshot: Int get() = maxErrorsProv.get()

    @get:Input
    val maxWarningsSnapshot: Int get() = maxWarningsProv.get()

    @get:Input
    val werrorSnapshot: Boolean get() = warningsAsErrorsProv.get()

    private fun isLintAll(): Boolean = lintModeSnapshot.equals("all", ignoreCase = true)

    private fun isLintNone(): Boolean = lintModeSnapshot.equals("none", ignoreCase = true)

    private fun isLintDefault(): Boolean = lintModeSnapshot.equals("default", ignoreCase = true)

    override fun asArguments(): Iterable<String> = buildList {
        when {
            isLintAll() -> add("-Xlint:all")
            isLintNone() -> add("-Xlint:none")
            isLintDefault() -> add("-Xlint:unchecked") // What javac suggests when it shows "Note: Recompile with -Xlint:unchecked"
            else -> error("Unknown lint mode: '$lintModeSnapshot'. Valid values: all, default, none")
        }
        if (previewSnapshot) {
            add("--enable-preview")
            if (isLintAll() || isLintDefault()) add("-Xlint:preview")
        }
        if (maxErrorsSnapshot > 0) addAll(listOf("-Xmaxerrs", maxErrorsSnapshot.toString()))
        if (maxWarningsSnapshot > 0) addAll(listOf("-Xmaxwarns", maxWarningsSnapshot.toString()))
        if (werrorSnapshot) add("-Werror")
    }
}

/** Top-level typed Action for Test logging (no script capture). */
private class ConfigureTestLoggingAction(
    private val showStdout: Provider<Boolean>
) : Action<Test> {
    override fun execute(t: Test) {
        val on = showStdout.get()
        t.testLogging.showStandardStreams = on
        if (on) {
            t.testLogging.events(
                TestLogEvent.STANDARD_OUT,
                TestLogEvent.STANDARD_ERROR,
                TestLogEvent.SKIPPED,
                TestLogEvent.STARTED,
                TestLogEvent.PASSED,
                TestLogEvent.FAILED
            )
        }
    }
}

/* ── Properties (Providers) ───────────────────────────────────────────────── */

val pprefix = "org.xtclang.java"

val jdkVersion         = xdkProperties.int("$pprefix.jdk")
val enablePreview      = xdkProperties.boolean("$pprefix.enablePreview", false)
val enableNativeAccess = xdkProperties.boolean("$pprefix.enableNativeAccess", false)
// Lint mode: "all" for extensive, "default" for javac defaults, "none" to disable
val lintMode           = xdkProperties.string("$pprefix.lint", "none")
val maxErrors          = xdkProperties.int("$pprefix.maxErrors", 0)
val maxWarnings        = xdkProperties.int("$pprefix.maxWarnings", 0)
val warningsAsErrors   = xdkProperties.boolean("$pprefix.warningsAsErrors", true)
val showTestStdout     = xdkProperties.boolean("$pprefix.test.stdout", false)

/* JVM args composed lazily */
val defaultJvmArgs: Provider<List<String>> =
    enablePreview.zip(enableNativeAccess) { preview, native ->
        buildList {
            add("-ea")
            if (preview) add("--enable-preview")
            if (native) add("--enable-native-access=ALL-UNNAMED")
        }
    }

// Expose defaultJvmArgs as a typed project property for other build scripts to use
project.extensions.add(typeOf<Provider<List<String>>>(), "defaultJvmArgs", defaultJvmArgs)

/* ── Java toolchain ───────────────────────────────────────────────────────── */

java {
    toolchain.languageVersion.set(jdkVersion.map { JavaLanguageVersion.of(it) })
}

/* ── Testing with the consumer’s version catalog (no hard-coded versions) ─── */

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val junitBom = libsCatalog.findLibrary("junit.bom")
val junitJupiter = libsCatalog.findLibrary("junit.jupiter")

testing {
    suites {
        @Suppress("UnstableApiUsage", "unused")
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                // Resolving catalog entries at configuration time is fine (static coords).
                implementation(platform(junitBom.get()))
                implementation(junitJupiter.get())
            }
        }
    }
}

/* ── Tasks (lazy + CC-safe) ───────────────────────────────────────────────── */

tasks.withType<JavaExec>().configureEach {
    inputs.property("jdkVersion", jdkVersion)
    inputs.property("defaultJvmArgs", defaultJvmArgs)
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    jvmArgumentProviders.add(DefaultJvmArgsProvider(defaultJvmArgs))
}

tasks.withType<JavaCompile>().configureEach {
    inputs.property("jdkVersion", jdkVersion)
    inputs.property("enablePreview", enablePreview)   // javac cares about preview, not just java
    inputs.property("lintMode", lintMode)
    inputs.property("maxErrors", maxErrors)
    inputs.property("maxWarnings", maxWarnings)
    inputs.property("warningsAsErrors", warningsAsErrors)

    // target bytecode = toolchain language level
    options.release.set(jdkVersion)

    // all compile flags via provider-backed arg provider
    options.compilerArgumentProviders.add(
        JavaCompilerArgsProvider(
            lintMode,
            enablePreview,
            maxErrors,
            maxWarnings,
            warningsAsErrors
        )
    )

    // non-provider knobs at execution time
    doFirst {
        options.encoding = UTF_8.toString()
    }
}

// Test: JVM args provider + typed Action for logging (no doFirst lambda)
tasks.withType<Test>().configureEach(ConfigureTestLoggingAction(showTestStdout))
tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(DefaultJvmArgsProvider(defaultJvmArgs))
    inputs.property("defaultJvmArgs", defaultJvmArgs)
    inputs.property("showTestStdout", showTestStdout)
}
