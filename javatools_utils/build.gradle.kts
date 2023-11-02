/*
 * Build file for the common Java utilities classes used by various Java projects in the XDK.
 */

plugins {
    id("org.xvm.build.java")
}

val semanticVersion: SemanticVersion by extra

val xtcJavaToolsUtilsProvider by configurations.registering {
    description = "Provider configuration of the XVM java_utils-$version artifacts (classes/jars)."
    isCanBeResolved = false
    isCanBeConsumed = true
    isVisible = false
}

val jar by tasks.existing(Jar::class) {
    manifest {
        attributes(
            "Manifest-Version" to "1.0",
            "Xdk-Version" to semanticVersion.toString(),
            "Sealed" to "true",
            "Name" to "/org/xvm/util",
            "Specification-Title" to "xvm",
            "Specification-Version" to "version",
            "Specification-Vendor" to  "xtclang.org",
            "Implementation-Title" to  "xvm-javatools-utils",
            "Implementation-Version" to version,
            "Implementation-Vendor" to "xtclang.org"
        )
    }
    doLast {
        logger.info("$prefix Finished building Java utilities: '${archiveFile.get().asFile.absolutePath}' as artifact.") // '$semanticVersion'.")
    }
}
