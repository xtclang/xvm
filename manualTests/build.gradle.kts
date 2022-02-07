/*
 * Test utilities.
 */

val xdk          = project(":xdk");
val javatools    = project(":javatools")
val javatoolsJar = "${javatools.buildDir}/libs/javatools.jar"

val tests = listOf<String>(
    "src/main/x/annos.x",
    "src/main/x/array.x",
    "src/main/x/collections.x",
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

val compileAll = tasks.register<JavaExec>("compileAll") {
    group       = "Build"
    description = "Compile all tests"

    dependsOn(xdk.tasks["build"])

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)

    val opts = listOf<String>(
        "-verbose",
        "-o", "$buildDir",
        "-L", "${xdk.buildDir}/xdk/lib",
        "-L", "${xdk.buildDir}/xdk/javatools/javatools_bridge.xtc")

    args(opts + tests)
    mainClass.set("org.xvm.tool.Compiler")
}

tasks.register<JavaExec>("runAll") {
    group       = "Test"
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
    mainClass.set("org.xvm.runtime.TestConnector")
}

val compileOne = tasks.register<JavaExec>("compileOne") {
    description = "Compile a \"testName\" test"

    dependsOn(xdk.tasks["build"])

    val name = if (project.hasProperty("testName")) project.property("testName") else "TestSimple"

    classpath(javatoolsJar)

    args("-verbose",
         "-o", "$buildDir",
         "-L", "${xdk.buildDir}/xdk/lib",
         "-L", "${xdk.buildDir}/xdk/javatools/javatools_bridge.xtc",
         "-L", "$buildDir",
         "src/main/x/$name.x")
    mainClass.set("org.xvm.tool.Compiler")
}

tasks.register<JavaExec>("runOne") {
    group       = "Test"
    description = "Run a \"testName\" test"

    dependsOn(xdk.tasks["build"])

    val name = if (project.hasProperty("testName")) project.property("testName") else "TestSimple"

    jvmArgs("-showversion", "-Xms1024m", "-Xmx1024m", "-ea")

    systemProperties.put("xvm.db.impl", System.getProperty("xvm.db.impl"))

    classpath(
        "${javatools.buildDir}/classes/java/main",
        "${javatools.buildDir}/resources/main",
        "${javatools.buildDir}/classes/java/test")

    args("src/main/x/$name.x")
    mainClass.set("org.xvm.runtime.TestConnector")
}

tasks.register<JavaExec>("host") {
    group       = "Test"
    description = "Host a \"testName\" test"

    dependsOn(xdk.tasks["build"])

    val name = if (project.hasProperty("testName")) project.property("testName") else "TestSimple"

    systemProperties.put("xvm.db.impl", System.getProperty("xvm.db.impl"))

    classpath(javatoolsJar)

    val opts = listOf<String>(
        "-L", "${xdk.buildDir}/xdk/lib/",
        "-L", "${xdk.buildDir}/xdk/javatools/javatools_bridge.xtc",
        "-L", "$buildDir",
        "${xdk.buildDir}/xdk/lib/host.xtc")

    args(opts + "build/$name.xtc")
    mainClass.set("org.xvm.tool.Runner")

    doLast {
        val console = "$buildDir/${name}_home/console.log"
        if (file(console).exists()) {
            exec() {
                commandLine = listOf<String>("cat", "$buildDir/${name}_home/console.log")
            }
        }
    }
}
