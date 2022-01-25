/*
 * Main build file for the XVM project, producing the XDK.
 */

group = "org.xvm"
version = "0.3.0"

allprojects {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.xtclang.xvm:javatools_utils"  )).with(project(":javatools_utils"))
            substitute(module("org.xtclang.xvm:javatools_unicode")).with(project(":javatools_unicode"))
            substitute(module("org.xtclang.xvm:javatools"        )).with(project(":javatools"))
        }
    }

    repositories {
        mavenCentral {
            content {
                excludeGroup("org.xtclang.xvm")
            }
        }
    }
}

tasks.register("build") {
    group       = "Build"
    description = "Build all projects"

    dependsOn(project("xdk:").tasks["build"])
}
