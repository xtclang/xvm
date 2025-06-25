plugins {
    id("org.xtclang.build.xdk.versioning")
    id("org.xtclang.xtc-plugin")
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
}

