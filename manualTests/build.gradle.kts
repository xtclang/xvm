/*
 * Test utilities.
 */

val xdk          = project(":xdk");
val javatools    = project(":javatools")
val javatoolsJar = "${javatools.buildDir}/libs/javatools.jar"

val tests = listOf<String>(
    "src/main/x/annos.x",
    "src/main/x/array.x",
    "src/main/x/defasn.x",
    "src/main/x/exceptions.x",
    "src/main/x/generics.x",
    "src/main/x/innerOuter.x",
    "src/main/x/files.x",
    "src/main/x/IO.x",
    "src/main/x/lambda.x",
    "src/main/x/literals.x",
    "src/main/x/loop.x",
    "src/main/x/nesting.x",
    "src/main/x/numbers.x",
    "src/main/x/prop.x",
    "src/main/x/maps.x",
    "src/main/x/misc.x",
    "src/main/x/queues.x",
    "src/main/x/services.x",
    "src/main/x/reflect.x",
    "src/main/x/tuple.x")

tasks.register("clean") {
    group       = "Build"
    description = "Delete previous build results"
    delete("$buildDir")
}

tasks.register<JavaExec>("compileAll") {
    group       = "Build"
    description = "Run all tests"

    dependsOn(xdk.tasks["build"])

    classpath(javatoolsJar)

    val opts = listOf<String>(
        "-verbose",
        "-o", "$buildDir",
        "-L", "${xdk.buildDir}/xdk/lib/Ecstasy.xtc",
        "-L", "${xdk.buildDir}/xdk/javatools/javatools_bridge.xtc",
        "-L", "${xdk.buildDir}/xdk/lib/Json.xtc")

    args(opts + tests)
    main = "org.xvm.tool.Compiler"
}

tasks.register<JavaExec>("runAll") {
    group       = "Execution"
    description = "Run all tests"

    dependsOn(xdk.tasks["build"])

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    // the first two paths contain classes that are present in the javatoolsJar,
    // but gradle's classpath() doesn't allow combining a jar with a regular path
    classpath(
        "${javatools.buildDir}/classes/java/main",
        "${javatools.buildDir}/resources/main",
        "${javatools.buildDir}/classes/java/test")

    args(tests)
    main = "org.xvm.runtime.TestConnector"
}