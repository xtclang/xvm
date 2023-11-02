plugins {
    id("org.xvm.build.version")
    alias(libs.plugins.xtc)
    alias(libs.plugins.tasktree)
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
