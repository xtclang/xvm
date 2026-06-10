plugins {
    alias(libs.plugins.xtc)
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    xtcModule(libs.xdk.json)
    xtcModuleTest(libs.javatools.bridge)
    xtcModuleTest(libs.xdk.xunit.engine)
}
