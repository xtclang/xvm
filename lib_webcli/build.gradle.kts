plugins {
    alias(libs.plugins.xtc)
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    xtcModule(libs.xdk.cli)
    xtcModule(libs.xdk.web)  // transitively includes aggregate, collections, sec, convert, crypto, json, net
}

