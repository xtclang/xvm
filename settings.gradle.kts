rootProject.name = "xvm"

include(":utils")               // produces utils.jar for org.xvm.utils package
include(":unicode")             // produces data files -> :ecstasy/resources, only on request
include(":ecstasy")             // produces *only* a source zip file (no .xtc), and only on request
include(":javatools_bridge")    // produces *only* a source zip file (no .xtc), and only on request
include(":javatools")           // produces javatools.jar
include(":javatools_launcher")  // produces native (Win, Mac, Linux) executables, only on request
include(":lib_json")            // produces json.xtc
include(":lib_oodb")            // produces oodb.xtc
include(":lib_jsondb")          // produces jsondb.xtc
// TODO(":wiki")
include(":xdk")         // builds the above modules (ecstasy.xtc, javatools_bridge.xtc, json.xtc, etc.)
// drags in Java libraries (utils, javatools), native launchers, wiki, etc.

include(":manualTests") // temporary; allowing gradle test execution