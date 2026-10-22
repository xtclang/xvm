plugins {
    alias(libs.plugins.xtc)
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    xtcModuleTest(libs.javatools.bridge)
    xtcModuleTest(libs.xdk.xunit.engine)
}
