/**
 * Root settings file. Used for build aggregation, and configuring logic that must be ready
 * before we reach the root project build.
 */

pluginManagement {
    includeBuild("build-logic/aggregator")
    includeBuild("build-logic/settings-plugins")
    includeBuild("build-logic/common-plugins")
}

plugins {
    `gradle-enterprise`
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        isUploadInBackground = System.getenv("CI") != null
        publishAlwaysIf(System.getenv("GITHUB_ACTIONS") == "true")
        publishOnFailure()
        capture {
            isTaskInputFiles = true
        }
    }
}

includeBuild("javatools_utils")
includeBuild("javatools")
includeBuild("javatools_unicode")
includeBuild("plugin")
includeBuild("xdk")
includeBuild("manualTests")

rootProject.name = "xvm"

/*
include(":javatools_utils")     // produces javatools_utils.jar for org.xvm.utils package
include(":javatools_unicode")   // produces data files -> :lib_ecstasy/resources, only on request
include(":javatools")           // produces javatools.jar
include(":javatools_turtle")    // produces *only* a source zip file (no .xtc), and only on request
include(":javatools_bridge")    // produces *only* a source zip file (no .xtc), and only on request
include(":javatools_launcher")  // produces native executables (Win, Mac, Linux), only on request
include(":lib_ecstasy")         // produces *only* a source zip file (no .xtc), and only on request
include(":lib_aggregate")       // produces aggregate.xtc
include(":lib_collections")     // produces collections.xtc
include(":lib_crypto")          // produces crypto.xtc
include(":lib_net")             // produces net.xtc
include(":lib_json")            // produces json.xtc
include(":lib_oodb")            // produces oodb.xtc
include(":lib_jsondb")          // produces jsondb.xtc
include(":lib_web")             // produces web.xtc
include(":lib_webauth")         // produces webauth.xtc
include(":lib_xenia")           // produces xenia.xtc

// TODO(":wiki")
include(":xdk")      // builds the above modules (ecstasy.xtc, javatools_bridge.xtc, json.xtc, etc.)
// drags in Java libraries (javatools_utils, javatools), native launchers, wiki, etc.
include(":manualTests") // temporary; allowing gradle test execution
*/


// New stuff:
