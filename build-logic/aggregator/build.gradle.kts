plugins {
    `kotlin-dsl`
}

// Access properties from XdkPropertiesService for toolchain configuration
val xdkPropertiesService = gradle.sharedServices.registrations.named("xdkPropertiesService").get().service.get() as XdkPropertiesService
val jdkVersion = providers.provider {
    xdkPropertiesService.get("org.xtclang.java.jdk")?.toInt()
        ?: error("org.xtclang.java.jdk not found")
}
val kotlinJdkVersion = providers.provider {
    xdkPropertiesService.get("org.xtclang.kotlin.jdk")?.toInt()
        ?: error("org.xtclang.kotlin.jdk not found")
}

java {
    toolchain {
        languageVersion.set(jdkVersion.map { JavaLanguageVersion.of(it) })
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(kotlinJdkVersion.map { JavaLanguageVersion.of(it) })
    }
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}
