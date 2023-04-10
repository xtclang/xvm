import org.gradle.api.Project

plugins {
    "kotlin-dsl"
//    kotlin("jvm") version "1.8.20" // Kotlin version to use
   // "java-gradle-plugin"
}

repositories {
    mavenCentral()
}

/*
gradlePlugin {
    plugins {
        create("test-plugin") {
            id = "org.xvm.testplugin"
            implementationClass = "org.xvm.XvmPlugin"
        }
    }
}
*/

/*

task("gitClean") {
    group = "other"
    description = "Runs git clean, recursively from the project root. Default is dry run."

    doLast {
        exec {
            val dryRun = getBooleanProperty("gitCleanDryRun", true)
            logger.lifecycle("Running gitClean task...")
            if (dryRun) {
                logger.warn("WARNING: gitClean is in dry run mode. To explicitly run gitClean, use '-PgitCleanDryRun=false'.")
            }
            commandLine("git", "clean", if (dryRun) "-nfxd" else "-fxd", "-e", ".idea")
        }
    }
}
*/

fun Project.tttt(name: String, defaultValue: Boolean = false): Boolean =
    if (project.hasProperty(name)) project.property(name).toString().toBoolean() else defaultValue
