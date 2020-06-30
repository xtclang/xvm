/*
 * Test utilities.
 */

tasks.register<JavaExec>("runAll") {
    group       = "Execution"
    description = "Run all tests"

    dependsOn(project(":xdk").tasks["build"])

    val javatools = project(":javatools")
    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(
        "${javatools.buildDir}/classes/java/main",
        "${javatools.buildDir}/classes/java/test",
        "${javatools.buildDir}/resources/main"
        )

    args(
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
        "src/main/x/tuple.x"
        )
    main = "org.xvm.runtime.TestConnector"
}
