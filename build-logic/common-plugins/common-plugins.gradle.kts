plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$embeddedKotlinVersion")
    implementation("io.github.rybalkinsd:kohttp:0.12.0")
    implementation("io.github.rybalkinsd:kohttp-jackson:0.12.0")
}
// TODO: 8.5 version catalog interface?

repositories {
    mavenCentral()
    gradlePluginPortal()
}

logger.lifecycle("[${project.name}] Gradle version: v${gradle.gradleVersion} (embedded Kotlin: v$embeddedKotlinVersion).")
