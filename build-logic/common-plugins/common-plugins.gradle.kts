plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$embeddedKotlinVersion")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

logger.lifecycle("Kotlin details: (embedded Kotlin version: $embeddedKotlinVersion)")
