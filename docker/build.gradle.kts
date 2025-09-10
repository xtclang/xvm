/**
 * Docker build and management for XVM project.
 * Docker build tasks are provided by the org.xtclang.build.docker convention plugin.
 */

plugins {
    base
    id("org.xtclang.build.xdk.versioning")
    id("org.xtclang.build.git")
    id("org.xtclang.build.docker")
}

private val semanticVersion: SemanticVersion by extra

// Docker tasks (buildAmd64, buildArm64, buildAll, pushAmd64, pushArm64, pushAll, cleanImages) 
// are automatically created by the org.xtclang.build.docker convention plugin