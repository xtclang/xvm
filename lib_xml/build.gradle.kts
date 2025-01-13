plugins {
    id("org.xtclang.build.xdk.versioning")
    alias(libs.plugins.xtc)
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
}
