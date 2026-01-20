plugins {
    alias(libs.plugins.xtc)
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    xtcModule(libs.xdk.jsondb)  // transitively includes aggregate, collections, json, oodb
    xtcModule(libs.xdk.xunit)
}
