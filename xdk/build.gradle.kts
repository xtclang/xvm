/*
 * Build files for the XDK.
 */

val ecstasy      = project(":ecstasy")
val javatools    = project(":javatools")
val bridge       = project(":javatools_bridge")
val ecstasySrc   = "${ecstasy.projectDir}/src/main/x"
val bridgeSrc    = "${bridge.projectDir}/src/main/x"
val javatoolsJar = "${javatools.buildDir}/libs/javatools.jar"

val copyOutline = tasks.register<Copy>("copyOutline") {
    from(file("$projectDir/src/main/resources"))
    include("**/xdk/*")
    into(file("$buildDir"))
}

val copyJavatools = tasks.register<Copy>("copyJavatools") {
    from(file(javatoolsJar))
    into(file("$buildDir/xdk/javatools/"))

    dependsOn(javatools.tasks["build"])
}

val compileEcstasy = tasks.register<JavaExec>("compileEcstasy") {
    group       = "Execution"
    description = "Build Ecstasy.xtc and _native.xtc modules"

    dependsOn(javatools.tasks["build"])

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$buildDir/xdk/lib",
            "${ecstasySrc}/module.x",
            "${bridgeSrc}/module.x")
    main = "org.xvm.tool.Compiler"
}

tasks.register("build") {
    group = "Build"
    description = "Build the jdk"
    dependsOn(copyOutline)
    dependsOn(copyJavatools)
    dependsOn(compileEcstasy) // TODO: how to skip this if not necessary?

    // TODO: move the files around
}
