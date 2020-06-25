/*
 * Build files for the XDK.
 */

val javatools    = project(":javatools")
val javatoolsJar = "${javatools.buildDir}/libs/javatools.jar"

val copyOutline = tasks.register<Copy>("copyOutline") {
    from(file("$projectDir/src/main/resources"))
    include("xdk")
    into(file("$buildDir"))
}

val copyJavatools = tasks.register<Copy>("copyJavatools") {
    from(file(javatoolsJar))
    into(file("$buildDir/xdk/javatools/"))

    dependsOn(javatools.tasks["build"])
}

tasks.register<JavaExec>("compileEcstasy") {
    group = "Execution"
    description = "Build Ecstasy.xtc and _native.xtc modules"

    dependsOn(javatools.tasks["build"])

    println(javatoolsJar)
    classpath(javatoolsJar)
    main = "org.xvm.compiler.CommandLine"
}
