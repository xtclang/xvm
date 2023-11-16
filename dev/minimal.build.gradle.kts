import java.io.File

plugins {
    id("org.xvm.build.version")
    alias(libs.plugins.xtc)
}

dependencies {
    xdk(libs.xdk)
}

val genesLocalXtcSourceFile = File(System.getProperty("user.home") + "/ggleyzer-xtc-code/GenesHelloWorld.x")

// Add a source directory with the file to be compiled to this project. Exclude everything else from this dir or
// any other default dir.
sourceSets.main {
    xtc {
        srcDir(genesLocalXtcSourceFile.parentFile) // Adds a source directory to the XTC source set.
        include(genesLocalXtcSourceFile.name) // Include Gene's XTC source file and absolutely nothing else.
    }
}

/**
 * XTC Compile configuration block. See @XtcCompilerExtension for what's in it.
 */
xtcCompile {
    forceRebuild = true // not recommended for production builds.
}

/**
 * XTC Runtime DSL. See @XtcRuntimeExtension class for what's in it.
 */
xtcRun {
    fork = false // execute the runner inside the build process so breakpoint works without connectors and stuff
    moduleName("GenesMainModuleName")  // can also supply anything else you can to the Runner
}
