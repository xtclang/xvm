/*
 * Build the web XTC module.
 */

tasks.register("build") {
    group       = "Build"
    description = "Build this project"
    // the web project doesn't build anything itself
}

tasks.register<JavaExec>("compileWeb") {
    group       = "Build"
    description = "Build web module"

    val xdk = project(":xdk");
    dependsOn(xdk.tasks["build"])

    val javatools = project(":javatools")
    val javatoolsJar = "${javatools.buildDir}/libs/javatools.jar"

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$buildDir",
            "-L", "${xdk.buildDir}/xdk/lib/ecstasy.xtc",
            "-L", "${xdk.buildDir}/xdk/javatools/javatools_bridge.xtc",
            "src/main/x/module.x")
    main = "org.xvm.tool.Compiler"
}
