/*
 * Build file for the Unicode tools portion of the XDK.
 *
 * Technically, this only needs to be built and run when new versions of the Unicode standard are
 * released, and when that occurs, the code in Char.x also has to be updated (to match the .dat file
 * data) using the values in the *.txt files that are outputted by running this.
 */

plugins {
    application
}

dependencies {
    implementation("com.sun.activation:javax.activation:1.2.0")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:2.3.2")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.2")
    implementation("org.xtclang.xvm:utils:")
}

application {
    getMainClass().set("org.xvm.tool.BuildUnicodeTables")
}
