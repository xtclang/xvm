plugins {
    alias(libs.plugins.xtc)
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    xtcModule(libs.xdk.json)
    xtcModule(libs.xdk.net)  // transitively includes crypto
}
