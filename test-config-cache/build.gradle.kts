plugins {
    id("java")
    id("org.xtclang.xtc-plugin") version "0.4.4-SNAPSHOT"
}

repositories {
    mavenCentral()
}

dependencies {
    // Add some test dependencies
    testImplementation("junit:junit:4.13.2")
}

// Configure XTC
xtc {
    // Basic configuration
}

// Add a simple test task to verify everything works
tasks.register("testConfigCache") {
    doLast {
        println("Configuration cache test successful!")
    }
}