/*
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    java
}

sourceSets {
    create("implicits") {
        resources {
            srcDir {
                "${project(":ecstasy").buildDir}/resources/"
            }
        }
    }
//    sourceSets.create("integrationTest") {
//        java.srcDir("src/integrationTest/java")
//        java.srcDir("build/generated/source/apt/integrationTest")
//        resources.srcDir("src/integrationTest/resources")
//    }
//    sourceSets.getByName("main") {
//        java.srcDir("src/main/java")
//        java.srcDir("src/main/kotlin")
//    }
}

//def implicit = copySpec {
//    from("${project(":ecstasy").buildDir}/resources/") {
//        include "**/*.x"
//    }
//}

tasks.withType(Jar::class) {
    manifest {
        attributes["Manifest-Version"] = "1.0"
        attributes["Sealed"] = "true"
        attributes["Main-Class"] = "org.xvm.tool.Launcher"
        attributes["Name"] = "/org/xvm/"
        attributes["Specification-Title"] = "xvm"
        attributes["Specification-Version"] = "0.1.0"
        attributes["Specification-Vendor"] = "xtclang.org"
        attributes["Implementation-Title"] = "xvm-prototype"
        attributes["Implementation-Version"] = "0.1.0"
        attributes["Implementation-Vendor"] = "xtclang.org"
    }
    with(implicit)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation("org.xtclang.xvm:utils:")

    // Use JUnit test framework
    testImplementation("junit:junit:4.12")
}
