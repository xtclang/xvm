plugins {
    id("org.xtclang.build.xdk.versioning")
    alias(libs.plugins.xtc)
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    xtcModule(libs.xdk.aggregate)
    xtcModule(libs.xdk.collections)
    xtcModule(libs.xdk.json)
    xtcModule(libs.xdk.oodb)
}
