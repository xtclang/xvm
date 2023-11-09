This directory is intended to contain logic for building a pure Maven plugin from the XTC plugin. 
The plugin will be built using the [Maven Plugin Plugin](https://maven.apache.org/plugin-tools/maven-plugin-plugin/).
And with [MavenPluginDevelopmentExtension] https://www.benediktritter.de/maven-plugin-development/

There should be test code verifying that the publication works (and unit tests for the original Gradle
plugin for XTC are also, at the moment, a bit lacking.)

We also intend to publish non snapshot releases to MavenCentral, as soon as we are stable enough
and sorted out code signing and credentia
