plugins {
    id("org.xtclang.build.java")
    // TODO: alias(libs.plugins.maven.plugin.development)
}

val build by tasks.existing {
    doFirst {
        logger.warn("$prefix Placeholder for Maven version of XTC plugin. Composite root: $compositeRootProjectDirectory")
    }
}
