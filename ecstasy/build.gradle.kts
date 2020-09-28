/*
 * Build file for the Ecstasy core library of the XDK.
 *
 * This project does NOT build the ecstasy.xtc file. (The :xdk project builds it.)
 *
 * This project can update the Unicode data files, if a Unicode release has occurred and provided
 * a new `ucd.all.flat.zip`; that is the only time that the Unicode data files have to be updated.
 */

project.ext.set("implicit.x", "${projectDir}/src/main/resources/implicit.x")

tasks.register("clean") {
    group       = "Build"
    description = "Delete previous build results"
    // the Ecstasy module project does not build anything itself, so there is nothing to clean
}

tasks.register<Copy>("importUnicodeFiles") {
    group       = "Build"
    description = "Copy the various Unicode data files from :unicode to :ecstasy project."
    from(file("${project(":unicode").buildDir}/resources/"))
    include("Char*.txt", "Char*.dat")
    into(file("src/main/resources/text/"))
    doLast {
        println("Finished task: importUnicodeFiles")
    }
}

tasks.register("rebuildUnicodeFiles") {
    group       = "Build"
    description = "Force the rebuild of the `./src/main/resources/text` data files by running the :unicode project and copying the results."
    val make = project(":unicode").tasks["run"]
    val copy = tasks["importUnicodeFiles"]
    dependsOn(make)
    dependsOn(copy)
    copy.mustRunAfter(make)
    doLast {
        println("Finished task: rebuildUnicodeFiles")
    }
}

tasks.register("build") {
    group       = "Build"
    description = "Build this project"
    // the Ecstasy module project does not build anything itself
}
