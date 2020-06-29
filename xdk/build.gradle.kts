/*
 * Build files for the XDK.
 */

val ecstasy      = project(":ecstasy")
val javatools    = project(":javatools")
val bridge       = project(":javatools_bridge")
val ecstasySrc   = "${ecstasy.projectDir}/src/main/x"
val bridgeSrc    = "${bridge.projectDir}/src/main/x"
val javatoolsJar = "${javatools.buildDir}/libs/javatools.jar"

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
    description = "Build Ecstasy.xtc and _native.xtc modules"

    dependsOn(javatools.tasks["build"])
    dependsOn(copyJavatools)

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$buildDir/xdk/lib",
            "$ecstasySrc/module.x",
            "$bridgeSrc/module.x")
    main = "org.xvm.tool.Compiler"

    doLast {
        file("$buildDir/xdk/lib/_native.xtc").
           renameTo(file("$buildDir/xdk/javatools/javatools_bridge.xtc"))
        println("Finished task: compileEcstasy")
    }
}

tasks.register("build") {
    group       = "Build"
    description = "Build the XDK"

    // we assume that the launcher project has been built
    val launcher         = project(":javatools_launcher")
    val macos_launcher   = "${launcher.buildDir}/exe/macos_launcher"
    val windows_launcher = "${launcher.buildDir}/exe/windows_launcher.exe"

    dependsOn(compileEcstasy) // TODO: how to skip this if not necessary?
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
