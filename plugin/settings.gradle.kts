pluginManagement {
    includeBuild("../build-logic/settings-plugins")
    includeBuild("../build-logic/common-plugins")
}

plugins {
    id("org.xtclang.build.common")
}

includeBuild("../javatools")
includeBuild("plugin-maven")

rootProject.name = "xtc-plugin"
