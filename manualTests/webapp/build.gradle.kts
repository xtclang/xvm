plugins {
    alias(libs.plugins.xtc)
    alias(libs.plugins.tasktree)
}

// TODO: xdkDistribution does not resolve on its own in this environment. It's not clear why.
dependencies {
    xdk(libs.xdk)
}

xtcRun {
    moduleName("welcomeTest")
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
