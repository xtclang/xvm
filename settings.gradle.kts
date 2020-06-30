rootProject.name = "xvm"

include(":utils")               // produces utils.jar for org.xvm.utils package
include(":unicode")             // produces data files -> :ecstasy/resources, only on request
include(":ecstasy")             // produces *only* a source zip file (no .xtc), and only on request
include(":javatools_bridge")    // produces *only* a source zip file (no .xtc), and only on request
include(":javatools")           // produces javatools.jar
include(":javatools_launcher")  // produces native (Win, Mac, Linux) executables, only on request
// TODO(":wiki")
// TODO(":json")
include(":xdk")         // builds the above modules (ecstasy.xtc, javatools_bridge.xtc, json.xtc)
                        // drags in Java libraries (utils, javatools), native launchers, wiki, etc.

include(":manualTests") // temporary; allowing gradle test execution