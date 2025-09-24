/**
 * Docker build and management for XVM project.
 * Docker build tasks are provided by the org.xtclang.build.docker convention plugin.
 *
 * Docker tasks (buildAmd64, buildArm64, buildAll, pushAmd64, pushArm64, pushAll, cleanImages)
 * are automatically created by the org.xtclang.build.docker convention plugin
 */

plugins {
    base
    id("org.xtclang.build.xdk.versioning")
    id("org.xtclang.build.docker")
}

// Create consumer configuration for XDK distribution zip
val xdkDistConsumer by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("xdk-distribution-archive"))
    }
}

dependencies {
    xdkDistConsumer(libs.xdk)
}
