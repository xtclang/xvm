/*
 * Build files for the XDK.
 */

val ecstasy      = project(":ecstasy")
val javatools    = project(":javatools")
val bridge       = project(":javatools_bridge")
val json         = project(":lib_json");
val jsondb       = project(":lib_jsondb");
val oodb         = project(":lib_oodb");

val ecstasyMain  = "${ecstasy.projectDir}/src/main"
val bridgeMain   = "${bridge.projectDir}/src/main"
val javatoolsJar = "${javatools.buildDir}/libs/javatools.jar"
val jsonMain     = "${json.projectDir}/src/main";
val jsondbMain   = "${jsondb.projectDir}/src/main";
val oodbMain     = "${oodb.projectDir}/src/main";


val libDir       = "$buildDir/xdk/lib"
val coreLib      = "$libDir/ecstasy.xtc"
val bridgeLib    = "$buildDir/xdk/javatools/javatools_bridge.xtc"

tasks.register("clean") {
    group       = "Build"
    description = "Delete previous build results"
    delete("$buildDir")
}

val copyOutline = tasks.register<Copy>("copyOutline") {
    from("$projectDir/src/main/resources") {
        include("xdk/**")
    }
    into("$buildDir")
    doLast {
        println("Finished task: copyOutline")
    }
}

val copyJavatools = tasks.register<Copy>("copyJavatools") {
    from(javatoolsJar)
    into("$buildDir/xdk/javatools/")

    dependsOn(javatools.tasks["build"])
    dependsOn(copyOutline)
    doLast {
        println("Finished task: copyJavatools")
    }
}

val compileEcstasy = tasks.register<JavaExec>("compileEcstasy") {
    group       = "Execution"
    description = "Build ecstasy.xtc and _native.xtc modules"

    dependsOn(javatools.tasks["build"])
    dependsOn(copyJavatools)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "$ecstasyMain/x/module.x",
            "$bridgeMain/x/module.x")
    main = "org.xvm.tool.Compiler"

    doLast {
        file("$libDir/_native.xtc").
           renameTo(file("$bridgeLib"))
        println("Finished task: compileEcstasy")
    }
}

val compileJson = tasks.register<JavaExec>("compileJson") {
    group       = "Execution"
    description = "Build json.xtc module"

    shouldRunAfter(compileEcstasy)

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-L", "$coreLib",
            "-L", "$bridgeLib",
            "$jsonMain/x/module.x")
    main = "org.xvm.tool.Compiler"
}

val compileOODB = tasks.register<JavaExec>("compileOODB") {
    group       = "Execution"
    description = "Build oodb.xtc module"

    shouldRunAfter(compileEcstasy)

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-L", "$coreLib",
            "-L", "$bridgeLib",
            "$oodbMain/x/module.x")
    main = "org.xvm.tool.Compiler"
}

val compileJsonDB = tasks.register<JavaExec>("compileJsonDB") {
    group       = "Execution"
    description = "Build jsondb.xtc module"

    shouldRunAfter(compileJson)
    shouldRunAfter(compileOODB)

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-L", "$coreLib",
            "-L", "$bridgeLib",
            "-L", "$libDir",
            "$jsondbMain/x/module.x")
    main = "org.xvm.tool.Compiler"
}

tasks.register("build") {
    group       = "Build"
    description = "Build the XDK"

    // we assume that the launcher project has been built
    val launcher         = project(":javatools_launcher")
    val macos_launcher   = "${launcher.buildDir}/exe/macos_launcher"
    val windows_launcher = "${launcher.buildDir}/exe/windows_launcher.exe"

    // compile Ecstasy
    val coreSrc = fileTree(ecstasyMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val coreDest = file("$coreLib").lastModified()

    val bridgeSrc = fileTree(bridgeMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val bridgeDest = file("$bridgeLib").lastModified()

    if (coreSrc > coreDest || bridgeSrc > bridgeDest) {
        dependsOn(compileEcstasy)
    } else {
        dependsOn(copyJavatools)
    }

    // compile JSON
    val jsonSrc = fileTree(jsonMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val jsonDest = file("$libDir/json.xtc").lastModified()

    if (jsonSrc > jsonDest) {
        dependsOn(compileJson)
        }

    // compile OODB
    val oodbSrc = fileTree(oodbMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val oodbDest = file("$libDir/oodb.xtc").lastModified()

    if (oodbSrc > oodbDest) {
        dependsOn(compileOODB)
        }

    // compile JSON-DB
    val jsondbSrc = fileTree(jsondbMain).getFiles().stream().
    mapToLong({f -> f.lastModified()}).max().orElse(0)
    val jsondbDest = file("$libDir/jsondb.xtc").lastModified()

    if (jsondbSrc > jsondbDest) {
        dependsOn(compileJsonDB)
    }

    doLast {
        copy {
            from(macos_launcher, windows_launcher)
            into("$buildDir/xdk/bin/")
            }
        println("Finished task: build")
    }

// TODO wiki
// TODO ZIP the resulting xdk directory; e.g. on macOS:
//    `zip -r xdk.zip ./xdk -x *.DS_Store`
}
