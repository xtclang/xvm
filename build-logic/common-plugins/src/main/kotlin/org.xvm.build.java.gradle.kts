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
        val xdkJavaVersion = JavaLanguageVersion.of(xdkPropertyOrgXvm("java.jdk", "17").toInt())
        val buildProcessJavaVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion.toInt())
        if (!buildProcessJavaVersion.canCompileOrRun(xdkJavaVersion)) {
            throw buildException("Error in Java toolchain config. The builder can't compile requested Java version: $xdkJavaVersion")
        }
        logger.lifecycle("$prefix Java Toolchain config; binary format version: 'JDK $xdkJavaVersion' (build process version: 'JDK $buildProcessJavaVersion')")
        languageVersion.set(xdkJavaVersion)
    }
}

tasks {
    val enablePreview : Boolean = xdkPropertyOrgXvm("java.enablePreview", "false").toBoolean()
    if (enablePreview) {
        logger.warn("$prefix WARNING; project has Java preview features enabled.")
    }

    withType<JavaExec> {
        logger.info("$prefix Configuring JavaExec task $name from toolchain (Java version: ${java.toolchain.languageVersion})")
        javaLauncher.set(javaToolchains.launcherFor(java.toolchain)) // Or the plugin breaks
        if (enablePreview) {
            jvmArgs("--enable-preview")
        }
        doLast {
            logger.info("$prefix JVM arguments: $jvmArgs")
        }
    }

    withType<JavaCompile> {
        val lint = xdkPropertyOrgXvm("java.lint", "false").toBoolean()
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

            val maxErrors = xdkPropertyOrgXvm("java.maxErrors", "0").toInt()
            val maxWarnings = xdkPropertyOrgXvm("java.maxWarnings", "0").toInt()
            val warningsAsErrors = xdkPropertyOrgXvm("java.warningAsErrors", "false").toBoolean()
            if (!warningsAsErrors) {
                logger.warn("$prefix WARNING: Task '$name' XTC Java convention warnings are not treated as errors, which is best-practice (Enable -Werror).")
            }
            if (maxErrors > 0) {
                add("-Xmaxerrs")
                add("$maxErrors")
            }
            if (maxWarnings > 0) {
                add("-Xmaxwarns")
                add("$maxWarnings")
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
    withType<Test> {
        if (enablePreview) {
            jvmArgs("--enable-preview")
        }
        maxHeapSize = "4G" // TODO make this configurable in the properties files along with the other Java properties.
        testLogging {
            showStandardStreams = xdkPropertyOrgXvm("java.test.stdout", "false").toBoolean()
            if (showStandardStreams) {
                events(STANDARD_OUT, STANDARD_ERROR, SKIPPED, STARTED, PASSED, FAILED)
            }
        }
        doLast {
            logger.info("$prefix Task '$name' configured (Test).")
        }
    }
}

testing {
    suites {
        @Suppress("UnstableApiUsage") val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}
