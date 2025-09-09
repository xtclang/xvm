import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_JAVATOOLS_JAR

plugins {
    id("org.xtclang.build.xdk.versioning")
    alias(libs.plugins.xdk.build.java)
    alias(libs.plugins.xdk.build.publish)
    alias(libs.plugins.gradle.portal.publish)
}

// Extract values during configuration to avoid capturing project references
private val enablePreviewValue = getXdkPropertyBoolean("org.xtclang.java.enablePreview", false)
private val enableNativeAccessValue = getXdkPropertyBoolean("org.xtclang.java.enableNativeAccess", false)
private val defaultJvmArgsValue = buildList {
    add("-ea")
    if (enablePreviewValue) {
        add("--enable-preview")
    }
    if (enableNativeAccessValue) {
        add("--enable-native-access=ALL-UNNAMED")
    }
}

// Generate resource file with default JVM args computed at plugin build time
val generateDefaultJvmArgs by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources")
    val outputFile = outputDir.map { it.file("org/xtclang/build/internal/defaultJvmArgs.properties") }
    
    // Declare properties as inputs for proper invalidation
    inputs.property("enablePreview", enablePreviewValue)
    inputs.property("enableNativeAccess", enableNativeAccessValue)
    
    outputs.file(outputFile)
    
    doLast {
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("""
                # Auto-generated default JVM arguments computed at plugin build time
                defaultJvmArgs=${defaultJvmArgsValue.joinToString(",")}
                """.trimIndent())
        }
        logger.info("[plugin] Generated defaultJvmArgs.properties with: $defaultJvmArgsValue")
    }
}

tasks.processResources {
    dependsOn(generateDefaultJvmArgs)
    from(layout.buildDirectory.dir("generated/resources"))
}


private val semanticVersion: SemanticVersion by extra

private val pprefix = "org.xtclang"

// Property for the Plugin ID (unique to a plugin) - extracted during configuration
private val pluginId = getXdkProperty("$pprefix.plugin.id")

// Properties for the artifact - extracted during configuration
private val pluginName = project.name
private val pluginGroup = getXdkProperty("$pprefix.plugin.group", group.toString())
private val pluginVersion = getXdkProperty("$pprefix.plugin.version", version.toString())

logger.info("[plugin] Plugin (id: '$pluginId') artifact version identifier: '$pluginGroup:$pluginName:$pluginVersion'")

private val shouldBundleJavaTools = getXdkPropertyBoolean("$pprefix.plugin.bundle.javatools")
private val javaToolsContents = objects.fileCollection()

val xdkJavaToolsJarConsumer by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(XDK_ARTIFACT_NAME_JAVATOOLS_JAR))
    }
}

dependencies {
    if (shouldBundleJavaTools) {
        @Suppress("UnstableApiUsage") xdkJavaToolsJarConsumer(libs.javatools)
    }
    testImplementation(libs.junit.jupiter)
}

publishing {
    publications {
        val xtcPlugin by registering(MavenPublication::class) {
            groupId = pluginGroup
            artifactId = pluginName
            version = pluginVersion
            artifact(tasks.jar)
            // we have two more jar artifacts with "javadoc" and "source" classifiers, respectively. Tell Gradle we do NOT want those to be part of the
            // publication (i.e. don't use from(components["java"])
            logger.info("[plugin] Publication '$groupId:$artifactId:$version' (name: '$name') configured.")
        }
    }
}

// Extract plugin configuration values during configuration
private val isAutomatedPublishingValue = getXdkPropertyBoolean("$pprefix.plugin.isAutomatedPublishing", true)
private val vcsUrlValue = getXdkProperty("$pprefix.plugin.vcs.url")
private val websiteValue = getXdkProperty("$pprefix.plugin.website")
private val implementationClassValue = getXdkProperty("$pprefix.plugin.implementation.class")
private val displayNameValue = getXdkProperty("$pprefix.plugin.display.name")
private val descriptionValue = getXdkProperty("$pprefix.plugin.description")

@Suppress("UnstableApiUsage")
gradlePlugin {
    // The built-in pluginMaven publication can be disabled with "isAutomatedPublishing=false"
    // However, this results in the Gradle version (with Gradle specific metadata) of the plugin not
    // being published. To read it from at least a local repo, we need that artifact too, hence we
    // get three artifacts.
    isAutomatedPublishing = isAutomatedPublishingValue

    logger.info("[plugin] Configuring Gradle Plugin; isAutomatedPublishing=$isAutomatedPublishingValue")

    vcsUrl = vcsUrlValue
    website = websiteValue

    plugins {
        val xtc by registering {
            version = pluginVersion
            id = pluginId
            implementationClass = implementationClassValue
            displayName = displayNameValue
            description = descriptionValue
            logger.info("[plugin] Configuring gradlePlugin; pluginId=$pluginId, implementationClass=$implementationClassValue, displayName=$displayNameValue, description=$descriptionValue")
            tags = listOf("xtc", "language", "ecstasy", "xdk")
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    enabled = false
    // TODO: Write JavaDocs for plugin.
    logger.info("[plugin] Note: JavaDoc task is currently disabled, but certain publication methods, such as for the Gradle plugin portal will still generate and publish JavaDocs.")
}

tasks.withType<PublishToMavenRepository>().matching { it.name.startsWith("publishPluginMaven") }.configureEach {
    enabled = false
    // TODO: Reuse the existing PluginMaven task instead, because that is the one gradlePluginPortal hardcodes.
    val taskName = name
    logger.info(
        "[plugin] Disabled default publication task: '$taskName'. The task '${taskName.replace("PluginMaven", "XtcPlugin")}' should be equivalent."
    )
}

tasks.withType<Jar>().configureEach {
    val taskName = name
    if (taskName == "jar") {
        if (shouldBundleJavaTools) {
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
            attributes(
                "Manifest-Version" to "1.0",
                "Xdk-Version" to semanticVersion.toString(),
                "Main-Class" to "$pprefix.plugin.Usage",
                "Name" to "/org/xtclang/plugin/",
                "Sealed" to "true",
                "Specification-Title" to "XTC Gradle and Maven Plugin",
                "Specification-Vendor" to "xtclang.org",
                "Specification-Version" to pluginVersion,
                "Implementation-Title" to "xtc-plugin",
                "Implementation-Vendor" to "xtclang.org",
                "Implementation-Version" to pluginVersion,
            )
        }
    }
}
