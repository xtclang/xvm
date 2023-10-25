import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED

import java.nio.charset.StandardCharsets.UTF_8

plugins {
    id("org.xvm.build.version")
    java
}

java {
    toolchain {
        val xdkJavaVersion = JavaLanguageVersion.of(getXdkProperty("java.jdk", "17").toInt())
        val buildProcessJavaVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion.toInt())
        if (!buildProcessJavaVersion.canCompileOrRun(xdkJavaVersion)) {
            throw buildException("Error in Java toolchain config. The builder can't compile requested Java version: $xdkJavaVersion")
        }
        logger.lifecycle("$prefix Java Toolchain config; binary format version: 'JDK $xdkJavaVersion' (build process version: 'JDK $buildProcessJavaVersion')")
        languageVersion.set(xdkJavaVersion)
    }
}

private val enablePreview : Boolean = getXdkProperty("java.enablePreview", "false").toBoolean()
if (enablePreview) {
    logger.warn("$prefix WARNING; project has Java preview features enabled.")
}

// Note: make sure to use configureEach for any task collection modified through withType.
// Otherwise, the task collection will always be configured, even if they aren't used in a particular build.
tasks.withType<JavaExec>().configureEach {
    logger.info("$prefix Configuring JavaExec task $name from toolchain (Java version: ${java.toolchain.languageVersion})")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain)) // Or the plugin breaks
    if (enablePreview) {
        jvmArgs("--enable-preview")
    }
    doLast {
        logger.info("$prefix JVM arguments: $jvmArgs")
    }
}

tasks.withType<JavaCompile>().configureEach {
    val lint = getXdkProperty("java.lint", "false").toBoolean()
    val maxErrors = getXdkProperty("java.maxErrors", "0").toInt()
    val maxWarnings = getXdkProperty("java.maxWarnings", "0").toInt()
    val warningsAsErrors = getXdkProperty("java.warningsAsErrors", "false").toBoolean()

    if (!warningsAsErrors) {
        logger.warn("$prefix WARNING: Task '$name' XTC Java convention warnings are not treated as errors, which is best-practice (Enable -Werror).")
    }

    val args = buildList {
        if (lint) {
            add("-Xlint:unchecked")
            add("-Xlint:deprecation")
        }
        if (enablePreview) {
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
        if (warningsAsErrors) {
            add("-Werror")
        }
    }

    options.compilerArgs.addAll(args)
    options.isDeprecation = lint
    options.encoding = UTF_8.toString()
    doLast {
        logger.info("$prefix Task '$name' configured (JavaCompile): [isDeprecation=${options.isDeprecation}, encoding=${options.encoding}, arguments=${options.compilerArgs}]")
    }
}

// TODO add more linting, integrate lint fix/checkstyle/spotless.
tasks.withType<Test>().configureEach {
    if (enablePreview) {
        jvmArgs("--enable-preview")
    }
    maxHeapSize = "4G" // TODO make this configurable in the properties files along with the other Java properties.
    testLogging {
        showStandardStreams = getXdkProperty("java.test.stdout", "false").toBoolean()
        if (showStandardStreams) {
            events(STANDARD_OUT, STANDARD_ERROR, SKIPPED, STARTED, PASSED, FAILED)
        }
    }
    doLast {
        logger.info("$prefix Task '$name' configured (Test).")
    }
}

testing {
    suites {
        @Suppress("UnstableApiUsage") val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}
