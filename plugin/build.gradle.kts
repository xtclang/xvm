plugins {
    id("org.xvm.build.java")
    id("org.xvm.build.publish")
    id("java-gradle-plugin")
    alias(libs.plugins.tasktree)
}

internal val pluginGroup = "$group.plugin"
internal val pluginVersion = version.toString()
internal val pluginId = xdkPropertyOrgXvm("plugin.id")

fun sanityCheckPlugin(): Boolean {
    val projectGroup = project.group.toString()
    val pluginGroupProperty = xdkPropertyOrgXvm("plugin.group", pluginGroup)
    if (pluginGroupProperty != pluginGroup) {
        throw buildException("$prefix plugin group defined in two places, the version catalog .toml file, AND the plugin properties.")
    }
    if (pluginGroup.indexOf(project.group.toString()) != 0 || pluginGroup == projectGroup) {
        logger.warn("WARNING: The plugin needs to be rebuilt for relatively small XDK changes; it is not a good idea to use a different version than the XDK repository root version.")
        throw buildException("$prefix group mismatch, the plugin group is supposed to be 'project.group' + '.plugin'. ($pluginGroup != $projectGroup.plugin)")
    }
    if (pluginVersion != project.version.toString()) {
        throw buildException("$prefix version mismatch between project version (${project.version}) and plugin version ($pluginVersion)")
    }
    logger.lifecycle("$prefix Plugin sanity check passed; artifact='$pluginGroup:$name:$pluginVersion' (plugin id: $pluginId)")
    return true
}

tasks {
    assemble {
        sanityCheckPlugin()
    }

    withType<Javadoc> {
        enabled = false
    }
}

publishing {
    publications {
        create<MavenPublication>("xtcPlugin") {
            groupId = pluginGroup
            artifactId = project.name
            version = pluginVersion
            from(components["java"])
            logger.lifecycle("$prefix Publication '$groupId:$artifactId:$version.$name' configured.")
        }
    }
}

gradlePlugin {
    logger.lifecycle("$prefix Configuring gradlePlugin; isAutomatedPublishing=$isAutomatedPublishing")

    @Suppress("UnstableApiUsage")
    vcsUrl = xdkPropertyOrgXvm("plugin.vcs.url")
    @Suppress("UnstableApiUsage")
    website = xdkPropertyOrgXvm("plugin.website")

    plugins {
        create("xtcPlugin") {
            version = pluginVersion
            id = pluginId
            implementationClass = xdkPropertyOrgXvm("plugin.implementation.class")
            displayName = xdkPropertyOrgXvm("plugin.display.name")
            description = xdkPropertyOrgXvm("plugin.description")
            @Suppress("UnstableApiUsage")
            tags = listOfNotNull("xtc", "gradle", "plugin", "xdk")
        }
    }
}
