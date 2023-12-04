import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE

plugins {
    id("org.xtclang.build.java")
    id("org.xtclang.build.publish")
    alias(libs.plugins.gradle.portal.publish)
    alias(libs.plugins.tasktree)
}

val semanticVersion: SemanticVersion by extra

val xtcJavaToolsJarConsumer by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
    outgoing.artifact(tasks.jar)
    attributes {
        attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(JAR))
    }
}

val xtcPluginRepoProvider by configurations.registering {
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(buildRepoDirectory) {
        builtBy("publishAllPublicationsToBuildRepository")
        type = ArtifactTypeDefinition.DIRECTORY_TYPE
    }
    attributes {
        attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("buildRepo"))
    }
}

dependencies {
    @Suppress("UnstableApiUsage")
    xtcJavaToolsJarConsumer(libs.javatools)
}

// Properties for the ID (unique to a plugin)
private val pluginId = getXdkProperty("org.xtclang.plugin.id")

// Properties for the artifact
private val pluginName = project.name
private val pluginGroup = getXdkProperty("org.xtclang.plugin.group", group.toString())
private val pluginVersion = getXdkProperty("org.xtclang.plugin.version", version.toString())

private val shouldBundleJavaTools = getXdkPropertyBoolean("org.xtclang.plugin.bundle.javatools")

logger.lifecycle("$prefix Plugin (id: $pluginId) will get project and artifact versions like this: '$pluginGroup:$pluginName:$pluginVersion'")

publishing {
    publications {
        register<MavenPublication>("xtcPlugin") {
            groupId = pluginGroup
            artifactId = pluginName
            version = pluginVersion
            artifact(tasks.jar)  // we have two more jar artifacts with "javadoc" and "source" classifiers, respectively. Tell Gradle we do NOT want those to be part of the publication (i.e. don't use from(components["java"]) // TODO: Do not publish source or javadoc
            logger.lifecycle("$prefix Publication '$groupId:$artifactId:$version' (name: '$name') configured.")
        }
    }
}

// TODO: For pure maven plugin artifacts, we can also use "de.benediktritter.maven-plugin-development, mavenPlugin { }"
gradlePlugin {
    // The built-in pluginMaven publication can be disabled with "isAutomatedPublishing=false"
    // However, this results in the Gradle version (with Gradle specific metadata) of the plugin not
    // being published. To read it from at least a local repo, we need that artifact too, hence we
    // get three artifacts.
    isAutomatedPublishing = getXdkPropertyBoolean("org.xtclang.plugin.isAutomatedPublishing", true)

    logger.info("$prefix Configuring gradlePlugin; isAutomatedPublishing=$isAutomatedPublishing")

    @Suppress("UnstableApiUsage")
    vcsUrl = getXdkProperty("org.xtclang.plugin.vcs.url")
    @Suppress("UnstableApiUsage")
    website = getXdkProperty("org.xtclang.plugin.website")

    plugins {
        val xtc by registering {
            version = pluginVersion
            id = pluginId
            implementationClass = getXdkProperty("org.xtclang.plugin.implementation.class")
            displayName = getXdkProperty("org.xtclang.plugin.display.name")
            description = getXdkProperty("org.xtclang.plugin.description")
            logger.lifecycle("$prefix Configuring gradlePlugin; pluginId=$pluginId, implementationClass=$implementationClass, displayName=$displayName, description=$description")
            @Suppress("UnstableApiUsage")
            tags = listOfNotNull("xtc", "language", "ecstasy", "xdk")
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    enabled = false
}

tasks.withType<PublishToMavenRepository>().matching { it.name.startsWith("publishPluginMaven") }.configureEach {
    enabled = false
    logger.lifecycle("$prefix Disabled default publication task: '$name'. The task '${name.replace("PluginMaven", "XtcPlugin")}' should be equivalent.")
}

val jar by tasks.existing(Jar::class) {
    dependsOn(gradle.includedBuild("javatools").task(":jar"))
    if (shouldBundleJavaTools) {
        from(zipTree(xtcJavaToolsJarConsumer.get().singleFile))
        doLast {
            logger.info("$prefix Creating fat jar bundling the associated XDK version as the plugin version into the plugin.")
        }
    }
    manifest {
        attributes(
            "Manifest-Version" to "1.0",
            "Xdk-Version" to semanticVersion.toString(),
            "Main-Class" to "org.xtclang.plugin.Usage",
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
