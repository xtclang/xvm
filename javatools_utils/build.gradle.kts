/*
 * Build file for the common Java utilities classes used by various Java projects in the XDK.
 */

plugins {
    java
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
    jar {
        val version = rootProject.version

        manifest {
            attributes["Manifest-Version"] = "1.0"
            attributes["Sealed"] = "true"
            attributes["Name"] = "/org/xvm/util"
            attributes["Specification-Title"] = "xvm"
            attributes["Specification-Version"] = version
            attributes["Specification-Vendor"] = "xtclang.org"
            attributes["Implementation-Title"] = "xvm-javatools_utils"
            attributes["Implementation-Version"] = version
            attributes["Implementation-Vendor"] = "xtclang.org"
        }
    }

    test {
        maxHeapSize = "1G"
    }
}
