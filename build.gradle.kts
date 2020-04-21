/*
 * Main build file for the XDK.
 */

plugins {
    java
}

tasks.withType<AbstractArchiveTask> {
    setProperty("archiveFileName", "xvm.jar")
}

tasks.withType(Jar::class) {
    manifest {
        attributes["Manifest-Version"] = "1.0"
        attributes["Sealed"] = "true"
        attributes["Main-Class"] = "org.xvm.tool.Launcher"
        attributes["Name"] = "/org/xvm/"
        attributes["Specification-Title"] = "xvm"
        attributes["Specification-Version"] = "0.1"
        attributes["Specification-Vendor"] = "xtclang.org"
        attributes["Implementation-Title"] = "xvm-prototype"
        attributes["Implementation-Version"] = "0.1"
        attributes["Implementation-Vendor"] = "xtclang.org"
    }
}

sourceSets {
    main {
        java {
            exclude("./src/main/java")
            srcDir("./src")
        }
        resources {
            exclude("./src/main/resources")
            srcDir("./resources")
        }
    }
    test {
        java {
            exclude("./src/test/java")
            // TODO srcDir("./tests")
        }
        resources {
            exclude("./src/test/resources")
            srcDir("./resources")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    // Use jcenter for resolving dependencies.
    // You can declare any Maven/Ivy/file repository here.
    jcenter()
}

dependencies {
    // dependencies exported to consumers (found on their compile classpath), e.g.:
    // api("org.apache.commons:commons-math3:3.6.1")

    // dependencies used internally (not exposed to consumers on their own compile classpath), e.g.:
    // implementation("com.google.guava:guava:28.2-jre")

    // Use JUnit test framework
    testImplementation("junit:junit:4.12")
}

tasks.register<Copy>("copyJar") {
    description = "Copy the prototype JAR after it is built."
    dependsOn("build")
    from(file("${buildDir}/lib/xvm.jar"))
    into(file("${buildDir}/xdk/prototype"))
}

