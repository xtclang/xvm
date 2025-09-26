plugins {
    java
}

repositories {
    // New Central Portal snapshots repository
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
    // Maven Central releases
    mavenCentral()
}

val xdkVersion = project.findProperty("xdkVersion")?.toString() ?: "0.4.4-SNAPSHOT"

dependencies {
    implementation("org.xtclang:xdk:$xdkVersion")
}

tasks.register("verifyXdkAvailability") {
    doLast {
        println("Testing XDK version: $xdkVersion")

        val config = configurations.getByName("compileClasspath")
        val resolved = config.resolvedConfiguration

        if (resolved.hasError()) {
            println("❌ Failed to resolve XDK $xdkVersion")
            resolved.rethrowFailure()
        } else {
            val xdkArtifacts = resolved.resolvedArtifacts.filter {
                it.moduleVersion.id.group == "org.xtclang" && it.moduleVersion.id.name == "xdk"
            }

            if (xdkArtifacts.isNotEmpty()) {
                println("✅ Successfully resolved XDK $xdkVersion from Maven Central")
                xdkArtifacts.forEach { artifact ->
                    println("   - ${artifact.file.name} (${artifact.file.length()} bytes)")
                    println("   - Module: ${artifact.moduleVersion.id}")
                }
            } else {
                println("❌ XDK $xdkVersion not found in repositories")
            }
        }
    }
}

tasks.register("listAvailableVersions") {
    doLast {
        println("Checking available XDK versions in Maven Central...")
        // This would require additional Maven metadata parsing
        println("Use: ./gradlew verifyXdkAvailability -PxdkVersion=<version>")
        println("Examples:")
        println("  -PxdkVersion=0.4.4-SNAPSHOT  (for snapshots)")
        println("  -PxdkVersion=0.4.3           (for releases)")
    }
}