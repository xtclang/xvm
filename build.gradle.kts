/*
 * Main build file for the XVM project, producing the XDK.
 */

group = "org.xvm"
version = "0.1.0"

allprojects {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.xtclang.xvm:utils")).with(project(":utils"))
            substitute(module("org.xtclang.xvm:unicode")).with(project(":unicode"))
            substitute(module("org.xtclang.xvm:javatools")).with(project(":javatools"))
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
