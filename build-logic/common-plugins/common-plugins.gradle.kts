plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$embeddedKotlinVersion")

    val kohttpVersion = "0.12.0"
    implementation("io.github.rybalkinsd:kohttp:$kohttpVersion")
    implementation("io.github.rybalkinsd:kohttp-jackson:$kohttpVersion")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

logger.lifecycle("[${project.name}] Gradle version: v${gradle.gradleVersion} (embedded Kotlin: v$embeddedKotlinVersion).")
