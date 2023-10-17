/*
 * Build file for the common Java utilities classes used by various Java projects in the XDK.
 */

plugins {
    id("org.xvm.build.java")
}

val xtcJavaToolsUtilsProvider by configurations.registering {
    description = "Provider configuration of the XVM java_utils-$version artifacts (classes/jars)."
    isCanBeResolved = false
    isCanBeConsumed = true
    isVisible = false
}

tasks.jar {
    manifest {
        attributes["Manifest-Version"] = "1.0"
        attributes["Sealed"] = "true"
        attributes["Name"] = "/org/xvm/util"
        attributes["Specification-Title"] = "xvm"
        attributes["Specification-Version"] = version
        attributes["Specification-Vendor"] = "xtclang.org"
        attributes["Implementation-Title"] = "xvm-javatools-utils"
        attributes["Implementation-Version"] = version
        attributes["Implementation-Vendor"] = "xtclang.org"
    }
    doLast {
        logger.lifecycle("$prefix Finished building Java utilities: '${archiveFile.get().asFile.absolutePath}' as artifact.") // '$semanticVersion'.")
    }
}
