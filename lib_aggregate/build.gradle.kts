plugins {
    id("org.xtclang.build.version")
    alias(libs.plugins.xtc)
}

dependencies {
    xtcJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
}
