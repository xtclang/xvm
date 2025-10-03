plugins {
    `kotlin-dsl`
}

val kotlinDslJavaVersion = JavaLanguageVersion.of(24)

java {
    toolchain {
        languageVersion.set(kotlinDslJavaVersion)
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(kotlinDslJavaVersion)
    }
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    }
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.gradle.toolchains.foojay-resolver-convention:org.gradle.toolchains.foojay-resolver-convention.gradle.plugin:1.0.0")
}
