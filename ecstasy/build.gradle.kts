/*
 * Build file for the Ecstasy core library of the XDK.
 *
 * This project does NOT build the Ecstasy.xtc file. (The :xdk project builds it.)
 *
 * This project can update the Unicode data files, if a Unicode release has occurred and provided
 * a new `ucd.all.flat.zip`; that is the only time that the Unicode data files have to be updated.
 */

tasks.register<Copy>("importUnicodeFiles") {
    description = "Copy the various Unicode data files from :unicode to :ecstasy project."
    from(file("${project(":unicode").buildDir}/resources/"))
    include("Char*.txt", "Char*.dat")
    into(file("src/main/resources/text/"))
}

tasks.register("rebuildUnicodeFiles") {
    description = "Force the rebuild of the `./src/main/resources/text` data files by running the :unicode project and copying the results."
    val make = project(":unicode").tasks["run"]
    val copy = tasks["importUnicodeFiles"]
    dependsOn(make)
    dependsOn(copy)
    copy.mustRunAfter(make)
}

