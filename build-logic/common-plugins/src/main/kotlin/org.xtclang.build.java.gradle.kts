import org.gradle.api.Task
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
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

private class JavaCompilerArgsProvider(
    private val lintProv: Provider<Boolean>,
    private val enablePreviewProv: Provider<Boolean>,
    private val maxErrorsProv: Provider<Int>,
    private val maxWarningsProv: Provider<Int>,
    private val warningsAsErrorsProv: Provider<Boolean>,
) : CommandLineArgumentProvider {

    @get:Input
    val lintSnapshot: Boolean get() = lintProv.get()

    @get:Input
    val previewSnapshot: Boolean get() = enablePreviewProv.get()

    @get:Input
    val maxErrorsSnapshot: Int get() = maxErrorsProv.get()

    @get:Input
    val maxWarningsSnapshot: Int get() = maxWarningsProv.get()

    @get:Input
    val werrorSnapshot: Boolean get() = warningsAsErrorsProv.get()

    override fun asArguments(): Iterable<String> = buildList {
        add("-Xlint:${if (lintSnapshot) "all" else "none"}")
        // javac lint flags are cumulative, so these re-enable exactly two categories on top of the
        // default "none": constructor this-escape and switch fallthrough. Combined with the default
        // -Werror below, an unsuppressed hit in either category is a build error everywhere this
        // convention applies. Deliberate exceptions require a local @SuppressWarnings with a
        // comment proving why the escape/fallthrough is safe; see
        // docs/reentrancy/must-audit-backlog.md (build-gate and fallthrough rows).
        //
        // rawtypes reached zero on 2026-09-03 and is now fatal. The estimate that used to sit
        // here was "~40 sites remain"; the real count was 54, cleared as: omitted diamonds, a
        // record replacing Parser's raw List[] pair (its own comment asked for one), wildcards
        // where nothing mutates through the pattern, and Utils.any() replacing a raw ANY constant.
        // Four sites keep a documented suppression because Java cannot express them: the two
        // NativeTemplateRef keys whose class literal is raw in its own type argument, and the two
        // Entry[] creations in TypeInfoReal, since generic array creation is illegal.
        add("-Xlint:rawtypes")
        add("-Xlint:this-escape")
        add("-Xlint:fallthrough")
        // Categories below are at ZERO across the Java sources today, verified by compiling with
        // -Xlint:all and tallying: only unchecked, rawtypes, dangling-doc-comments, serial, cast
        // and classfile produce anything. Enabling a clean category costs nothing now and, with
        // -Werror, makes reintroducing one a build failure rather than a warning nobody reads.
        //
        // Deliberately NOT enabled: path, options and processing depend on the invoking
        // environment rather than on the code, so they can fail a build for reasons a contributor
        // cannot see in the source. The six dirty categories above stay off until their counts
        // reach zero; rawtypes is tracked in the enhancement list.
        // Added 2026-08-31, verified at zero with -Xlint:all. These three are not merely clean,
        // they are the ones that matter for this codebase: lossy-conversions catches an implicit
        // narrowing in a compound assignment (the interpreter does long/int arithmetic constantly,
        // and `intVar += longExpr` silently truncates); synchronization catches locking on a
        // value-based class, which a concurrent runtime must never do; output-file-clash catches
        // two outputs racing for one path. The module-system categories (module, exports, opens,
        // requires-*) are also clean but are no-ops without a module-info, so they are left out
        // rather than added as decoration.
        // serial reached zero on 2026-08-31: eight classes that are Serializable only by accident
        // of extending Exception/ArrayList gained a serialVersionUID, and the one real finding -
        // Decimal.RangeException holding a non-serializable Decimal - is documented in place.
        // Nothing in this tree uses Java serialization; making this fatal keeps it that way.
        add("-Xlint:serial")
        add("-Xlint:lossy-conversions")
        add("-Xlint:synchronization")
        add("-Xlint:output-file-clash")
        add("-Xlint:deprecation")
        add("-Xlint:removal")
        add("-Xlint:dep-ann")
        add("-Xlint:divzero")
        add("-Xlint:empty")
        add("-Xlint:finally")
        add("-Xlint:overloads")
        add("-Xlint:overrides")
        add("-Xlint:static")
        add("-Xlint:strictfp")
        add("-Xlint:text-blocks")
        add("-Xlint:try")
        add("-Xlint:varargs")
        add("-Xlint:auxiliaryclass")
        add("-Xlint:identity")
        if (previewSnapshot) {
            add("--enable-preview")
            if (lintSnapshot) add("-Xlint:preview")
        }
        if (maxErrorsSnapshot > 0) addAll(listOf("-Xmaxerrs", maxErrorsSnapshot.toString()))
        if (maxWarningsSnapshot > 0) addAll(listOf("-Xmaxwarns", maxWarningsSnapshot.toString()))
        if (werrorSnapshot) add("-Werror")
    }
}

/** Top-level typed Action for Test logging (no script capture). */
private class ConfigureTestLoggingAction(
    private val showStdout: Provider<Boolean>,
    private val failFastTests: Provider<Boolean>
) : Action<Test> {
    override fun execute(t: Test) {
        val on = showStdout.get()
        t.failFast = failFastTests.get()
        t.testLogging.events(
            TestLogEvent.FAILED
        )
        t.testLogging.exceptionFormat = TestExceptionFormat.SHORT
        t.testLogging.showExceptions = true
        t.testLogging.showCauses = true
        t.testLogging.showStackTraces = false
        t.testLogging.showStandardStreams = on
        if (on) {
            t.testLogging.events(
                TestLogEvent.STANDARD_OUT,
                TestLogEvent.STANDARD_ERROR,
                TestLogEvent.FAILED
            )
        }
    }
}

/** Spec to skip tests when skipAllTests property is set (configuration cache safe). */
private class SkipAllTestsSpec(
    private val skipAllTests: Boolean
) : Spec<Task> {
    override fun isSatisfiedBy(task: Task): Boolean = !skipAllTests
}

/* ── Properties (Providers) ───────────────────────────────────────────────── */

val pprefix = "org.xtclang.java"

val jdkVersion         = xdkProperties.int("$pprefix.jdk")
val enablePreview      = xdkProperties.boolean("$pprefix.enablePreview", false)
val enableNativeAccess = xdkProperties.boolean("$pprefix.enableNativeAccess", false)
val lint               = xdkProperties.boolean("$pprefix.lint", false)
val maxErrors          = xdkProperties.int("$pprefix.maxErrors", 0)
val maxWarnings        = xdkProperties.int("$pprefix.maxWarnings", 0)
val warningsAsErrors   = xdkProperties.boolean("$pprefix.warningsAsErrors", true)
val showTestStdout     = xdkProperties.boolean("$pprefix.test.stdout", false)
val failFastTests      = xdkProperties.boolean("$pprefix.test.failFast", false)

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
// Toolchain is configured by the org.xtclang.build.xdk.properties plugin
// (applied transitively above) so every project that touches Java OR Kotlin
// gets the same JDK 25 toolchain from a single source of truth. Nothing to do
// here.

/* ── Testing with the consumer’s version catalog (no hard-coded versions) ─── */

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val junitBom = libsCatalog.findLibrary("junit.bom")
val junitJupiter = libsCatalog.findLibrary("junit.jupiter")

testing {
    suites {
        @Suppress("UnstableApiUsage")
        getByName<JvmTestSuite>("test") {
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
    inputs.property("lint", lint)
    inputs.property("maxErrors", maxErrors)
    inputs.property("maxWarnings", maxWarnings)
    inputs.property("warningsAsErrors", warningsAsErrors)

    // target bytecode = toolchain language level
    options.release.set(jdkVersion)

    // all compile flags via provider-backed arg provider
    options.compilerArgumentProviders.add(
        JavaCompilerArgsProvider(
            lint,
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
tasks.withType<Test>().configureEach(ConfigureTestLoggingAction(showTestStdout, failFastTests))
tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(DefaultJvmArgsProvider(defaultJvmArgs))
    inputs.property("defaultJvmArgs", defaultJvmArgs)
    inputs.property("showTestStdout", showTestStdout)
    inputs.property("failFastTests", failFastTests)
    // Forward -Dxvm.* from the Gradle invocation into the forked test JVM, so opt-in diagnostics
    // (e.g. -Dxvm.typeinfo.trace) can be switched on for one run without editing the build.
    systemProperties(providers.systemPropertiesPrefixedBy("xvm.").get())
    // Skip all tests when -PskipAllTests is set (configuration cache safe)
    onlyIf(SkipAllTestsSpec(project.hasProperty("skipAllTests")))
}
