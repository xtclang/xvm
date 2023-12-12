plugins {
    id("org.xtclang.build.version")
    alias(libs.plugins.xtc)
}

val xtcTurtleProvider by configurations.registering {
    isCanBeResolved = false
    isCanBeConsumed = true
    outgoing.artifact(tasks.processResources)
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("mackDir"))
    }
}
