/*
 * Build file for the common Java utilities classes used by various Java projects in the XDK.
 */

plugins {
    java
}

tasks.withType(Jar::class) {
    manifest {
        attributes["Manifest-Version"] = "1.0"
        attributes["Sealed"] = "true"
        attributes["Name"] = "/org/xvm/util"
        attributes["Specification-Title"] = "xvm"
        attributes["Specification-Version"] = "0.3.0"
        attributes["Specification-Vendor"] = "xtclang.org"
        attributes["Implementation-Title"] = "xvm-javatools_utils"
        attributes["Implementation-Version"] = "0.3.0"
        attributes["Implementation-Vendor"] = "xtclang.org"
    }
}

java {
    // Java 17 is the latest "Long Term Support" (LTS) release, as of late 2021
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
    useJUnit();
    maxHeapSize = "1G"
}

dependencies {
    // Use JUnit test framework
    testImplementation("junit:junit:4.12")
}
