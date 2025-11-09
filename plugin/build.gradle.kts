import org.gradle.api.tasks.compile.JavaCompile

plugins {
    alias(libs.plugins.xdk.build.java)
    alias(libs.plugins.gradle.portal.publish)  // Automatically applies java-gradle-plugin
    alias(libs.plugins.xdk.build.publishing)
}

private val defaultJvmArgs: Provider<List<String>> = extensions.getByName<Provider<List<String>>>("defaultJvmArgs")
private val jdkVersionProvider = xdkProperties.int("org.xtclang.java.jdk")

// Generate resource file with build-time configuration
val generatePluginResources by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources")
    val buildInfoFile = outputDir.map { it.file("org/xtclang/build/internal/plugin-build-info.properties") }
    val xdkVersionProvider = provider { version.toString() }
    inputs.property("defaultJvmArgs", defaultJvmArgs)
    inputs.property("xdkVersion", xdkVersionProvider)
    inputs.property("jdkVersion", jdkVersionProvider)
    outputs.file(buildInfoFile)
    doLast {
        val jvmArgs = defaultJvmArgs.get()
        val xdkVersion = xdkVersionProvider.get()
        val jdkVersion = jdkVersionProvider.get()
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
    // Compile against javatools for type information
    compileOnly(libs.javatools)

    // Make available at runtime during XDK development
    // Published plugin users MUST get javatools from their XDK dependency, not from this
    runtimeOnly(libs.javatools)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.javatools)  // For reentrancy tests
}

// ==================================================================================
// TERRIBLE WORKAROUND: Exclude javatools from publication while keeping it at runtime
// ==================================================================================
// This is a horrible hack necessitated by Gradle's inflexible publication model.
//
// The Problem:
// - We need javatools on the plugin's runtime classpath during XDK development (for LauncherException)
// - We CANNOT bundle javatools in the plugin JAR (would bloat the plugin and cause conflicts)
// - We MUST NOT publish javatools as a dependency (users get it from their XDK distribution)
//
// Why This Is Terrible:
// 1. Text-based regex manipulation of generated JSON/XML files
// 2. Fragile - breaks if Gradle changes metadata format
// 3. No type safety or validation
// 4. Executed in afterEvaluate/doLast hooks (late binding anti-pattern)
// 5. Groovy XML Node manipulation in Kotlin (type-unsafe cast hell)
//
// Better Solutions (that don't exist in Gradle):
// 1. A "developmentOnly" scope like Spring Boot's that isn't published
// 2. A way to mark dependencies as "local development only" in the publication API
// 3. Publication filters that work on dependency metadata (not just XML/JSON text)
// 4. Separate runtime vs publication dependency configurations with first-class support
// 5. A way to programmatically modify ComponentWithVariants before publication
//
// Why We Can't Use Better Approaches:
// - compileOnly alone: Causes ClassNotFoundException at runtime during XDK development
// - runtimeOnly alone: Gets published as a dependency (breaks published plugin users)
// - Custom configuration: Doesn't get added to plugin classloader automatically
// - Exclude in configuration: Gradle still validates and publishes it
//
// What This Actually Does:
// 1. Keeps runtimeOnly(libs.javatools) for XDK development runtime access
// 2. Suppresses validation error about missing version
// 3. Uses regex to strip javatools from generated .module JSON file
// 4. Uses Groovy XML DOM manipulation to strip javatools from generated .pom XML file
//
// If You're Reading This Because It Broke:
// - Check if Gradle changed the .module JSON format (unlikely but possible)
// - Check if Gradle changed how POM XML is structured
// - Consider lobbying Gradle for a better solution (developmentOnly scope?)
// ==================================================================================

// Suppress validation error for javatools dependency without version
tasks.withType<GenerateModuleMetadata> {
    suppressedValidationErrors.add("dependencies-without-versions")

    // HACK: Remove javatools from generated Gradle module metadata (.module file)
    // This is terrible because we're doing text manipulation of generated JSON
    doLast {
        val moduleFile = outputFile.asFile.get()
        if (moduleFile.exists()) {
            val content = moduleFile.readText()
            // Use regex to remove javatools dependency from JSON
            val modified = content.replace(
                Regex("""\s*\{\s*"group":\s*"org\.xtclang",\s*"module":\s*"javatools"\s*},?"""),
                ""
            ).replace(
                Regex(""""dependencies":\s*\[\s*,"""),
                """"dependencies": ["""
            ).replace(
                Regex(""""dependencies":\s*\[\s*\]"""),
                """"dependencies": []"""
            )
            moduleFile.writeText(modified)
        }
    }
}

// HACK: Remove javatools from published Maven POM
// This is terrible because we're using Groovy XML Node manipulation with unsafe casts
afterEvaluate {
    publishing {
        publications {
            withType<MavenPublication> {
                pom {
                    withXml {
                        val dependenciesNode = asNode().get("dependencies")
                        if (dependenciesNode is groovy.util.NodeList && dependenciesNode.isNotEmpty()) {
                            val deps = dependenciesNode[0] as groovy.util.Node
                            val toRemove = mutableListOf<groovy.util.Node>()
                            deps.children().filterIsInstance<groovy.util.Node>().forEach { dep ->
                                val artifactIdNodes = dep.get("artifactId") as? groovy.util.NodeList
                                val artifactId = (artifactIdNodes?.firstOrNull() as? groovy.util.Node)?.text()
                                if (artifactId == "javatools") {
                                    toRemove.add(dep)
                                }
                            }
                            toRemove.forEach { deps.remove(it) }
                        }
                    }
                }
            }
        }
    }
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

// Enable lint warnings specifically for the plugin project
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
