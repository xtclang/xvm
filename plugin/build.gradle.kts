import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE

plugins {
    id("org.xvm.build.java")
    id("org.xvm.build.publish")
    id("java-gradle-plugin")
    alias(libs.plugins.tasktree)
}

val xtcJavaToolsJarConsumer by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
    outgoing.artifact(tasks.jar)
    attributes {
        attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
        attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(JAR))
    }
}

dependencies {
    xtcJavaToolsJarConsumer(libs.javatools)
}

internal val pluginGroup = "$group.plugin"
internal val pluginVersion = version.toString()
internal val pluginId = getXdkProperty("org.xvm.plugin.id")

// TODO: Build another plugin artifact with the javatools included?
internal val shouldBundleJavaTools: Boolean get() = getXdkPropertyBoolean("org.xvm.plugin.bundle.javatools", false)

val assemble by tasks.existing {
    dependsOn(sanityCheckPluginVersion)
}

val jar by tasks.existing(Jar::class) {
    dependsOn(gradle.includedBuild("javatools").task(":jar"))
    if (shouldBundleJavaTools) {
        from(zipTree(xtcJavaToolsJarConsumer.get().singleFile))
        doLast {
            logger.lifecycle("$prefix Creating fat jar bundling the associated XDK version as the plugin version into the plugin.")
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    // TODO distribute and package Javadocs in the future, when we have them.
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("xtcPlugin") {
            groupId = pluginGroup
            artifactId = project.name
            version = pluginVersion
            from(components["java"])
            logger.lifecycle("$prefix Publication '$groupId:$artifactId:$version' (name: '$name') configured.")
        }
    }
}

gradlePlugin {
    // We may  want to manually configure the publications for the plugins, or we get semi-redundant tasks, which
    // may be slightly confusing. We want full control over the publication process anyway, and we do have
    // shared build logic, that makes sure that any declared publication is consistently applied over our
    // projects that publish artifacts.
    isAutomatedPublishing = true

    logger.lifecycle("$prefix Configuring gradlePlugin; isAutomatedPublishing=$isAutomatedPublishing")

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
            tags = listOfNotNull("xtc", "gradle", "plugin", "xdk")
        }
    }
}

val sanityCheckPluginVersion by tasks.registering {
    doLast {
        val mismatch = "XTC Plugin version mismatch"
        val projectGroup = project.group.toString()
        val projectVersion = project.version.toString()
        val catalogPluginVersion = libs.versions.xtcplugin.get()
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
            logger.warn("WARNING: The plugin needs to be rebuilt for relatively small XDK changes; it is not a good idea to use a different version than the XDK repository root version.")
            throw buildException("$mismatch; the plugin group is supposed to be 'project.group' + '.plugin'. ($pluginGroup != $projectGroup.plugin)")
        }

        logger.lifecycle("XTC Plugin sanity check passed; plugin artifact='$pluginGroup:$name:$pluginVersion' (plugin id: $pluginId)")
        logger.lifecycle("XTC Plugin sanity check passed; inherited artifact='$group:$name:$version' (plugin id: $pluginId)")
    }
}

