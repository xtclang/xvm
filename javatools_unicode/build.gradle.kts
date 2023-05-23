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
    implementation("org.xtclang.xvm:javatools_utils:")
}

// TODO: Temporary copying the source and target version logic. Tool chains are on the way in,
//  with Java version configuration in one and only one place, but we need this for the GitHub
//  Gradle verification action to not assume Java 11 here, and attempting to include dependencies
//  targeting >= Java 17.
java {
    // Java 17 is the latest "Long Term Support" (LTS) release, as of late 2021
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    getMainClass().set("org.xvm.tool.BuildUnicodeTables")
}
