plugins {
    id("org.xtclang.build.version")
    alias(libs.plugins.xtc)
}

dependencies {
    xtcJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    xtcModule(libs.xdk.aggregate)
    xtcModule(libs.xdk.collections)
    xtcModule(libs.xdk.json)
    xtcModule(libs.xdk.oodb)
}
