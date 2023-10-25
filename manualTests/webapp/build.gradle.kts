plugins {
    alias(libs.plugins.xtc)
    alias(libs.plugins.tasktree)
}

// TODO: xdkZip does not resolve on its own in this environment. It's not clear why.
dependencies {
    xdk(libs.xdk)
    // TODO: webappConsumer(project(":webapp"))
}

xtcCompile {
    verbose = true
}

// TODO: Right now processResources exists through the xtc plugin, is run, and copies src/<sourceSet>/resources to build resources for that source set
//   To do this properly with a node app, we just add a dependency to its output to copy those to our resources as well. A build may NEVER write
//   anything under src/, or all dependency frameworks will break.
//val processResources by tasks.existing(Copy::class) {
//    from(webappConsumer)
//}

val runXtc by tasks.existing {
    xtcRuntime {
        verbose = true
        moduleName("welcomeTest")
    }
    doFirst {
        logger.lifecycle("The default runXtc task needs no other extension than its modules. We can put a default configuration for all run tasks in the build.gradle.kts file.")
    }
}

val runTestNativeLauncher by tasks.registering {
    group = "application"
    description = "Run the standalone test with a native launcher, not involving the XTC plugin at all."
    dependsOn(tasks.build, tasks.processResources)
    doLast {
        exec {
            commandLine("xec", "-verbose", "-L", "${layout.buildDirectory.get()}/xdk/main/lib", "welcomeTest.xtc")
        }
    }
}
