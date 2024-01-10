import XdkBuildLogic.Companion.XDK_ARTIFACT_NAME_JAVATOOLS_FATJAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE

plugins {
    alias(libs.plugins.xdk.build.java)
    alias(libs.plugins.xdk.build.publish)
    alias(libs.plugins.gradle.portal.publish)
    alias(libs.plugins.tasktree)
}

val pprefix = "org.xtclang"
// TODO we could put the loggers in extra.
val semanticVersion: SemanticVersion by extra

val xdkJavaToolsJarConsumer by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(XDK_ARTIFACT_NAME_JAVATOOLS_FATJAR))
    }
}

dependencies {
    @Suppress("UnstableApiUsage")
    xdkJavaToolsJarConsumer(libs.javatools)
}

// Properties for the ID (unique to a plugin)
private val pluginId = getXdkProperty("$pprefix.plugin.id")

// Properties for the artifact
private val pluginName = project.name
private val pluginGroup = getXdkProperty("$pprefix.plugin.group", group.toString())
private val pluginVersion = getXdkProperty("$pprefix.plugin.version", version.toString())

private val shouldBundleJavaTools = getXdkPropertyBoolean("$pprefix.plugin.bundle.javatools")

logger.info("$prefix Plugin (id: '$pluginId') artifact version identifier: '$pluginGroup:$pluginName:$pluginVersion'")

publishing {
    publications {
        val xtcPlugin by registering(MavenPublication::class) {
            groupId = pluginGroup
            artifactId = pluginName
            version = pluginVersion
            artifact(tasks.jar)  // we have two more jar artifacts with "javadoc" and "source" classifiers, respectively. Tell Gradle we do NOT want those to be part of the publication (i.e. don't use from(components["java"]) // TODO: Do not publish source or javadoc
            logger.info("$prefix Publication '$groupId:$artifactId:$version' (name: '$name') configured.")
        }
    }
}

// TODO: For pure maven plugin artifacts, we can also use "de.benediktritter.maven-plugin-development, mavenPlugin { }"
@Suppress("UnstableApiUsage")
gradlePlugin {
    // The built-in pluginMaven publication can be disabled with "isAutomatedPublishing=false"
    // However, this results in the Gradle version (with Gradle specific metadata) of the plugin not
    // being published. To read it from at least a local repo, we need that artifact too, hence we
    // get three artifacts.
    isAutomatedPublishing = getXdkPropertyBoolean("$pprefix.plugin.isAutomatedPublishing", true)

    logger.info("$prefix Configuring Gradle Plugin; isAutomatedPublishing=$isAutomatedPublishing")

    vcsUrl = getXdkProperty("$pprefix.plugin.vcs.url")
    website = getXdkProperty("$pprefix.plugin.website")

    plugins {
        val xtc by registering {
            version = pluginVersion
            id = pluginId
            implementationClass = getXdkProperty("$pprefix.plugin.implementation.class")
            displayName = getXdkProperty("$pprefix.plugin.display.name")
            description = getXdkProperty("$pprefix.plugin.description")
            logger.info("$prefix Configuring gradlePlugin; pluginId=$pluginId, implementationClass=$implementationClass, displayName=$displayName, description=$description")
            tags = listOfNotNull("xtc", "language", "ecstasy", "xdk")
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    enabled = false
    // TODO: Write JavaDocs for plugin.
    logger.info("$prefix Note: JavaDoc task is currently disabled, but certain publication methods, such as for the Gradle plugin portal will still generate and publish JavaDocs.")
}

tasks.withType<PublishToMavenRepository>().matching { it.name.startsWith("publishPluginMaven") }.configureEach {
    enabled = false
    // TODO: Reuse the exsting PluginMaven task instead, because that is the one gradlePluginPortal hardcodes.
    logger.info("$prefix Disabled default publication task: '$name'. The task '${name.replace("PluginMaven", "XtcPlugin")}' should be equivalent.")
}

val jar by tasks.existing(Jar::class) {
    dependsOn(gradle.includedBuild("javatools").task(":jar"))
    if (shouldBundleJavaTools) {
        val javatoolsFiles = xdkJavaToolsJarConsumer.get().files
        assert(javatoolsFiles.count() == 1)
        javatoolsFiles.forEachIndexed { i, it ->
            logger.info("$prefix Adding zipTree from $it to plugin jar (artifact ${i + 1} / ${javatoolsFiles.count()}).")
            from(zipTree(it))
        }
        doLast {
            logger.info("$prefix Creating fat jar bundling the associated XDK version as the plugin version into the plugin.")
        }
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
