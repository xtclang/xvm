pluginManagement {
    includeBuild("../../build-logic/settings-plugins")
    includeBuild("../../build-logic/common-plugins")
}

plugins {
    id("org.xvm.build.common")
}

rootProject.name = "xtc-plugin-maven"
