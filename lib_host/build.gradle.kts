/*
 * Build the "host" module.
 */

tasks.register("build") {
    group       = "Build"
    description = "Build this project"
}

tasks.register<JavaExec>("compileHost") {
    group       = "Build"
    description = "Build host module"

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
            "-L", "${xdk.buildDir}/xdk/lib/oodb.xtc",
            "src/main/x/module.x")
    main = "org.xvm.tool.Compiler"
}
