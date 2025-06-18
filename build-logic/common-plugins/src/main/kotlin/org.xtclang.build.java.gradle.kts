import XdkBuildLogic.Companion.DEFAULT_JAVA_BYTECODE_VERSION
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED

import java.nio.charset.StandardCharsets.UTF_8

plugins {
    id("org.xtclang.build.xdk.versioning")
    id("org.xtclang.build.checkstyle")
    java
}

private val pprefix = "org.xtclang.java"
private val lintProperty = "$pprefix.lint"

private val jdkVersion: Provider<Int> = provider {
    getXdkPropertyInt("$pprefix.jdk", DEFAULT_JAVA_BYTECODE_VERSION)
}

// Separate execution JVM version from compilation target
private val executionJdkVersion: Provider<Int> = provider {
    getXdkPropertyInt("$pprefix.execution.jdk", 24) // Use Java 24 for execution by default
}

java {
    toolchain {
        // Use compilation JDK (23) for the toolchain to avoid compatibility warnings
        val compilationJavaVersion = JavaLanguageVersion.of(jdkVersion.get())
        val buildProcessJavaVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion.toInt())
        if (!buildProcessJavaVersion.canCompileOrRun(compilationJavaVersion)) {
            throw buildException("Error in Java Toolchain config. The builder can't compile requested Java version: $compilationJavaVersion")
        }
        logger.info("$prefix Java Toolchain config; compilation JDK: $compilationJavaVersion, execution JDK: ${executionJdkVersion.get()}")
        languageVersion.set(compilationJavaVersion)
    }
}

testing {
    suites {
        @Suppress("UnstableApiUsage") val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    inputs.property("jdkVersion", jdkVersion);
    inputs.property("executionJdkVersion", executionJdkVersion);
    
    // Use execution JDK (24) for running, not compilation JDK (23)
    val execJavaVersion = JavaLanguageVersion.of(executionJdkVersion.get())
    val executionLauncher = javaToolchains.launcherFor {
        languageVersion.set(execJavaVersion)
    }
    
    logger.info("$prefix Configuring JavaExec task $name: compilation JDK ${jdkVersion.get()}, execution JDK ${executionJdkVersion.get()}")
    javaLauncher.set(executionLauncher)
    
    if (enablePreview()) {
        jvmArgs("--enable-preview")
    }
    doLast {
        logger.info("$prefix JVM arguments: $jvmArgs")
    }
}

val checkWarnings by tasks.registering {
    if (!getXdkPropertyBoolean(lintProperty, false)) {
        val lintPropertyHasValue = isXdkPropertySet(lintProperty)
        logger.info("$prefix Java warnings are ${if (lintPropertyHasValue) "explicitly" else ""} disabled for project.")
    }
}

val assemble by tasks.existing {
    dependsOn(checkWarnings)
}

tasks.withType<JavaCompile>().configureEach {
    // TODO: These xdk properties may have to be declared as inputs.
    inputs.property("jdkVersion", jdkVersion)
    inputs.property("executionJdkVersion", executionJdkVersion)
    
    val lint = getXdkPropertyBoolean(lintProperty, false)
    val maxErrors = getXdkPropertyInt("$pprefix.maxErrors", 0)
    val maxWarnings = getXdkPropertyInt("$pprefix.maxWarnings", 0)
    val warningsAsErrors = getXdkPropertyBoolean("$pprefix.warningsAsErrors", true)
    if (!warningsAsErrors) {
        logger.warn("$prefix WARNING: Task '$name' XTC Java convention warnings are not treated as errors, which is best practice (Enable -Werror).")
    }

    val args = buildList {
        // Configure linting - allow internal API warnings since they're unavoidable in some cases
        if (lint) {
            add("-Xlint:all")     // Enable all linting
            // Note: Some internal API warnings (like ExtendedOpenOption) are unavoidable
        } else {
            add("-Xlint:none")
        }

        logger.info("$prefix Configuring JavaCompile task $name: compilation JDK ${jdkVersion.get()}, execution JDK ${executionJdkVersion.get()}")
        
        if (enablePreview()) {
            add("--enable-preview")
            if (lint) {
                add("-Xlint:preview")
            }
        }

        if (maxErrors > 0) {
            add("-Xmaxerrs")
            add("$maxErrors")
        }

        if (maxWarnings > 0) {
            add("-Xmaxwarns")
            add("$maxWarnings")
        }

        // Only treat warnings as errors if we're not dealing with unavoidable internal API warnings
        if (warningsAsErrors) {
            add("-Werror")
        }
    }

    with(options) {
        compilerArgs.addAll(args)
        isDeprecation = lint
        isWarnings = lint
        encoding = UTF_8.toString()
        //isFork = false
    }
}

tasks.withType<Test>().configureEach {
    // Use execution JDK (24) for running tests, not compilation JDK (23)
    val execJavaVersion = JavaLanguageVersion.of(executionJdkVersion.get())
    val testLauncher = javaToolchains.launcherFor {
        languageVersion.set(execJavaVersion)
    }
    javaLauncher.set(testLauncher)
    
    if (enablePreview()) {
        jvmArgs("--enable-preview")
    }
    testLogging {
        showStandardStreams = getXdkPropertyBoolean("$pprefix.test.stdout")
        if (showStandardStreams) {
            events(STANDARD_OUT, STANDARD_ERROR, SKIPPED, STARTED, PASSED, FAILED)
        }
    }
}

private fun enablePreview(): Boolean {
    val enablePreview = getXdkPropertyBoolean("$pprefix.enablePreview")
    if (enablePreview) {
        logger.warn("$prefix WARNING: Project has Java preview features enabled.")
    }
    return enablePreview
}
