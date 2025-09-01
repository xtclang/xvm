/**
 * This is a featherweight precompiled script plugin that helps us set the semantic version
 * of all XDK components/subprojects. The plugin itself should not do any manipulation of
 * versions, since it can be applied from arbitrary third party code.
 *
 * Any XTC project will have an extension with its resolved SemanticVersion
 */

// Debug plugin removed

val semanticVersion by extra {
    xdkBuildLogic.versions().assignSemanticVersionFromCatalog()
}
