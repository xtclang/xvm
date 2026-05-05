plugins {
    `kotlin-dsl`
}

// Access properties from XdkPropertiesService for toolchain configuration
val xdkPropertiesService = gradle.sharedServices.registrations.named("xdkPropertiesService").get().service.get() as XdkPropertiesService
val jdkVersion = providers.provider {
    xdkPropertiesService.get("org.xtclang.java.jdk")?.toInt()
        ?: error("org.xtclang.java.jdk not found")
}

// Kotlin auto-inherits this toolchain (no explicit kotlin.jvmToolchain needed).
java {
    toolchain {
        languageVersion.set(jdkVersion.map { JavaLanguageVersion.of(it) })
    }
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}
