plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$embeddedKotlinVersion")
    implementation("io.github.rybalkinsd:kohttp:0.12.0")
    implementation("io.github.rybalkinsd:kohttp-jackson:0.12.0")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

logger.lifecycle("Kotlin details: (embedded Kotlin version: $embeddedKotlinVersion)")
