/*
 * Main build file for the XVM project, producing the XDK.
 */

plugins {
    java
}

allprojects {
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.xtclang.xvm:utils")).with(project(":utils"))
            substitute(module("org.xtclang.xvm:unicode")).with(project(":unicode"))
            substitute(module("org.xtclang.xvm:ecstasy")).with(project(":ecstasy"))
            substitute(module("org.xtclang.xvm:javatools_bridge")).with(project(":javatools_bridge"))
            substitute(module("org.xtclang.xvm:javatools")).with(project(":javatools"))
            substitute(module("org.xtclang.xvm:javatools_launcher")).with(project(":javatools_launcher"))
            substitute(module("org.xtclang.xvm:xdk")).with(project(":xdk"))
        }
    }

    repositories {
        jcenter {
            content {
                excludeGroup("org.xtclang.xvm")
            }
        }
    }
}

dependencies {
    implementation("org.xtclang.xvm:utils:")
    implementation("org.xtclang.xvm:unicode:")
    implementation("org.xtclang.xvm:ecstasy:")
    implementation("org.xtclang.xvm:javatools_bridge:")
    implementation("org.xtclang.xvm:javatools:")
    implementation("org.xtclang.xvm:javatools_launcher:")
    implementation("org.xtclang.xvm:xdk:")
}
