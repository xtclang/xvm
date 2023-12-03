/**
 * This settings contains logic for using the XDK repo internal plugins,
 * as included builds. This means that you any code change done in e.g.
 * the XTC Launcher will only rebuild what's required to rerun the test.
 * An XTC (not XDK) developer would define the repository and version
 * for the XTC plugin and the XDK in this file instead (e.g. it's told
 * to use the components from an existing local distribution installed,
 * if it exists in mavenLocal(), or from an official xtc.org repository,
 * which currently only exists on GitLab. We will also publish to
 * gradlePluginPortal() and mavenCentral() in the near future, when we
 * have a stable release schedule/process, the required digital signature
 * mechanism enabled, and various "paperwork" signed.
 */

pluginManagement {
    includeBuild("../build-logic/settings-plugins")
    includeBuild("../build-logic/common-plugins")
}

plugins {
    id("org.xtclang.build.common")
}

rootProject.name = "dev"
