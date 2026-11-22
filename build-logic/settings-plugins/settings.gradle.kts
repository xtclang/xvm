buildCache {
    local {
        // As in the root build: each checkout keeps its own cache, and CI keeps it in the Gradle user home.
        if (!providers.environmentVariable("CI").isPresent) {
            directory = file("../../.gradle/build-cache")
        }
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

plugins {
    // Version literal here is unavoidable: the catalog (`libs`) is not accessible from
    // settings.gradle.kts plugin blocks (gradle/gradle#24876, #36437). The catalog IS
    // accessible from build.gradle.kts, where we use it for the kotlin-dsl classpath
    // dep — that's the single source of truth; this literal must track it.
    id("org.gradle.toolchains.foojay-resolver").version("1.0.0")
}

@Suppress("UnstableApiUsage")
toolchainManagement {
    jvm {
        javaRepositories {
            repository("foojay") {
                resolverClass.set(org.gradle.toolchains.foojay.FoojayToolchainResolver::class.java)
            }
        }
    }
}

rootProject.name = "settings-plugins"
