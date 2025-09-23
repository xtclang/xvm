import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_JAVATOOLS_JAR
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

plugins {
    id("org.xtclang.build.xdk.versioning")
    alias(libs.plugins.xdk.build.java)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.gradle.portal.publish)
    id("java-gradle-plugin")
    id("org.xtclang.build.publishing")
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

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    if (shouldBundleJavaTools) {
        @Suppress("UnstableApiUsage") xdkJavaToolsJarConsumer(libs.javatools)
    }
    testImplementation(libs.junit.jupiter)
}

// Configure Vanniktech Maven Publish for Gradle Plugin
mavenPublishing {
    coordinates(pluginGroup, pluginName, pluginVersion)

    // Configure as Gradle Plugin (vanniktech will handle plugin marker automatically)
    configure(
        com.vanniktech.maven.publish.GradlePlugin(
            javadocJar = com.vanniktech.maven.publish.JavadocJar.None(),
            sourcesJar = true
        )
    )

    // Maven Central publishing (disabled by default)
    val enableMavenCentral = getXdkPropertyBoolean("org.xtclang.publish.mavenCentral", false)
    if (enableMavenCentral) {
        publishToMavenCentral(automaticRelease = false)
        logger.lifecycle("[plugin] Maven Central publishing is enabled")
    } else {
        logger.info("[plugin] Maven Central publishing is disabled (use -Porg.xtclang.publish.mavenCentral=true to enable)")
    }

    // Note: Plugin Portal credentials handled by standard gradle.publish.* properties
    // Validation handled by root validateCredentials task when Portal publishing enabled
    
    
    pom {
        name.set(pluginName)
        description.set("XTC Gradle Plugin")
        url.set("https://xtclang.org")
        
        licenses {
            license {
                name.set("The XDK License")
                url.set("https://github.com/xtclang/xvm/tree/master/license")
            }
        }
        
        developers {
            developer {
                id.set("xtclang-workflows")
                name.set("XTC Team")
                email.set("noreply@xtclang.org")
            }
        }
    }
}

// Configure GitHub Packages repository (enabled when credentials are available)
publishing {
    repositories {
        // Use centralized credential management
        val gitHubUsername = xdkPublishingCredentials.gitHubUsername.get()
        val gitHubPassword = xdkPublishingCredentials.gitHubPassword.get()

        if (gitHubPassword.isNotEmpty()) {
            maven {
                name = "GitHub"
                url = uri("https://maven.pkg.github.com/xtclang/xvm")
                credentials {
                    username = gitHubUsername.ifEmpty { "xtclang-workflows" }
                    password = gitHubPassword
                }
            }
        } else {
            logger.lifecycle("[plugin] GitHub Packages repository not configured - missing GitHubPassword/GITHUB_TOKEN")
        }
    }
}

// Publishing tasks are handled by root build.gradle.kts

// Publication listing tasks (called by root aggregator) - now uses centralized implementation from build-logic

val listLocalPublications by tasks.registering(ListLocalPublicationsTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "List local Maven publications for this project"
    projectName.set(pluginName)
}

abstract class ListRemotePublicationsTask : DefaultTask() {
    @get:Input
    abstract val projectName: Property<String>


    @TaskAction
    fun listPublications() {
        logger.lifecycle("[${projectName.get()}] Project would publish to GitHub packages as org.xtclang:${projectName.get()}")
        logger.lifecycle("[${projectName.get()}] Use root-level './gradlew listRemotePublications' for actual GitHub API queries")
    }
}

abstract class DeleteLocalPublicationsTask : DefaultTask() {
    @get:Input
    abstract val projectName: Property<String>
    
    
    @TaskAction
    fun deletePublications() {
        val userHome = System.getProperty("user.home")
        val repoDir = File(userHome, ".m2/repository/org/xtclang/${projectName.get()}")
        
        if (!repoDir.exists()) {
            logger.lifecycle("[${projectName.get()}] No local publications found to delete")
            return
        }
        
        repoDir.deleteRecursively()
        logger.lifecycle("[${projectName.get()}] Deleted local publications: ${repoDir.absolutePath}")
    }
}

abstract class DeleteRemotePublicationsTask : DefaultTask() {
    @get:Input
    abstract val projectName: Property<String>
    
    
    @TaskAction
    fun deletePublications() {
        logger.lifecycle("[${projectName.get()}] Use GitHub packages management to delete remote publications")
    }
}

val listRemotePublications by tasks.registering(ListRemotePublicationsTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "List remote GitHub publications for this project"
    projectName.set(pluginName)
}

val deleteLocalPublications by tasks.registering(DeleteLocalPublicationsTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "Delete local Maven publications for this project"
    projectName.set(pluginName)
}

val deleteRemotePublications by tasks.registering(DeleteRemotePublicationsTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "Delete remote GitHub publications for this project"
    projectName.set(pluginName)
}

// Extract plugin configuration values during configuration
private val vcsUrlValue = getXdkProperty("$pprefix.plugin.vcs.url")
private val websiteValue = getXdkProperty("$pprefix.plugin.website")
private val implementationClassValue = getXdkProperty("$pprefix.plugin.implementation.class")
private val displayNameValue = getXdkProperty("$pprefix.plugin.display.name")
private val descriptionValue = getXdkProperty("$pprefix.plugin.description")

// Gradle plugin configuration for both vanniktech and plugin portal
gradlePlugin {
    website = websiteValue
    vcsUrl = vcsUrlValue

    plugins {
        val xtc by registering {
            id = pluginId
            implementationClass = implementationClassValue
            displayName = displayNameValue
            description = descriptionValue
            tags = listOf("xtc", "language", "compiler", "ecstasy")
        }
    }
}


tasks.withType<Javadoc>().configureEach {
    enabled = false
    // TODO: Write JavaDocs for plugin.
    logger.info("[plugin] Note: JavaDoc task is currently disabled, but certain publication methods, such as for the Gradle plugin portal will still generate and publish JavaDocs.")
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
            // Dependency on javatools handled through xdkJavaToolsJarConsumer configuration
            
            val baseAttributes = mapOf(
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
            
            attributes(baseAttributes)
        }
    }
}
