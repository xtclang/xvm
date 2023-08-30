import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP

/*
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    java
}

dependencies {
    implementation("org.xtclang.xvm:javatools_utils:")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

tasks {
    val copyImplicits by registering(Copy::class) {
        group = BUILD_GROUP
        description = "Copy the implicit.x from :lib_ecstasy project into the build directory."
        from(file(project(":lib_ecstasy").property("implicit.x")!!))
        into(file("$buildDir/resources/main/"))
        doLast {
            logger.info("Finished task: copyImplicits")
        }
    }

    val copyUtils by registering(Copy::class) {
        group = BUILD_GROUP
        description = "Copy the classes from :javatools_utils project into the build directory."
        dependsOn(project(":javatools_utils").tasks["classes"])
        from(file("${project(":javatools_utils").buildDir}/classes/java/main"))
        include("**/*.class")
        into(file("$buildDir/classes/java/main"))
        doLast {
            logger.info("Finished task: copyUtils")
        }
    }

    jar {
        dependsOn(copyImplicits, copyUtils)
        mustRunAfter(copyImplicits, copyUtils)

        val version = rootProject.version
        manifest {
            attributes["Manifest-Version"] = "1.0"
            attributes["Sealed"] = "true"
            attributes["Main-Class"] = "org.xvm.tool.Launcher"
            attributes["Name"] = "/org/xvm/"
            attributes["Specification-Title"] = "xvm"
            attributes["Specification-Version"] = version
            attributes["Specification-Vendor"] = "xtclang.org"
            attributes["Implementation-Title"] = "xvm-prototype"
            attributes["Implementation-Version"] = version
            attributes["Implementation-Vendor"] = "xtclang.org"
        }
    }

    compileTestJava {
        dependsOn(copyImplicits, copyUtils)
    }

    test {
        maxHeapSize = "1G"
    }
}
