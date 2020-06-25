/*
 * Build file for the Java tools portion of the XDK.
 */

plugins {
    java
}

val copyImplicits = tasks.register<Copy>("copyImplicits") {
    from(file("${project(":ecstasy").projectDir}/src/main/resources/implicit.x"))
    into(file("$buildDir/resources/main/"))
}

tasks.withType(Jar::class) {
    dependsOn(copyImplicits)
    mustRunAfter(copyImplicits)
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

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation("org.xtclang.xvm:utils:")

    // Use JUnit test framework
    testImplementation("junit:junit:4.12")
}
