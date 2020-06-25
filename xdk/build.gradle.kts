/*
 * Build file for the XDK.
 */

val copyOutline = tasks.register<Copy>("copyOutline") {
    from(file("$projectDir/src/main/resources"))
    include("xdk")
    into(file("$buildDir"))
}

val copyJavatools = tasks.register<Copy>("copyJavatools") {
    from(file("${project(":javatools").buildDir}/lib/javatools.jar"))
    into(file("$buildDir/xdk/javatools/"))
}
