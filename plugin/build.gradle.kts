import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE

plugins {
    id("org.xvm.build.java")
    id("org.xvm.build.publish")
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

private val pluginGroup = "$group.plugin"
private val pluginVersion = version.toString()
private val pluginId = getXdkProperty("org.xvm.plugin.id")

private val shouldBundleJavaTools: Boolean get() = getXdkPropertyBoolean("org.xvm.plugin.bundle.javatools", false)

publishing {
    publications {
        create<MavenPublication>("xtcPlugin") {
            groupId = pluginGroup
            artifactId = project.name
            version = pluginVersion
            from(components["java"]) // TODO: Do not publish source or javadoc
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
    isAutomatedPublishing = getXdkPropertyBoolean("org.xvm.plugin.isAutomatedPublishing", true)

    logger.info("$prefix Configuring gradlePlugin; isAutomatedPublishing=$isAutomatedPublishing")

    @Suppress("UnstableApiUsage")
    vcsUrl = getXdkProperty("org.xvm.plugin.vcs.url")
    @Suppress("UnstableApiUsage")
    website = getXdkProperty("org.xvm.plugin.website")

    plugins {
        create("xtcPlugin") {
            version = pluginVersion
            id = pluginId
            implementationClass = getXdkProperty("org.xvm.plugin.implementation.class")
            displayName = getXdkProperty("org.xvm.plugin.display.name")
            description = getXdkProperty("org.xvm.plugin.description")
            logger.lifecycle("$prefix Configuring gradlePlugin; pluginId=$pluginId, implementationClass=$implementationClass, displayName=$displayName, description=$description")
            @Suppress("UnstableApiUsage")
            tags = listOfNotNull("xtc", "language", "ecstasy", "xdk")
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    // TODO distribute and package Javadocs in the future, when we have them.
    enabled = false
}

val sanityCheckPluginVersion by tasks.registering {
    doLast {
        val mismatch = "XTC Plugin version mismatch"
        val projectGroup = project.group.toString()
        val projectVersion = project.version.toString()
        val catalogPluginVersion = libs.versions.xtc.plugin.get()
        val pluginGroupProperty = getXdkProperty("org.xvm.plugin.group", pluginGroup)

        if (pluginGroupProperty != pluginGroup) {
            throw buildException("$mismatch; the version catalog .toml file, AND the plugin properties.")
        }
        if (pluginVersion != catalogPluginVersion) {
            throw buildException("$mismatch between plugin version ($pluginVersion) and version catalog ($catalogPluginVersion)")
        }
        if (pluginVersion != projectVersion) {
            throw buildException("$mismatch between plugin version ($pluginVersion) and plugin project ($projectVersion)")
        }
        if (pluginGroup.indexOf(projectGroup) != 0 || pluginGroup == projectGroup) {
            logger.warn("$prefix WARNING: The plugin needs to be rebuilt for relatively small XDK changes; it is not a good idea to use a different version than the XDK repository root version.")
            throw buildException("$mismatch; the plugin group is supposed to be 'project.group' + '.plugin'. ($pluginGroup != $projectGroup.plugin)")
        }

        logger.info("$prefix XTC Plugin sanity check passed; plugin artifact='$pluginGroup:$name:$pluginVersion' (plugin id: $pluginId)")
        logger.info("$prefix XTC Plugin sanity check passed; inherited artifact='$group:$name:$version' (plugin id: $pluginId)")
    }
}

val assemble by tasks.existing {
    dependsOn(sanityCheckPluginVersion)
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
            "Main-Class" to "org.xvm.plugin.Usage",
            "Name" to "/org/xvm/plugin/",
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
