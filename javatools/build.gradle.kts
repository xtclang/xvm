/*
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    java
}

tasks.register<Copy>("copyImplicits") {
    group       = "Build"
    description = "Copy the implicit.x from :ecstasy project into the build directory."
    from(file(project(":ecstasy").property("implicit.x")!!))
    into(file("$buildDir/resources/main/"))
    doLast {
        println("Finished task: copyImplicits")
    }
}

tasks.register<Copy>("copyUtils") {
    group       = "Build"
    description = "Copy the classes from :utils project into the build directory."
    dependsOn(project(":utils").tasks["classes"])
    from(file("${project(":utils").buildDir}/classes/java/main"))
    include("**/*.class")
    into(file("$buildDir/classes/java/main"))
    doLast {
        println("Finished task: copyUtils")
    }
}

tasks.register<Copy>("copyDeps") {
    group       = "Build"
    description = "Copy the module dependencies into the build directory."
    from(configurations.default)
    into("$buildDir/deps")
    doLast {
        println("Finished task: copyDeps")
    }
}

tasks.jar {
    val copyImplicits = tasks["copyImplicits"]
    val copyUtils     = tasks["copyUtils"]
    val copyDeps      = tasks["copyDeps"]

    dependsOn(copyImplicits)
    dependsOn(copyUtils)
    dependsOn(copyDeps)

    mustRunAfter(copyImplicits)
    mustRunAfter(copyUtils)
    mustRunAfter(copyDeps)

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
}

tasks.compileTestJava {
    dependsOn(tasks["copyImplicits"])
    dependsOn(tasks["copyUtils"])
}

tasks.test {
    useJUnit();
    maxHeapSize = "1G"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.xtclang.xvm:utils:")
    implementation("io.netty:netty-all:4.1.71.Final")

    // Use JUnit test framework
    testImplementation("junit:junit:4.12")
}
