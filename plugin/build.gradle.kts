import XdkBuildLogic.XDK_ARTIFACT_NAME_JAVATOOLS_JAR

plugins {
    id("org.xtclang.build.xdk.versioning")
    alias(libs.plugins.xdk.build.java)
    alias(libs.plugins.gradle.portal.publish)
    id("java-gradle-plugin")
    id("org.xtclang.build.publishing")
}

private val defaultJvmArgs: Provider<List<String>> = extensions.getByName<Provider<List<String>>>("defaultJvmArgs")

// Generate resource file with build-time configuration
val generatePluginResources by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources")
    val buildInfoFile = outputDir.map { it.file("org/xtclang/build/internal/plugin-build-info.properties") }
    val xdkVersionProvider = libs.versions.xdk
    inputs.property("defaultJvmArgs", defaultJvmArgs)
    inputs.property("xdkVersion", xdkVersionProvider)

    outputs.file(buildInfoFile)

    doLast {
        val jvmArgs = defaultJvmArgs.get()
        val xdkVersion = xdkVersionProvider.get()

        // Generate buildInfo.properties with all build-time configuration
        buildInfoFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("""
                # Auto-generated build information
                xdk.version=$xdkVersion
                defaultJvmArgs=${jvmArgs.joinToString(",")}
                """.trimIndent())
        }
        logger.info("[plugin] Generated plugin-build-info.properties with xdk.version: $xdkVersion, defaultJvmArgs: $jvmArgs")
    }
}

tasks.processResources {
    dependsOn(generatePluginResources)
    from(layout.buildDirectory.dir("generated/resources"))
}

private val pprefix = "org.xtclang"

// Plugin metadata - resolved at configuration time (acceptable for static plugin metadata)
private val pluginIdValue: String = xdkProperties.string("$pprefix.plugin.id").get()
private val pluginGroupValue: String = xdkProperties.string("$pprefix.plugin.group", group.toString()).get()
private val pluginVersionValue: String = xdkProperties.string("$pprefix.plugin.version", version.toString()).get()
private val shouldBundleJavaTools: Provider<Boolean> = xdkProperties.boolean("$pprefix.plugin.bundle.javatools")

val xdkJavaToolsJarConsumer by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(XDK_ARTIFACT_NAME_JAVATOOLS_JAR))
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// Resolve bundling decision at configuration time (acceptable - static build configuration)
private val shouldBundleJavaToolsValue = shouldBundleJavaTools.get()

dependencies {
    if (shouldBundleJavaToolsValue) {
        xdkJavaToolsJarConsumer(libs.javatools)
    }
    testImplementation(libs.junit.jupiter)
}

// Configure project-specific publishing metadata
xdkPublishing {
    pomName.set("XTC Gradle Plugin")
    pomDescription.set("XTC Gradle Plugin")
}

// Configure publication type as Gradle Plugin (vanniktech will handle plugin marker automatically)
mavenPublishing {
    configure(
        com.vanniktech.maven.publish.GradlePlugin(
            javadocJar = com.vanniktech.maven.publish.JavadocJar.None(),
            sourcesJar = true
        )
    )
}

// Type-safe plugin configuration - resolve during configuration for gradlePlugin DSL
private val vcsUrlValue: String = xdkProperties.string("$pprefix.plugin.vcs.url").get()
private val websiteValue: String = xdkProperties.string("$pprefix.plugin.website").get()
private val pluginImplementationClassValue: String = xdkProperties.string("$pprefix.plugin.implementation.class").get()
private val pluginDisplayNameValue: String = xdkProperties.string("$pprefix.plugin.display.name").get()
private val pluginDescriptionValue: String = xdkProperties.string("$pprefix.plugin.description").get()

// Gradle plugin configuration for both vanniktech and plugin portal
gradlePlugin {
    website = websiteValue
    vcsUrl = vcsUrlValue

    plugins {
        val xtc by registering {
            id = pluginIdValue
            implementationClass = pluginImplementationClassValue
            displayName = pluginDisplayNameValue
            description = pluginDescriptionValue
            tags = listOf("xtc", "language", "compiler", "ecstasy")
        }
    }
}


tasks.withType<Javadoc>().configureEach {
    enabled = false
    // TODO: Write JavaDocs for plugin.
}


tasks.withType<Jar>().configureEach {
    val taskName = name
    if (taskName == "jar") {
        if (shouldBundleJavaToolsValue) {
            /*
             * It's important that this is a provider/lazy, because xdkJavaToolsJarConsumer kickstarts an
             * entire javatools fatjar build when you resolve it, and that's what we have to do if we want
             * it in the plugin, even though we are just configuring here. We will lift out the manualTests
             * "use the plugin as an external party" test from the build source line to make sure dependencies
             * are preserved correctly, and also add a dry-run/vs real diff test to see that our build caching
             * does not break.
             */
            // TODO with the right categories we could just, instead of grabbing the JAR, ask for the classes as outgoing config for javatools
            inputs.files(xdkJavaToolsJarConsumer)
            val jarFiles = { zipTree(xdkJavaToolsJarConsumer.get().singleFile) }
            from(jarFiles)
        }
        manifest {
            // Dependency on javatools handled through xdkJavaToolsJarConsumer configuration

            val baseAttributes = mapOf(
                "Manifest-Version" to "1.0",
                "Xdk-Version" to semanticVersion,
                "Main-Class" to "$pprefix.plugin.Usage",
                "Name" to "/org/xtclang/plugin/",
                "Sealed" to "true",
                "Specification-Title" to "XTC Gradle and Maven Plugin",
                "Specification-Vendor" to "xtclang.org",
                "Specification-Version" to pluginVersionValue,
                "Implementation-Title" to "xtc-plugin",
                "Implementation-Vendor" to "xtclang.org",
                "Implementation-Version" to pluginVersionValue,
            )

            attributes(baseAttributes)
        }
    }
}
