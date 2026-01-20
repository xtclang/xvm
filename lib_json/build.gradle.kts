plugins {
    alias(libs.plugins.xtc)
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    // For test execution, we need the native bridge module (provides _native.xtclang.org)
    // and the xunit engine. Transitive dependencies are resolved automatically.
    xtcModuleTest(libs.javatools.bridge)
    xtcModuleTest(libs.xdk.xunit.engine)
}
