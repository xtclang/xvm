plugins {
    checkstyle
}

checkstyle {
    val checkstyleConfigDir = compositeRootProjectDirectory.dir("gradle/checkstyle")
    val checkstyleConfig = checkstyleConfigDir.file("checkstyle.xml").asFile
    if (!checkstyleConfig.exists()) {
        throw buildException("$prefix Checkstyle configuration file not found: $checkstyleConfig")
    }
    configDirectory = checkstyleConfigDir
}

/**
 * Default checkstyle behavior for all tasks is to enabled checkstyle using the policy
 * file in <root>/gradle/checkstyle/checkstyle.xml, and to fail on error.
 *
 * While working on conforming to the code standard, checkstyle can be disabled or
 * run with warnings instead of errors when violations are detected.
 *
 * This is done by setting false as the value for these properties:
 *
 *    org.xtclang.build.checkstyle              (default: true)
 *    org.xtclang.build.checkstyle.failOnError  (default: true)
 *
 * Just like all other XDK/Gradle properties, they can be overridden at the
 * project level, by placing a properties file closed to the relevant project
 * than the root.
 */
tasks.withType(Checkstyle::class) {
    val enabled = getXdkPropertyBoolean("org.xtclang.build.checkstyle", true)
    val failOnError = getXdkPropertyBoolean("org.xtclang.build.checkstyle.failOnError", true)

    if (!failOnError && enabled) {
        logger.warn("$prefix WARNING: Checkstyle is enabled, but not set to fail on error: '${project.name}'.")
        @Suppress("UnstableApiUsage")
        ignoreFailures = true
        // If we have thousands of errors, like at the outset of checkstyle, we need to limit the parser,
        // or checkstyle will simply run out of heap memory with default settings.
        maxErrors = 100
        maxWarnings = 100
    }

    onlyIf {
        if (!enabled) {
            logger.info("$prefix WARNING: Checkstyle is disabled for: '${project.name}'.")
        }
        enabled
    }
}
