plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$embeddedKotlinVersion")

    // TODO: Figure out how to get this to live in the version catalog instead. Since
    //  build logic for talking with GitHub needs this, and because that is compiled
    //  as a build-logic plugin, we are too early in the lifecycle to resolve from the
    //  version catalog. Not even with the "best practice hacks", that are mostly applicable
    //  for this.
    val kohttpVersion = "0.12.0"
    implementation("io.github.rybalkinsd:kohttp:$kohttpVersion")
    implementation("io.github.rybalkinsd:kohttp-jackson:$kohttpVersion")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

logger.lifecycle("[${project.name}] Gradle version: v${gradle.gradleVersion} (embedded Kotlin: v$embeddedKotlinVersion).")
