import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    alias(libs.plugins.xdk.build.java)
    alias(libs.plugins.gradle.portal.publish)  // Automatically applies java-gradle-plugin
    alias(libs.plugins.xdk.build.publishing)
}

private val minimumGradleVersion = xdkProperties.stringValue(
    "org.xtclang.plugin.minimum.gradle.version",
    default = gradle.gradleVersion)
private val defaultJvmArgs: Provider<List<String>> = extensions.getByName<Provider<List<String>>>("defaultJvmArgs")
private val jdkVersionProvider = xdkProperties.int("org.xtclang.java.jdk")

// Generate resource file with build-time configuration
val generatePluginResources by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources")
    val buildInfoFile = outputDir.map { it.file("org/xtclang/build/internal/plugin-build-info.properties") }
    val xdkVersionProvider = provider { version.toString() }
    inputs.properties(mapOf(
        "defaultJvmArgs" to defaultJvmArgs,
        "xdkVersion" to xdkVersionProvider,
        "jdkVersion" to jdkVersionProvider,
        "minimumGradleVersion" to minimumGradleVersion
    ))
    outputs.file(buildInfoFile)
    doLast {
        val jvmArgs = defaultJvmArgs.get()
        val xdkVersion = xdkVersionProvider.get()
        val jdkVersion = jdkVersionProvider.get()
        logger.lifecycle("Generating build info [xdkVersion=$xdkVersion, minimumGradleVersion=${minimumGradleVersion}, jvmArgs=$jvmArgs]")
        // Generate buildInfo.properties with all build-time configuration
        buildInfoFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("""
                # Auto-generated build information
                xdk.version=$xdkVersion
                jdk.version=$jdkVersion
                defaultJvmArgs=${jvmArgs.joinToString(",")}
                """.trimIndent())
        }
        logger.info("[plugin] Generated plugin-build-info.properties with xdk.version: $xdkVersion, jdk.version: $jdkVersion, defaultJvmArgs: $jvmArgs")
    }
}

tasks.processResources {
    dependsOn(generatePluginResources)
    from(layout.buildDirectory.dir("generated/resources"))
}

private val pprefix = "org.xtclang"

// Plugin metadata - resolved at configuration time (acceptable for static plugin metadata)
private val pluginIdValue: String = xdkProperties.stringValue("$pprefix.plugin.id")
private val pluginVersionValue: String = xdkProperties.stringValue("$pprefix.plugin.version", version.toString())

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Compile-time only - javatools types available for compilation but loaded via custom classloader at runtime
    compileOnly(libs.javatools)
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
        GradlePlugin(
            javadocJar = JavadocJar.None(),
            sourcesJar = SourcesJar.Sources()
        )
    )
}

// Add Gradle plugin API version attribute to published variants for proper plugin resolution
// This is required for Gradle to correctly resolve the plugin when consumed from Maven/Gradle repositories
// Set to minimum supported Gradle version to ensure compatibility with consumers using older Gradle versions
val pluginApiVersionAttribute = GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE

configurations.all {
    if (name == "runtimeElements" || name == "apiElements") {
        attributes {
            attribute(pluginApiVersionAttribute, objects.named(GradlePluginApiVersion::class.java, minimumGradleVersion))
        }
    }
}

// Type-safe plugin configuration - resolve during configuration for gradlePlugin DSL
private val vcsUrlValue: String = xdkProperties.stringValue("$pprefix.plugin.vcs.url")
private val websiteValue: String = xdkProperties.stringValue("$pprefix.plugin.website")
private val pluginImplementationClassValue: String = xdkProperties.stringValue("$pprefix.plugin.implementation.class")
private val pluginDisplayNameValue: String = xdkProperties.stringValue("$pprefix.plugin.display.name")
private val pluginDescriptionValue: String = xdkProperties.stringValue("$pprefix.plugin.description")

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

// Enable overriding lint warnings specifically for the plugin project.
// TODO: Should be global default, but javatools has many linting errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:all")
}

tasks.withType<Jar>().configureEach {
    val taskName = name
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
    logger.info("[plugin] Configuring Jar task: $taskName")
    // TODO: skip the javadocJar and sourceJar; they do need manifests. (Why?)
    if (taskName == "jar") {
        manifest {
            attributes(baseAttributes)
        }
        logger.info("[plugin] Configured '$taskName' manifest with attributes: $baseAttributes")
    }
}
